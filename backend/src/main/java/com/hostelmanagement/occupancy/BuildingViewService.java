package com.hostelmanagement.occupancy;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.hostel.Hostel;
import com.hostelmanagement.hostel.HostelService;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BedView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BuildingNode;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.BuildingView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.FloorNode;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.ResidentOnBed;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.RoomView;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.StructureFloor;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.StructureNode;
import com.hostelmanagement.occupancy.dto.OccupancyDtos.StructureRoom;
import com.hostelmanagement.owner.OwnerSettingsService;
import com.hostelmanagement.room.Room;
import com.hostelmanagement.room.RoomRepository;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BuildingViewService {

    private final HostelService hostelService;
    private final HostelAccessService hostelAccess;
    private final OccupancyQueries queries;
    private final OwnerSettingsService settingsService;
    private final RoomRepository roomRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppClock clock;

    public int warningDays() {
        return settingsService.forOwner(CurrentUser.get().ownerId()).getVacancyWarningDays();
    }

    @Transactional(readOnly = true)
    public BuildingView buildingView(Long hostelId, Long buildingId, Long floorId) {
        Hostel hostel = hostelService.getEntity(hostelId);
        List<BuildingNode> buildings = buildingNodes(hostelId);
        Long selectedBuilding = null;
        if (buildingId != null) {
            selectedBuilding = buildings.stream().filter(b -> b.id().equals(buildingId)).findFirst()
                    .map(BuildingNode::id).orElseThrow(() -> ApiException.notFound("Building"));
        } else if (!buildings.isEmpty()) {
            selectedBuilding = buildings.get(0).id();
        }
        Long selectedFloor = null;
        if (selectedBuilding != null) {
            Long sb = selectedBuilding;
            List<FloorNode> floors = buildings.stream().filter(b -> b.id().equals(sb)).findFirst()
                    .map(BuildingNode::floors).orElse(List.of());
            if (floorId != null) {
                selectedFloor = floors.stream().filter(f -> f.id().equals(floorId)).findFirst()
                        .map(FloorNode::id).orElseThrow(() -> ApiException.notFound("Floor"));
            } else if (!floors.isEmpty()) {
                selectedFloor = floors.get(0).id();
            }
        }
        int warningDays = warningDays();
        List<RoomView> rooms = selectedFloor == null ? List.of()
                : RoomStatusCalculator.toRooms(queries.bedStates(List.of(hostelId), selectedBuilding, selectedFloor, null),
                clock.today(), warningDays);
        return ResidentPrivacy.redact(new BuildingView(hostelId, hostel.getName(), buildings, selectedBuilding,
                selectedFloor, warningDays, RoomStatusCalculator.summarize(rooms), rooms));
    }

    /** Room with bed-by-bed details plus each occupant's outstanding/overdue balance. */
    @Transactional(readOnly = true)
    public RoomView roomDetails(Long roomId) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId).orElseThrow(() -> ApiException.notFound("Room"));
        hostelAccess.checkEntity(room.getHostelId(), "Room");
        List<BedStateRow> rows = queries.bedStates(List.of(room.getHostelId()), null, null, roomId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Room");
        }
        RoomView view = RoomStatusCalculator.toRoom(rows, clock.today(), warningDays());
        List<Long> residentIds = view.beds().stream().filter(b -> b.resident() != null)
                .map(b -> b.resident().residentId()).toList();
        // Balances are only computed for callers allowed to see them
        Map<Long, BigDecimal[]> balances = ResidentPrivacy.canSeeMoney()
                ? queries.balancesByResident(residentIds, clock.today()) : Map.of();
        List<BedView> beds = new ArrayList<>();
        for (BedView b : view.beds()) {
            if (b.resident() == null) {
                beds.add(b);
                continue;
            }
            ResidentOnBed r = b.resident();
            BigDecimal[] bal = balances.getOrDefault(r.residentId(), new BigDecimal[]{Money.ZERO, Money.ZERO});
            String status = bal[1].signum() > 0 ? "OVERDUE" : bal[0].signum() > 0 ? "DUE" : "PAID_UP";
            beds.add(new BedView(b.id(), b.label(), b.state(), b.bedStatus(), b.maintenance(), b.rent(),
                    new ResidentOnBed(r.residentId(), r.admissionId(), r.name(), r.mobile(), r.whatsappOptIn(),
                            r.admissionDate(), r.allocatedSince(), r.expectedCheckoutDate(), r.checkoutConfirmed(),
                            r.daysToCheckout(), r.vacatingSoon(), r.checkoutOverdue(), r.monthlyRent(),
                            Money.of(bal[0]), Money.of(bal[1]), status),
                    b.reservation()));
        }
        return ResidentPrivacy.redact(new RoomView(view.id(), view.roomNumber(), view.roomType(), view.configuredStatus(), view.hostelId(),
                view.hostelName(), view.buildingId(), view.buildingName(), view.floorId(), view.floorName(),
                view.floorLevel(), view.status(), view.vacatingSoon(), view.roomUnderMaintenance(), view.totalBeds(),
                view.occupiedBeds(), view.availableBeds(), view.reservedBeds(), view.maintenanceBeds(),
                view.vacatingSoonBeds(), view.nextCheckoutDate(), view.monthlyRentPerBed(), view.securityDeposit(),
                view.amenities(), beds, view.planRow(), view.sortOrder()));
    }

    /** Rooms across accessible hostels with server-side filters. */
    @Transactional(readOnly = true)
    public List<RoomView> rooms(Long hostelId, Long buildingId, Long floorId, String statusFilter, String q) {
        List<Long> scope = hostelAccess.scope(hostelId);
        List<RoomView> rooms = RoomStatusCalculator.toRooms(queries.bedStates(scope, buildingId, floorId, null),
                clock.today(), warningDays());
        String query = q == null ? "" : q.trim().toLowerCase();
        // Searching by resident name would reveal who lives where, so it is limited to RESIDENT_VIEW holders
        boolean searchNames = ResidentPrivacy.canSeeIdentity();
        return ResidentPrivacy.redact(rooms.stream()
                .filter(r -> matchesStatus(r, statusFilter))
                .filter(r -> query.isEmpty() || r.roomNumber().toLowerCase().contains(query)
                        || (searchNames && r.beds().stream().anyMatch(b -> b.resident() != null
                        && b.resident().name().toLowerCase().contains(query))))
                .toList());
    }

    static boolean matchesStatus(RoomView r, String filter) {
        if (filter == null || filter.isBlank() || filter.equals("ALL")) {
            return true;
        }
        return switch (filter) {
            case "VACATING_SOON" -> r.vacatingSoon();
            case "HAS_AVAILABLE" -> r.availableBeds() > 0;
            default -> r.status().name().equals(filter);
        };
    }

    @Transactional(readOnly = true)
    public List<StructureNode> structure(Long hostelId) {
        hostelService.getEntity(hostelId);
        MapSqlParameterSource p = new MapSqlParameterSource("hostelId", hostelId);
        Map<Long, StructureNodeBuilder> buildings = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, name, description, status, sort_order FROM buildings
                WHERE hostel_id = :hostelId AND deleted_at IS NULL ORDER BY sort_order, name""", p, rs -> {
            buildings.put(rs.getLong("id"), new StructureNodeBuilder(rs.getLong("id"), rs.getString("name"),
                    rs.getString("description"), rs.getString("status"), rs.getInt("sort_order")));
        });
        Map<Long, StructureFloorBuilder> floors = new LinkedHashMap<>();
        jdbc.query("""
                SELECT f.id, f.building_id, f.name, f.level_number, f.description, f.sort_order, f.plan_layout,
                       f.has_lift, f.has_stairs FROM floors f
                JOIN buildings b ON b.id = f.building_id
                WHERE b.hostel_id = :hostelId AND b.deleted_at IS NULL AND f.deleted_at IS NULL
                ORDER BY f.sort_order, f.level_number""", p, rs -> {
            var fb = new StructureFloorBuilder(rs.getLong("id"), rs.getString("name"), rs.getInt("level_number"),
                    rs.getString("description"), rs.getInt("sort_order"), rs.getString("plan_layout"),
                    rs.getBoolean("has_lift"), rs.getBoolean("has_stairs"));
            floors.put(fb.id, fb);
            StructureNodeBuilder parent = buildings.get(rs.getLong("building_id"));
            if (parent != null) {
                parent.floors.add(fb);
            }
        });
        jdbc.query("""
                SELECT r.id, r.floor_id, r.room_number, r.room_type, r.monthly_rent_per_bed, r.security_deposit,
                       r.amenities, r.status, r.plan_row, r.sort_order,
                       (SELECT COUNT(*) FROM beds b WHERE b.room_id = r.id AND b.deleted_at IS NULL) beds
                FROM rooms r WHERE r.hostel_id = :hostelId AND r.deleted_at IS NULL
                ORDER BY r.sort_order, LENGTH(r.room_number), r.room_number""", p, rs -> {
            StructureFloorBuilder parent = floors.get(rs.getLong("floor_id"));
            if (parent != null) {
                parent.rooms.add(new StructureRoom(rs.getLong("id"), rs.getString("room_number"),
                        rs.getString("room_type"), rs.getInt("beds"), rs.getBigDecimal("monthly_rent_per_bed"),
                        rs.getBigDecimal("security_deposit"), rs.getString("amenities"), rs.getString("status"),
                        rs.getString("plan_row"), rs.getInt("sort_order")));
            }
        });
        return buildings.values().stream().map(StructureNodeBuilder::build).toList();
    }

    private List<BuildingNode> buildingNodes(Long hostelId) {
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, List<FloorNode>> floorsByBuilding = new LinkedHashMap<>();
        MapSqlParameterSource p = new MapSqlParameterSource("hostelId", hostelId);
        jdbc.query("""
                SELECT id, name FROM buildings WHERE hostel_id = :hostelId AND deleted_at IS NULL
                AND status = 'ACTIVE' ORDER BY sort_order, name""", p, rs -> {
            names.put(rs.getLong("id"), rs.getString("name"));
            floorsByBuilding.put(rs.getLong("id"), new ArrayList<>());
        });
        if (names.isEmpty()) {
            return List.of();
        }
        Map<Long, int[]> counts = new java.util.HashMap<>();
        names.keySet().forEach(bid -> counts.putAll(queries.floorCounts(bid)));
        jdbc.query("""
                SELECT id, building_id, name, level_number, sort_order, plan_layout, has_lift, has_stairs FROM floors
                WHERE building_id IN (:ids) AND deleted_at IS NULL ORDER BY sort_order, level_number""",
                new MapSqlParameterSource("ids", names.keySet()), rs -> {
                    long id = rs.getLong("id");
                    int[] c = counts.getOrDefault(id, new int[]{0, 0, 0});
                    floorsByBuilding.get(rs.getLong("building_id")).add(new FloorNode(id, rs.getString("name"),
                            rs.getInt("level_number"), c[0], c[1], c[2], rs.getInt("sort_order"),
                            rs.getString("plan_layout"), rs.getBoolean("has_lift"), rs.getBoolean("has_stairs")));
                });
        List<BuildingNode> list = new ArrayList<>();
        names.forEach((id, name) -> list.add(new BuildingNode(id, name, floorsByBuilding.get(id))));
        return list;
    }

    private static final class StructureNodeBuilder {
        final Long id;
        final String name, description, status;
        final int sortOrder;
        final List<StructureFloorBuilder> floors = new ArrayList<>();

        StructureNodeBuilder(Long id, String name, String description, String status, int sortOrder) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.status = status;
            this.sortOrder = sortOrder;
        }

        StructureNode build() {
            return new StructureNode(id, name, description, status, sortOrder,
                    floors.stream().map(StructureFloorBuilder::build).toList());
        }
    }

    private static final class StructureFloorBuilder {
        final Long id;
        final String name, description, planLayout;
        final int level, sortOrder;
        final boolean hasLift, hasStairs;
        final List<StructureRoom> rooms = new ArrayList<>();

        StructureFloorBuilder(Long id, String name, int level, String description, int sortOrder, String planLayout,
                              boolean hasLift, boolean hasStairs) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.description = description;
            this.sortOrder = sortOrder;
            this.planLayout = planLayout;
            this.hasLift = hasLift;
            this.hasStairs = hasStairs;
        }

        StructureFloor build() {
            return new StructureFloor(id, name, level, description, sortOrder, planLayout, hasLift, hasStairs, rooms);
        }
    }
}
