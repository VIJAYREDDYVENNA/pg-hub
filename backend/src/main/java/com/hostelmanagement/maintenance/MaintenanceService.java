package com.hostelmanagement.maintenance;

import com.hostelmanagement.bed.BedRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.BedLocationQueries;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.events.DomainEvents;
import com.hostelmanagement.common.files.FileStorageService;
import com.hostelmanagement.common.files.FileStorageService.FileDto;
import com.hostelmanagement.expenses.Expense;
import com.hostelmanagement.expenses.ExpenseRepository;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.room.Room;
import com.hostelmanagement.room.RoomRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import com.hostelmanagement.staff.Staff;
import com.hostelmanagement.staff.StaffRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    public static final String STATUSES = "OPEN|ASSIGNED|IN_PROGRESS|ON_HOLD|RESOLVED|CLOSED";

    private final MaintenanceRepository repository;
    private final MaintenanceAssignmentRepository historyRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final StaffRepository staffRepository;
    private final HostelRepository hostelRepository;
    private final ExpenseRepository expenseRepository;
    private final BedLocationQueries locationQueries;
    private final FileStorageService fileStorage;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public record MaintenanceRequestDto(@NotNull Long hostelId, Long roomId, Long bedId,
                                        @NotBlank @Size(max = 150) String title,
                                        @Size(max = 2000) String description,
                                        @Size(max = 40) String category,
                                        @NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
                                        boolean blocksAvailability,
                                        @DecimalMin("0.00") BigDecimal estimatedCost,
                                        Long assignedStaffId) {
    }

    public record StatusChangeRequest(@NotNull @Pattern(regexp = STATUSES) String status,
                                      @Size(max = 1000) String note,
                                      Long assignedStaffId,
                                      @DecimalMin("0.00") BigDecimal actualCost,
                                      /* When closing/resolving with an actual cost, also record it as an expense. */
                                      boolean recordExpense) {
    }

    public record HistoryDto(Long id, String statusFrom, String statusTo, Long staffId, String staffName, String note,
                             Instant createdAt) {
    }

    public record MaintenanceDto(Long id, Long hostelId, String hostelName, Long roomId, String roomNumber,
                                 Long bedId, String bedLabel, String title, String description, String category,
                                 String priority, String status, boolean blocksAvailability,
                                 BigDecimal estimatedCost, BigDecimal actualCost, Long assignedStaffId,
                                 String assignedStaffName, Instant createdAt, Instant updatedAt, Instant resolvedAt,
                                 String resolutionNotes, List<HistoryDto> history, List<FileDto> images) {
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceDto> list(Long hostelId, String status, String priority, Long roomId, int page, int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = repository.search(hostelAccess.scope(hostelId),
                blank(status) ? null : status, blank(priority) ? null : priority, roomId, pageable);
        Map<Long, String> hostelNames = new HashMap<>();
        hostelRepository.findAllById(result.getContent().stream().map(MaintenanceRequest::getHostelId).distinct().toList())
                .forEach(h -> hostelNames.put(h.getId(), h.getName()));
        Map<Long, String> roomNumbers = new HashMap<>();
        roomRepository.findAllById(result.getContent().stream().map(MaintenanceRequest::getRoomId)
                .filter(Objects::nonNull).distinct().toList()).forEach(r -> roomNumbers.put(r.getId(), r.getRoomNumber()));
        Map<Long, String> staffNames = staffNames(result.getContent().stream()
                .map(MaintenanceRequest::getAssignedStaffId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> bedLabels = new HashMap<>();
        bedRepository.findAllById(result.getContent().stream().map(MaintenanceRequest::getBedId)
                .filter(Objects::nonNull).distinct().toList()).forEach(b -> bedLabels.put(b.getId(), b.getBedLabel()));
        return PageResponse.of(result, m -> toDto(m, hostelNames.get(m.getHostelId()), roomNumbers.get(m.getRoomId()),
                bedLabels.get(m.getBedId()), staffNames.get(m.getAssignedStaffId()), List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public MaintenanceDto get(Long id) {
        MaintenanceRequest m = getEntity(id);
        List<MaintenanceAssignment> history = historyRepository.findByRequestIdOrderByCreatedAtAsc(id);
        Map<Long, String> staff = staffNames(history.stream().map(MaintenanceAssignment::getStaffId)
                .filter(Objects::nonNull).distinct().toList());
        if (m.getAssignedStaffId() != null) {
            staff.putAll(staffNames(List.of(m.getAssignedStaffId())));
        }
        String hostelName = hostelRepository.findById(m.getHostelId()).map(h -> h.getName()).orElse(null);
        String roomNumber = m.getRoomId() == null ? null
                : roomRepository.findById(m.getRoomId()).map(Room::getRoomNumber).orElse(null);
        String bedLabel = m.getBedId() == null ? null
                : bedRepository.findById(m.getBedId()).map(b -> b.getBedLabel()).orElse(null);
        return toDto(m, hostelName, roomNumber, bedLabel, staff.get(m.getAssignedStaffId()),
                history.stream().map(h -> new HistoryDto(h.getId(), h.getStatusFrom(), h.getStatusTo(), h.getStaffId(),
                        staff.get(h.getStaffId()), h.getNote(), h.getCreatedAt())).toList(),
                fileStorage.list("MAINTENANCE", id));
    }

    MaintenanceRequest getEntity(Long id) {
        MaintenanceRequest m = repository.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance request"));
        hostelAccess.checkEntity(m.getHostelId(), "Maintenance request");
        return m;
    }

    @Transactional
    public MaintenanceDto create(MaintenanceRequestDto req) {
        AuthUser user = CurrentUser.get();
        hostelAccess.checkHostel(req.hostelId());
        validateLocation(req.hostelId(), req.roomId(), req.bedId());
        MaintenanceRequest m = new MaintenanceRequest();
        m.setHostelId(req.hostelId());
        m.setReportedBy(user.userId());
        apply(m, req);
        if (m.getAssignedStaffId() != null) {
            m.setStatus("ASSIGNED");
        }
        repository.save(m);
        history(m.getId(), null, m.getStatus(), m.getAssignedStaffId(), "Reported", user.userId());
        auditService.log("MAINTENANCE_CREATED", "MAINTENANCE", m.getId(), m.getTitle());
        events.publishEvent(new DomainEvents.MaintenanceChanged(user.ownerId(), m.getHostelId(), m.getId(),
                m.getTitle(), m.getStatus(), true, user.userId()));
        return get(m.getId());
    }

    @Transactional
    public MaintenanceDto update(Long id, MaintenanceRequestDto req) {
        MaintenanceRequest m = getEntity(id);
        if (!m.getHostelId().equals(req.hostelId())) {
            throw ApiException.badRequest("A maintenance request cannot be moved to another hostel");
        }
        if ("CLOSED".equals(m.getStatus())) {
            throw ApiException.conflict("Closed requests cannot be edited");
        }
        validateLocation(req.hostelId(), req.roomId(), req.bedId());
        Long previousStaff = m.getAssignedStaffId();
        apply(m, req);
        if (!Objects.equals(previousStaff, m.getAssignedStaffId())) {
            history(id, m.getStatus(), m.getStatus(), m.getAssignedStaffId(), "Reassigned", CurrentUser.get().userId());
        }
        auditService.log("MAINTENANCE_UPDATED", "MAINTENANCE", id, m.getTitle());
        return get(id);
    }

    @Transactional
    public MaintenanceDto changeStatus(Long id, StatusChangeRequest req) {
        AuthUser user = CurrentUser.get();
        MaintenanceRequest m = getEntity(id);
        String from = m.getStatus();
        if ("CLOSED".equals(from)) {
            throw ApiException.conflict("This request is closed");
        }
        if (req.assignedStaffId() != null) {
            m.setAssignedStaffId(validStaff(req.assignedStaffId()));
        }
        if ("ASSIGNED".equals(req.status()) && m.getAssignedStaffId() == null) {
            throw ApiException.badRequest("Choose a staff member to assign");
        }
        if (req.actualCost() != null) {
            m.setActualCost(Money.of(req.actualCost()));
        }
        m.setStatus(req.status());
        boolean done = "RESOLVED".equals(req.status()) || "CLOSED".equals(req.status());
        if (done) {
            if (m.getResolvedAt() == null) {
                m.setResolvedAt(Instant.now());
            }
            if (req.note() != null && !req.note().isBlank()) {
                m.setResolutionNotes(req.note());
            }
            if (req.recordExpense() && Money.isPositive(m.getActualCost())) {
                Expense e = new Expense();
                e.setHostelId(m.getHostelId());
                e.setCategory("MAINTENANCE");
                e.setDescription("Maintenance: " + m.getTitle());
                e.setAmount(Money.of(m.getActualCost()));
                e.setExpenseDate(clock.today());
                e.setPaymentMethod("CASH");
                e.setMaintenanceRequestId(m.getId());
                e.setRecordedBy(user.userId());
                expenseRepository.save(e);
            }
        } else {
            m.setResolvedAt(null);
        }
        history(id, from, req.status(), m.getAssignedStaffId(), req.note(), user.userId());
        auditService.log("MAINTENANCE_STATUS", "MAINTENANCE", id, from + " -> " + req.status());
        events.publishEvent(new DomainEvents.MaintenanceChanged(user.ownerId(), m.getHostelId(), m.getId(),
                m.getTitle(), m.getStatus(), false, user.userId()));
        return get(id);
    }

    @Transactional
    public FileDto uploadImage(Long id, MultipartFile file) {
        MaintenanceRequest m = getEntity(id);
        return FileDto.from(fileStorage.store(file, "MAINTENANCE", id, m.getHostelId(), "IMAGE", true));
    }

    private void validateLocation(Long hostelId, Long roomId, Long bedId) {
        if (bedId != null && roomId == null) {
            throw ApiException.badRequest("Select the room of the bed");
        }
        if (roomId != null) {
            Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                    .filter(r -> r.getHostelId().equals(hostelId))
                    .orElseThrow(() -> ApiException.badRequest("Room does not belong to the selected hostel"));
            if (bedId != null) {
                bedRepository.findByIdAndDeletedAtIsNull(bedId).filter(b -> b.getRoomId().equals(room.getId()))
                        .orElseThrow(() -> ApiException.badRequest("Bed does not belong to the selected room"));
            }
        }
    }

    private Long validStaff(Long staffId) {
        Staff s = staffRepository.findById(staffId)
                .filter(x -> x.getOwnerId().equals(CurrentUser.get().ownerId()))
                .filter(x -> x.getHostelId() == null || hostelAccess.canAccess(x.getHostelId()))
                .orElseThrow(() -> ApiException.badRequest("Selected staff member does not exist"));
        return s.getId();
    }

    private void apply(MaintenanceRequest m, MaintenanceRequestDto req) {
        m.setRoomId(req.roomId());
        m.setBedId(req.bedId());
        m.setTitle(req.title().trim());
        m.setDescription(req.description());
        m.setCategory(req.category());
        m.setPriority(req.priority());
        m.setBlocksAvailability(req.blocksAvailability());
        m.setEstimatedCost(req.estimatedCost() == null ? null : Money.of(req.estimatedCost()));
        m.setAssignedStaffId(req.assignedStaffId() == null ? null : validStaff(req.assignedStaffId()));
    }

    private void history(Long requestId, String from, String to, Long staffId, String note, Long userId) {
        MaintenanceAssignment h = new MaintenanceAssignment();
        h.setRequestId(requestId);
        h.setStatusFrom(from);
        h.setStatusTo(to);
        h.setStaffId(staffId);
        h.setNote(note);
        h.setChangedBy(userId);
        historyRepository.save(h);
    }

    private Map<Long, String> staffNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (!ids.isEmpty()) {
            staffRepository.findAllById(ids).forEach(s -> map.put(s.getId(), s.getFullName()));
        }
        return map;
    }

    private static MaintenanceDto toDto(MaintenanceRequest m, String hostelName, String roomNumber, String bedLabel,
                                        String staffName, List<HistoryDto> history, List<FileDto> images) {
        return new MaintenanceDto(m.getId(), m.getHostelId(), hostelName, m.getRoomId(), roomNumber, m.getBedId(),
                bedLabel, m.getTitle(), m.getDescription(), m.getCategory(), m.getPriority(), m.getStatus(),
                m.isBlocksAvailability(), m.getEstimatedCost(), m.getActualCost(), m.getAssignedStaffId(), staffName,
                m.getCreatedAt(), m.getUpdatedAt(), m.getResolvedAt(), m.getResolutionNotes(), history, images);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || "ALL".equals(s);
    }
}
