package com.hostelmanagement.room;

import com.hostelmanagement.bed.Bed;
import com.hostelmanagement.bed.BedRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.floor.FloorService;
import com.hostelmanagement.occupancy.OccupancyQueries;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomService {

    public static final int MAX_BEDS_PER_ROOM = 30;

    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final FloorService floorService;
    private final OccupancyQueries occupancyQueries;
    private final com.hostelmanagement.security.HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final com.hostelmanagement.subscription.SubscriptionLimitService limits;

    public record RoomRequest(@NotNull Long floorId,
                              @NotBlank @Size(max = 20) String roomNumber,
                              @NotBlank @Size(max = 30) String roomType,
                              @NotNull @Min(1) @Max(MAX_BEDS_PER_ROOM) Integer totalBeds,
                              @NotNull @DecimalMin("0.00") BigDecimal monthlyRentPerBed,
                              @NotNull @DecimalMin("0.00") BigDecimal securityDeposit,
                              @Size(max = 500) String amenities,
                              @Pattern(regexp = "AVAILABLE|MAINTENANCE|INACTIVE") String status,
                              @Pattern(regexp = "TOP|BOTTOM|AUTO") String planRow) {
    }

    public Room getEntity(Long id) {
        Room r = roomRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Room"));
        hostelAccess.checkEntity(r.getHostelId(), "Room");
        return r;
    }

    @Transactional
    public Long create(RoomRequest req) {
        var ref = floorService.getRef(req.floorId());
        limits.assertCanAddBeds(com.hostelmanagement.security.CurrentUser.get().ownerId(), ref.building().getHostelId(), req.totalBeds());
        String number = req.roomNumber().trim();
        if (roomRepository.existsByFloorIdAndRoomNumberIgnoreCaseAndDeletedAtIsNull(req.floorId(), number)) {
            throw ApiException.conflict("Room " + number + " already exists on this floor");
        }
        Room room = new Room();
        room.setHostelId(ref.building().getHostelId());
        room.setFloorId(req.floorId());
        apply(room, req);
        room.setSortOrder(roomRepository.findByFloorIdAndDeletedAtIsNull(req.floorId()).stream()
                .mapToInt(Room::getSortOrder).max().orElse(-1) + 1);
        roomRepository.save(room);
        addBeds(room.getId(), new ArrayList<>(), req.totalBeds());
        auditService.log("ROOM_CREATED", "ROOM", room.getId(), "Room " + room.getRoomNumber() + ", beds " + req.totalBeds());
        return room.getId();
    }

    @Transactional
    public Long update(Long id, RoomRequest req) {
        Room room = getEntity(id);
        if (!room.getFloorId().equals(req.floorId())) {
            var ref = floorService.getRef(req.floorId());
            if (!ref.building().getHostelId().equals(room.getHostelId())) {
                throw ApiException.badRequest("A room can only be moved to a floor of the same hostel");
            }
        }
        String number = req.roomNumber().trim();
        if ((!room.getRoomNumber().equalsIgnoreCase(number) || !room.getFloorId().equals(req.floorId()))
                && roomRepository.existsByFloorIdAndRoomNumberIgnoreCaseAndDeletedAtIsNull(req.floorId(), number)) {
            throw ApiException.conflict("Room " + number + " already exists on this floor");
        }
        room.setFloorId(req.floorId());
        if ("INACTIVE".equals(req.status()) && occupancyQueries.activeAllocationsInRoom(id) > 0) {
            throw ApiException.conflict("A room with residents cannot be deactivated");
        }
        apply(room, req);
        resizeBeds(room, req.totalBeds());
        auditService.log("ROOM_UPDATED", "ROOM", id, "Room " + room.getRoomNumber());
        return id;
    }

    @Transactional
    public void delete(Long id) {
        Room room = getEntity(id);
        if (occupancyQueries.activeAllocationsInRoom(id) > 0) {
            throw ApiException.conflict("This room has residents. Move or check them out first.");
        }
        if (occupancyQueries.liveReservationsInRoom(id) > 0) {
            throw ApiException.conflict("This room has live bed reservations. Cancel them first.");
        }
        Instant now = Instant.now();
        room.setDeletedAt(now);
        bedRepository.findByRoomIdAndDeletedAtIsNullOrderByBedLabelAsc(id).forEach(b -> b.setDeletedAt(now));
        auditService.log("ROOM_DELETED", "ROOM", id, "Room " + room.getRoomNumber());
    }

    private void resizeBeds(Room room, int target) {
        List<Bed> beds = new ArrayList<>(bedRepository.findByRoomIdAndDeletedAtIsNullOrderByBedLabelAsc(room.getId()));
        if (target > beds.size()) {
            limits.assertCanAddBeds(com.hostelmanagement.security.CurrentUser.get().ownerId(), room.getHostelId(), target - beds.size());
            addBeds(room.getId(), beds, target - beds.size());
        } else if (target < beds.size()) {
            int toRemove = beds.size() - target;
            beds.sort(Comparator.comparing(Bed::getBedLabel, Comparator.comparingInt(String::length)
                    .thenComparing(Comparator.naturalOrder())).reversed());
            Instant now = Instant.now();
            for (Bed b : beds) {
                if (toRemove == 0) {
                    break;
                }
                if (!occupancyQueries.bedIsOccupied(b.getId()) && !occupancyQueries.bedIsReserved(b.getId())) {
                    b.setDeletedAt(now);
                    toRemove--;
                }
            }
            if (toRemove > 0) {
                throw ApiException.conflict("Cannot reduce beds: only free (unoccupied, unreserved) beds can be removed");
            }
        }
    }

    void addBeds(Long roomId, List<Bed> existing, int count) {
        Set<String> used = new HashSet<>();
        existing.forEach(b -> used.add(b.getBedLabel().toUpperCase(Locale.ROOT)));
        for (int i = 0; i < count; i++) {
            String label = nextLabel(used);
            used.add(label);
            Bed bed = new Bed();
            bed.setRoomId(roomId);
            bed.setBedLabel(label);
            bedRepository.save(bed);
        }
    }

    /** Beds are labelled A, B, C ... and then 27, 28 ... for very large dormitories. */
    static String nextLabel(Set<String> used) {
        for (char c = 'A'; c <= 'Z'; c++) {
            String l = String.valueOf(c);
            if (!used.contains(l)) {
                return l;
            }
        }
        for (int n = 27; ; n++) {
            String l = String.valueOf(n);
            if (!used.contains(l)) {
                return l;
            }
        }
    }

    private void apply(Room room, RoomRequest req) {
        room.setRoomNumber(req.roomNumber().trim());
        room.setRoomType(req.roomType().trim());
        room.setMonthlyRentPerBed(Money.of(req.monthlyRentPerBed()));
        room.setSecurityDeposit(Money.of(req.securityDeposit()));
        room.setAmenities(req.amenities());
        room.setStatus(req.status() == null ? "AVAILABLE" : req.status());
        if (req.planRow() != null) {
            room.setPlanRow(req.planRow());
        }
    }
}
