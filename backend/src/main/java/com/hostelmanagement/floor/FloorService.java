package com.hostelmanagement.floor;

import com.hostelmanagement.building.Building;
import com.hostelmanagement.building.BuildingService;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.occupancy.OccupancyQueries;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FloorService {

    private final FloorRepository repository;
    private final com.hostelmanagement.room.RoomRepository roomRepository;
    private final BuildingService buildingService;
    private final OccupancyQueries occupancyQueries;
    private final AuditService auditService;

    public record FloorRequest(@NotNull Long buildingId,
                               @NotBlank @Size(max = 60) String name,
                               @NotNull Integer levelNumber,
                               @Size(max = 500) String description,
                               Integer sortOrder) {
    }

    public record FloorResponse(Long id, Long buildingId, Long hostelId, String name, int levelNumber,
                                String description, int sortOrder, String planLayout, boolean hasLift,
                                boolean hasStairs) {
    }

    public record RoomPlacement(@NotNull Long roomId,
                                @NotNull @jakarta.validation.constraints.Pattern(regexp = "TOP|BOTTOM|AUTO") String planRow,
                                @NotNull Integer sortOrder) {
    }

    public record LayoutRequest(@NotNull @jakarta.validation.constraints.Pattern(regexp = "CORRIDOR|SINGLE_ROW|GRID") String planLayout,
                                boolean hasLift, boolean hasStairs,
                                @NotNull List<@jakarta.validation.Valid RoomPlacement> rooms) {
    }

    public record FloorOrderRequest(@NotNull List<Long> floorIds) {
    }

    public record FloorRef(Floor floor, Building building) {
    }

    @Transactional(readOnly = true)
    public List<FloorResponse> list(Long buildingId) {
        Building b = buildingService.getEntity(buildingId);
        return repository.findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscLevelNumberAsc(buildingId).stream()
                .map(f -> toResponse(f, b)).toList();
    }

    /** Loads a floor and verifies that the caller can access its hostel. */
    public FloorRef getRef(Long id) {
        Floor f = repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Floor"));
        Building b;
        try {
            b = buildingService.getEntity(f.getBuildingId());
        } catch (ApiException e) {
            throw ApiException.notFound("Floor");
        }
        return new FloorRef(f, b);
    }

    @Transactional
    public FloorResponse create(FloorRequest req) {
        Building b = buildingService.getEntity(req.buildingId());
        if (repository.existsByBuildingIdAndNameIgnoreCaseAndDeletedAtIsNull(b.getId(), req.name().trim())) {
            throw ApiException.conflict("A floor with this name already exists in the building");
        }
        Floor f = new Floor();
        f.setBuildingId(b.getId());
        apply(f, req);
        repository.save(f);
        auditService.log("FLOOR_CREATED", "FLOOR", f.getId(), b.getName() + " / " + f.getName());
        return toResponse(f, b);
    }

    @Transactional
    public FloorResponse update(Long id, FloorRequest req) {
        FloorRef ref = getRef(id);
        if (!ref.floor().getBuildingId().equals(req.buildingId())) {
            throw ApiException.badRequest("A floor cannot be moved to another building");
        }
        apply(ref.floor(), req);
        auditService.log("FLOOR_UPDATED", "FLOOR", id, ref.floor().getName());
        return toResponse(ref.floor(), ref.building());
    }

    @Transactional
    public void delete(Long id) {
        FloorRef ref = getRef(id);
        if (occupancyQueries.activeAllocationsOnFloor(id) > 0) {
            throw ApiException.conflict("This floor has residents allocated to beds. Move or check them out first.");
        }
        ref.floor().setDeletedAt(Instant.now());
        auditService.log("FLOOR_DELETED", "FLOOR", id, ref.floor().getName());
    }

    /** Saves the floor-plan style and the position/order of every room on the floor in one transaction. */
    @Transactional
    public FloorResponse saveLayout(Long id, LayoutRequest req) {
        FloorRef ref = getRef(id);
        Floor f = ref.floor();
        f.setPlanLayout(req.planLayout());
        f.setHasLift(req.hasLift());
        f.setHasStairs(req.hasStairs());
        Map<Long, com.hostelmanagement.room.Room> rooms = roomRepository.findByFloorIdAndDeletedAtIsNull(id).stream()
                .collect(Collectors.toMap(com.hostelmanagement.room.Room::getId, Function.identity()));
        var seen = new HashSet<Long>();
        for (RoomPlacement p : req.rooms()) {
            var room = rooms.get(p.roomId());
            if (room == null || !seen.add(p.roomId())) {
                throw ApiException.badRequest("Room " + p.roomId() + " is not on this floor");
            }
            room.setPlanRow(p.planRow());
            room.setSortOrder(p.sortOrder());
        }
        auditService.log("FLOOR_LAYOUT_UPDATED", "FLOOR", id, req.planLayout() + ", " + req.rooms().size() + " room(s)");
        return toResponse(f, ref.building());
    }

    /** Re-orders the floors of a building. floorIds are given top floor first; the top floor gets the highest order. */
    @Transactional
    public List<FloorResponse> reorder(Long buildingId, FloorOrderRequest req) {
        Building b = buildingService.getEntity(buildingId);
        Map<Long, Floor> floors = repository.findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscLevelNumberAsc(buildingId)
                .stream().collect(Collectors.toMap(Floor::getId, Function.identity()));
        if (req.floorIds().size() != floors.size() || !floors.keySet().containsAll(req.floorIds())) {
            throw ApiException.badRequest("Send every floor of the building exactly once");
        }
        int n = req.floorIds().size();
        for (int i = 0; i < n; i++) {
            floors.get(req.floorIds().get(i)).setSortOrder(n - 1 - i);
        }
        auditService.log("FLOORS_REORDERED", "BUILDING", buildingId, req.floorIds().toString());
        return list(b.getId());
    }

    private void apply(Floor f, FloorRequest req) {
        f.setName(req.name().trim());
        f.setLevelNumber(req.levelNumber());
        f.setDescription(req.description());
        f.setSortOrder(req.sortOrder() == null ? req.levelNumber() : req.sortOrder());
    }

    private FloorResponse toResponse(Floor f, Building b) {
        return new FloorResponse(f.getId(), f.getBuildingId(), b.getHostelId(), f.getName(), f.getLevelNumber(),
                f.getDescription(), f.getSortOrder(), f.getPlanLayout(), f.isHasLift(), f.isHasStairs());
    }
}
