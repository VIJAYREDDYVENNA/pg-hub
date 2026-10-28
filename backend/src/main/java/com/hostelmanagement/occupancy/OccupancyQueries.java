package com.hostelmanagement.occupancy;

import com.hostelmanagement.hostel.dto.HostelDtos.OccupancyStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side SQL for occupancy. Uses set-based queries (no N+1) and always filters by an explicit list of
 * hostel IDs that the caller has already been authorized for.
 */
@Repository
@RequiredArgsConstructor
public class OccupancyQueries {

    /** Open, blocking maintenance on a specific bed. */
    static final String BED_MAINT = """
            EXISTS (SELECT 1 FROM maintenance_requests m WHERE m.bed_id = b.id AND m.blocks_availability = TRUE
                    AND m.status NOT IN ('RESOLVED','CLOSED'))""";
    /** Open, blocking maintenance on a whole room (no bed specified). */
    static final String ROOM_MAINT = """
            EXISTS (SELECT 1 FROM maintenance_requests m WHERE m.room_id = r.id AND m.bed_id IS NULL
                    AND m.blocks_availability = TRUE AND m.status NOT IN ('RESOLVED','CLOSED'))""";

    private static final String BED_STATE_SQL = """
            SELECT h.id hostel_id, h.name hostel_name, bl.id building_id, bl.name building_name,
                   f.id floor_id, f.name floor_name, f.level_number,
                   r.id room_id, r.room_number, r.room_type, r.status room_status, r.monthly_rent_per_bed,
                   r.security_deposit, r.amenities, r.plan_row, r.sort_order room_sort,
                   b.id bed_id, b.bed_label, b.status bed_status, b.rent_override,
                   ba.id alloc_id, ba.start_date alloc_start,
                   a.id admission_id, a.admission_date, a.expected_checkout_date, a.checkout_confirmed, a.monthly_rent,
                   res.id resident_id, res.full_name, res.mobile, res.whatsapp_opt_in,
                   bk.id booking_id, bk.guest_name, bk.expected_admission_date,
                   %s bed_maint, %s room_maint
            FROM rooms r
            JOIN floors f ON f.id = r.floor_id AND f.deleted_at IS NULL
            JOIN buildings bl ON bl.id = f.building_id AND bl.deleted_at IS NULL
            JOIN hostels h ON h.id = r.hostel_id AND h.deleted_at IS NULL
            LEFT JOIN beds b ON b.room_id = r.id AND b.deleted_at IS NULL AND b.status <> 'INACTIVE'
            LEFT JOIN bed_allocations ba ON ba.bed_id = b.id AND ba.end_date IS NULL
            LEFT JOIN admissions a ON a.id = ba.admission_id
            LEFT JOIN residents res ON res.id = a.resident_id
            LEFT JOIN bookings bk ON bk.bed_id = b.id AND bk.status = 'RESERVED'
            WHERE r.deleted_at IS NULL AND r.status <> 'INACTIVE' AND r.hostel_id IN (:hostelIds)
            """.formatted(BED_MAINT, ROOM_MAINT);

    // Leading newline matters: it separates ORDER BY from a preceding named parameter such as :floorId
    private static final String BED_STATE_ORDER = "\nORDER BY h.name, bl.sort_order, bl.name, f.sort_order, f.level_number, r.sort_order,"
            + " LENGTH(r.room_number), r.room_number, b.bed_label";

    private final NamedParameterJdbcTemplate jdbc;

    public List<BedStateRow> bedStates(Collection<Long> hostelIds, Long buildingId, Long floorId, Long roomId) {
        StringBuilder sql = new StringBuilder(BED_STATE_SQL);
        MapSqlParameterSource p = new MapSqlParameterSource("hostelIds", hostelIds);
        if (buildingId != null) {
            sql.append(" AND bl.id = :buildingId");
            p.addValue("buildingId", buildingId);
        }
        if (floorId != null) {
            sql.append(" AND f.id = :floorId");
            p.addValue("floorId", floorId);
        }
        if (roomId != null) {
            sql.append(" AND r.id = :roomId");
            p.addValue("roomId", roomId);
        }
        sql.append(BED_STATE_ORDER);
        return jdbc.query(sql.toString(), p, BED_STATE_MAPPER);
    }

