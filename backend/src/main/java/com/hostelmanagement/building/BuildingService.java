package com.hostelmanagement.building;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.floor.Floor;
import com.hostelmanagement.floor.FloorRepository;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository repository;
    private final FloorRepository floorRepository;
    private final HostelAccessService hostelAccess;
    private final OccupancyQueries occupancyQueries;
    private final AuditService auditService;

    public record BuildingRequest(@NotNull Long hostelId,
                                  @NotBlank @Size(max = 100) String name,
                                  @Size(max = 500) String description,
                                  @Pattern(regexp = "ACTIVE|INACTIVE") String status,
                                  Integer sortOrder,
                                  /* Optional: create this many floors (Ground, 1st, 2nd ...) on creation. */
                                  @Min(0) @Max(100) Integer numberOfFloors) {
    }

    public record BuildingResponse(Long id, Long hostelId, String name, String description, String status,
                                   int sortOrder, long floorCount) {
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> list(Long hostelId) {
        hostelAccess.checkHostel(hostelId);
        return repository.findByHostelIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(hostelId).stream()
                .map(this::toResponse).toList();
    }

    public Building getEntity(Long id) {
        Building b = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Building"));
        hostelAccess.checkEntity(b.getHostelId(), "Building");
        return b;
    }

    @Transactional(readOnly = true)
    public BuildingResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public BuildingResponse create(BuildingRequest req) {
        hostelAccess.checkHostel(req.hostelId());
        if (repository.existsByHostelIdAndNameIgnoreCaseAndDeletedAtIsNull(req.hostelId(), req.name().trim())) {
            throw ApiException.conflict("A building with this name already exists in the hostel");
        }
        Building b = new Building();
        b.setHostelId(req.hostelId());
        apply(b, req);
        repository.save(b);
        int floors = req.numberOfFloors() == null ? 0 : req.numberOfFloors();
        for (int level = 0; level < floors; level++) {
            Floor f = new Floor();
            f.setBuildingId(b.getId());
            f.setLevelNumber(level);
            f.setSortOrder(level);
            f.setName(Floor.defaultName(level));
            floorRepository.save(f);
        }
        auditService.log("BUILDING_CREATED", "BUILDING", b.getId(), b.getName());
        return toResponse(b);
    }

    @Transactional
    public BuildingResponse update(Long id, BuildingRequest req) {
        Building b = getEntity(id);
        if (!b.getHostelId().equals(req.hostelId())) {
            throw ApiException.badRequest("A building cannot be moved to another hostel");
        }
        apply(b, req);
        auditService.log("BUILDING_UPDATED", "BUILDING", b.getId(), b.getName());
        return toResponse(b);
    }

    @Transactional
    public void delete(Long id) {
        Building b = getEntity(id);
        if (occupancyQueries.activeAllocationsInBuilding(id) > 0) {
            throw ApiException.conflict("This building has residents allocated to beds. Move or check them out first.");
        }
        b.setDeletedAt(Instant.now());
        auditService.log("BUILDING_DELETED", "BUILDING", b.getId(), b.getName());
    }

    private void apply(Building b, BuildingRequest req) {
        b.setName(req.name().trim());
        b.setDescription(req.description());
        b.setStatus(req.status() == null ? "ACTIVE" : req.status());
        if (req.sortOrder() != null) {
            b.setSortOrder(req.sortOrder());
        }
    }

    private BuildingResponse toResponse(Building b) {
        return new BuildingResponse(b.getId(), b.getHostelId(), b.getName(), b.getDescription(), b.getStatus(),
                b.getSortOrder(), floorRepository.countByBuildingIdAndDeletedAtIsNull(b.getId()));
    }
}
