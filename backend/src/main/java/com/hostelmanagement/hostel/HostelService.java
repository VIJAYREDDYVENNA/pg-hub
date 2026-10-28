package com.hostelmanagement.hostel;

import com.hostelmanagement.auth.User;
import com.hostelmanagement.auth.UserHostelAccess;
import com.hostelmanagement.auth.UserHostelAccessRepository;
import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.building.BuildingRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.files.FileAttachment;
import com.hostelmanagement.common.files.FileStorageService;
import com.hostelmanagement.hostel.dto.HostelDtos.HostelRequest;
import com.hostelmanagement.hostel.dto.HostelDtos.HostelResponse;
import com.hostelmanagement.hostel.dto.HostelDtos.OccupancyStats;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HostelService {

    private final HostelRepository hostelRepository;
    private final BuildingRepository buildingRepository;
    private final UserRepository userRepository;
    private final UserHostelAccessRepository accessRepository;
    private final HostelAccessService hostelAccess;
    private final com.hostelmanagement.subscription.SubscriptionLimitService limits;
    private final OccupancyQueries occupancyQueries;
    private final FileStorageService fileStorage;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<HostelResponse> list() {
        var ids = hostelAccess.accessibleHostelIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Hostel> hostels = hostelRepository.findByIdInAndDeletedAtIsNullOrderByNameAsc(ids);
        Map<Long, OccupancyStats> stats = occupancyQueries.statsByHostel(List.copyOf(ids));
        Map<Long, Long> buildingCounts = occupancyQueries.buildingCounts(List.copyOf(ids));
        Map<Long, String> managerNames = managerNames(hostels);
        return hostels.stream().map(h -> toResponse(h, stats.getOrDefault(h.getId(), OccupancyStats.empty()),
                buildingCounts.getOrDefault(h.getId(), 0L), managerNames.get(h.getManagerUserId()))).toList();
    }

    @Transactional(readOnly = true)
    public HostelResponse get(Long id) {
        Hostel h = getEntity(id);
        var stats = occupancyQueries.statsByHostel(List.of(id)).getOrDefault(id, OccupancyStats.empty());
        long buildings = occupancyQueries.buildingCounts(List.of(id)).getOrDefault(id, 0L);
        return toResponse(h, stats, buildings, managerNames(List.of(h)).get(h.getManagerUserId()));
    }

    public Hostel getEntity(Long id) {
        hostelAccess.checkHostel(id);
        return hostelRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Hostel"));
    }

    @Transactional
    public HostelResponse create(HostelRequest req) {
        AuthUser user = CurrentUser.get();
        limits.assertCanAddHostel(user.ownerId());
        Hostel h = new Hostel();
        h.setOwnerId(user.ownerId());
        apply(h, req, user);
        hostelRepository.save(h);
        if (!user.isOwner()) {
            accessRepository.save(new UserHostelAccess(user.userId(), h.getId()));
        }
        if (h.getManagerUserId() != null) {
            grantAccess(h.getManagerUserId(), h.getId());
        }
        hostelAccess.evict();
        auditService.log("HOSTEL_CREATED", "HOSTEL", h.getId(), h.getName());
        return get(h.getId());
    }

    @Transactional
    public HostelResponse update(Long id, HostelRequest req) {
        Hostel h = getEntity(id);
        apply(h, req, CurrentUser.get());
        if (h.getManagerUserId() != null) {
            grantAccess(h.getManagerUserId(), h.getId());
        }
        auditService.log("HOSTEL_UPDATED", "HOSTEL", h.getId(), h.getName());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Hostel h = getEntity(id);
        if (occupancyQueries.activeAdmissionsInHostel(id) > 0) {
            throw ApiException.conflict("This hostel still has active residents. Check them out before removing it.");
        }
        h.setDeletedAt(Instant.now());
        h.setStatus("INACTIVE");
        auditService.log("HOSTEL_DELETED", "HOSTEL", h.getId(), h.getName());
    }

    @Transactional
    public HostelResponse uploadImage(Long id, MultipartFile file) {
        Hostel h = getEntity(id);
        FileAttachment f = fileStorage.store(file, "HOSTEL", id, id, "IMAGE", true);
        h.setImageFileId(f.getId());
        return get(id);
    }

    private void apply(Hostel h, HostelRequest req, AuthUser user) {
        h.setName(req.name().trim());
        h.setHostelType(req.hostelType());
        h.setAddress(req.address().trim());
        h.setCity(req.city().trim());
        h.setState(req.state().trim());
        h.setPinCode(req.pinCode().trim());
        h.setContactNumber(blankToNull(req.contactNumber()));
        h.setEmail(blankToNull(req.email()));
        h.setStatus(req.status() == null ? "ACTIVE" : req.status());
        // Naming a hostel manager grants that user access to the hostel, so it needs the user-management permission
        if (!java.util.Objects.equals(req.managerUserId(), h.getManagerUserId()) && !user.has("STAFF_MANAGE")) {
            throw ApiException.forbidden("You do not have permission to change the hostel manager");
        }
        if (req.managerUserId() != null) {
            User manager = userRepository.findByIdAndOwnerId(req.managerUserId(), user.ownerId())
                    .orElseThrow(() -> ApiException.badRequest("Selected manager does not exist"));
            h.setManagerUserId(manager.getId());
        } else {
            h.setManagerUserId(null);
        }
    }

    private void grantAccess(Long userId, Long hostelId) {
        var key = new UserHostelAccess.Key(userId, hostelId);
        if (!accessRepository.existsById(key)) {
            accessRepository.save(new UserHostelAccess(userId, hostelId));
        }
    }

    private Map<Long, String> managerNames(List<Hostel> hostels) {
        var ids = hostels.stream().map(Hostel::getManagerUserId).filter(java.util.Objects::nonNull).distinct().toList();
        // HashMap (not Map.of) so lookups with a null manager id return null instead of throwing
        Map<Long, String> map = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u.getFullName()));
        return map;
    }

    private HostelResponse toResponse(Hostel h, OccupancyStats stats, long buildingCount, String managerName) {
        return new HostelResponse(h.getId(), h.getName(), h.getHostelType(), h.getAddress(), h.getCity(),
                h.getState(), h.getPinCode(), h.getContactNumber(), h.getEmail(), h.getManagerUserId(), managerName,
                h.getImageFileId() == null ? null : "/api/files/" + h.getImageFileId(), h.getStatus(),
                buildingCount, stats);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