    /** Bed states for an explicit set of beds (used by vacancy forecasts). */
    public List<BedStateRow> bedStatesForBeds(Collection<Long> hostelIds, Collection<Long> bedIds) {
        if (bedIds.isEmpty()) {
            return List.of();
        }
        String sql = BED_STATE_SQL + " AND b.id IN (:bedIds)" + BED_STATE_ORDER;
        return jdbc.query(sql, new MapSqlParameterSource("hostelIds", hostelIds).addValue("bedIds", bedIds),
                BED_STATE_MAPPER);
    }

    /** Aggregated bed counts per hostel, computed in the database. */
    public Map<Long, OccupancyStats> statsByHostel(Collection<Long> hostelIds) {
        String sql = """
                SELECT r.hostel_id,
                       COUNT(DISTINCT r.id) rooms,
                       COUNT(b.id) beds,
                       COALESCE(SUM(CASE WHEN ba.id IS NOT NULL THEN 1 ELSE 0 END), 0) occupied,
                       COALESCE(SUM(CASE WHEN b.id IS NOT NULL AND ba.id IS NULL AND (b.status = 'MAINTENANCE'
                            OR r.status = 'MAINTENANCE' OR %s OR %s) THEN 1 ELSE 0 END), 0) maintenance,
                       COALESCE(SUM(CASE WHEN b.id IS NOT NULL AND ba.id IS NULL AND bk.id IS NOT NULL
                            AND NOT (b.status = 'MAINTENANCE' OR r.status = 'MAINTENANCE' OR %s OR %s)
                            THEN 1 ELSE 0 END), 0) reserved
                FROM rooms r
                JOIN floors f ON f.id = r.floor_id AND f.deleted_at IS NULL
                JOIN buildings bl ON bl.id = f.building_id AND bl.deleted_at IS NULL
                LEFT JOIN beds b ON b.room_id = r.id AND b.deleted_at IS NULL AND b.status <> 'INACTIVE'
                LEFT JOIN bed_allocations ba ON ba.bed_id = b.id AND ba.end_date IS NULL
                LEFT JOIN bookings bk ON bk.bed_id = b.id AND bk.status = 'RESERVED'
                WHERE r.deleted_at IS NULL AND r.status <> 'INACTIVE' AND r.hostel_id IN (:hostelIds)
                GROUP BY r.hostel_id
                """.formatted(BED_MAINT, ROOM_MAINT, BED_MAINT, ROOM_MAINT);
        Map<Long, OccupancyStats> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("hostelIds", hostelIds), rs -> {
            long beds = rs.getLong("beds");
            long occupied = rs.getLong("occupied");
            long maintenance = rs.getLong("maintenance");
            long reserved = rs.getLong("reserved");
            long vacant = Math.max(0, beds - occupied - maintenance);
            result.put(rs.getLong("hostel_id"), new OccupancyStats(rs.getLong("rooms"), beds, occupied, vacant,
                    reserved, maintenance, percent(occupied, beds)));
        });
        return result;
    }

    public static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    public Map<Long, Long> buildingCounts(Collection<Long> hostelIds) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT hostel_id, COUNT(*) c FROM buildings WHERE deleted_at IS NULL AND hostel_id IN (:ids)
                GROUP BY hostel_id""", new MapSqlParameterSource("ids", hostelIds),
                rs -> {
                    result.put(rs.getLong("hostel_id"), rs.getLong("c"));
                });
        return result;
    }

    /** Per floor: number of rooms, beds and occupied beds (for the floor selector). */
    public Map<Long, int[]> floorCounts(Long buildingId) {
        Map<Long, int[]> result = new HashMap<>();
        jdbc.query("""
                SELECT r.floor_id, COUNT(DISTINCT r.id) rooms, COUNT(b.id) beds,
                       COALESCE(SUM(CASE WHEN ba.id IS NOT NULL THEN 1 ELSE 0 END), 0) occupied
                FROM rooms r JOIN floors f ON f.id = r.floor_id
                LEFT JOIN beds b ON b.room_id = r.id AND b.deleted_at IS NULL AND b.status <> 'INACTIVE'
                LEFT JOIN bed_allocations ba ON ba.bed_id = b.id AND ba.end_date IS NULL
                WHERE f.building_id = :buildingId AND r.deleted_at IS NULL AND r.status <> 'INACTIVE'
                GROUP BY r.floor_id""", new MapSqlParameterSource("buildingId", buildingId),
                rs -> {
                    result.put(rs.getLong("floor_id"),
                            new int[]{rs.getInt("rooms"), rs.getInt("beds"), rs.getInt("occupied")});
                });
        return result;
    }

    public long activeAdmissionsInHostel(Long hostelId) {
        return count("SELECT COUNT(*) FROM admissions WHERE hostel_id = :id AND status = 'ACTIVE'", hostelId);
    }

    public long activeAllocationsInBuilding(Long buildingId) {
        return count("""
                SELECT COUNT(*) FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id JOIN rooms r ON r.id = b.room_id
                JOIN floors f ON f.id = r.floor_id WHERE f.building_id = :id AND ba.end_date IS NULL""", buildingId);
    }

    public long activeAllocationsOnFloor(Long floorId) {
        return count("""
                SELECT COUNT(*) FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id JOIN rooms r ON r.id = b.room_id
                WHERE r.floor_id = :id AND ba.end_date IS NULL""", floorId);
    }

    public long activeAllocationsInRoom(Long roomId) {
        return count("""
                SELECT COUNT(*) FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                WHERE b.room_id = :id AND ba.end_date IS NULL""", roomId);
    }

    public long liveReservationsInRoom(Long roomId) {
        return count("""
                SELECT COUNT(*) FROM bookings bk JOIN beds b ON b.id = bk.bed_id
                WHERE b.room_id = :id AND bk.status = 'RESERVED'""", roomId);
    }

    public boolean bedIsOccupied(Long bedId) {
        return count("SELECT COUNT(*) FROM bed_allocations WHERE bed_id = :id AND end_date IS NULL", bedId) > 0;
    }

    public boolean bedIsReserved(Long bedId) {
        return count("SELECT COUNT(*) FROM bookings WHERE bed_id = :id AND status = 'RESERVED'", bedId) > 0;
    }

    /** True when the bed is blocked by bed/room status or an open blocking maintenance request. */
    public boolean bedUnderMaintenance(Long bedId) {
        return count("""
                SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id
                WHERE b.id = :id AND (b.status <> 'AVAILABLE' OR r.status <> 'AVAILABLE' OR %s OR %s)"""
                .formatted(BED_MAINT, ROOM_MAINT), bedId) > 0;
    }

    /** Outstanding and overdue balances per resident: [outstanding, overdue]. */
    public Map<Long, BigDecimal[]> balancesByResident(Collection<Long> residentIds, LocalDate today) {
        Map<Long, BigDecimal[]> result = new HashMap<>();
        if (residentIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT resident_id,
                       COALESCE(SUM(total_amount - amount_paid), 0) outstanding,
                       COALESCE(SUM(CASE WHEN due_date < :today THEN total_amount - amount_paid ELSE 0 END), 0) overdue
                FROM invoices WHERE resident_id IN (:ids) AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE')
                GROUP BY resident_id""",
                new MapSqlParameterSource("ids", residentIds).addValue("today", Date.valueOf(today)),
                rs -> {
                    result.put(rs.getLong("resident_id"),
                            new BigDecimal[]{rs.getBigDecimal("outstanding"), rs.getBigDecimal("overdue")});
                });
        return result;
    }

    private long count(String sql, Long id) {
        Long c = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Long.class);
        return c == null ? 0 : c;
    }

    private static LocalDate date(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static Long longOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static final RowMapper<BedStateRow> BED_STATE_MAPPER = (rs, i) -> new BedStateRow(
            rs.getLong("hostel_id"), rs.getString("hostel_name"),
            rs.getLong("building_id"), rs.getString("building_name"),
            rs.getLong("floor_id"), rs.getString("floor_name"), rs.getInt("level_number"),
            rs.getLong("room_id"), rs.getString("room_number"), rs.getString("room_type"), rs.getString("room_status"),
            rs.getBigDecimal("monthly_rent_per_bed"), rs.getBigDecimal("security_deposit"), rs.getString("amenities"),
            longOrNull(rs, "bed_id"), rs.getString("bed_label"), rs.getString("bed_status"),
            rs.getBigDecimal("rent_override"),
            longOrNull(rs, "alloc_id"), date(rs, "alloc_start"),
            longOrNull(rs, "admission_id"), date(rs, "admission_date"), date(rs, "expected_checkout_date"),
            rs.getBoolean("checkout_confirmed"), rs.getBigDecimal("monthly_rent"),
            longOrNull(rs, "resident_id"), rs.getString("full_name"), rs.getString("mobile"),
            rs.getBoolean("whatsapp_opt_in"),
            longOrNull(rs, "booking_id"), rs.getString("guest_name"), date(rs, "expected_admission_date"),
            rs.getBoolean("bed_maint"), rs.getBoolean("room_maint"),
            rs.getString("plan_row"), rs.getInt("room_sort"));
}
