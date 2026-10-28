package com.hostelmanagement.admission;

import com.hostelmanagement.admission.dto.AdmissionDtos.AdmissionDto;
import com.hostelmanagement.admission.dto.AdmissionDtos.AllocationDto;
import com.hostelmanagement.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read models for admissions and their allocation history. */
@Repository
@RequiredArgsConstructor
public class AdmissionQueries {

    private static final String ADMISSION_SELECT = """
            SELECT a.id, a.resident_id, res.full_name, res.mobile, a.hostel_id, h.name hostel_name, a.admission_date,
                   a.expected_checkout_date, a.checkout_confirmed, a.actual_checkout_date, a.monthly_rent,
                   a.security_deposit, a.billing_cycle, a.cycle_months, a.status, a.notes, a.checkout_notes
            FROM admissions a JOIN residents res ON res.id = a.resident_id JOIN hostels h ON h.id = a.hostel_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<AdmissionDto> forResident(Long residentId) {
        return load(ADMISSION_SELECT + " WHERE a.resident_id = :rid ORDER BY a.admission_date DESC, a.id DESC",
                new MapSqlParameterSource("rid", residentId));
    }

    public AdmissionDto get(Long admissionId) {
        List<AdmissionDto> list = load(ADMISSION_SELECT + " WHERE a.id = :id", new MapSqlParameterSource("id", admissionId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Recent admissions or checkouts in the given hostels. type: ADMITTED | CHECKED_OUT | ACTIVE. */
    public PageResponse<AdmissionDto> search(Collection<Long> hostelIds, String type, LocalDate from, LocalDate to,
                                             int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE a.hostel_id IN (:ids)");
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds);
        String dateCol = "a.admission_date";
        if ("CHECKED_OUT".equals(type)) {
            where.append(" AND a.status = 'CHECKED_OUT'");
            dateCol = "a.actual_checkout_date";
        } else if ("ACTIVE".equals(type)) {
            where.append(" AND a.status = 'ACTIVE'");
        }
        if (from != null) {
            where.append(" AND ").append(dateCol).append(" >= :from");
            p.addValue("from", Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND ").append(dateCol).append(" <= :to");
            p.addValue("to", Date.valueOf(to));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM admissions a" + where, p, Long.class);
        int safeSize = Math.min(Math.max(size, 1), 100);
        p.addValue("limit", safeSize).addValue("offset", Math.max(page, 0) * safeSize);
        List<AdmissionDto> content = load(ADMISSION_SELECT + where + " ORDER BY " + dateCol + " DESC, a.id DESC"
                + " LIMIT :limit OFFSET :offset", p);
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, Math.max(page, 0), safeSize, t, (int) Math.ceil(t / (double) safeSize));
    }

    private List<AdmissionDto> load(String sql, MapSqlParameterSource p) {
        Map<Long, Object[]> base = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            base.put(rs.getLong("id"), new Object[]{rs.getLong("resident_id"), rs.getString("full_name"),
                    rs.getString("mobile"), rs.getLong("hostel_id"), rs.getString("hostel_name"),
                    date(rs, "admission_date"), date(rs, "expected_checkout_date"), rs.getBoolean("checkout_confirmed"),
                    date(rs, "actual_checkout_date"), rs.getBigDecimal("monthly_rent"),
                    rs.getBigDecimal("security_deposit"), rs.getString("billing_cycle"), rs.getInt("cycle_months"),
                    rs.getString("status"), rs.getString("notes"), rs.getString("checkout_notes")});
        });
        if (base.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AllocationDto>> allocations = allocations(base.keySet());
        List<AdmissionDto> result = new ArrayList<>();
        base.forEach((id, o) -> {
            List<AllocationDto> allocs = allocations.getOrDefault(id, List.of());
            AllocationDto current = allocs.stream().filter(x -> x.endDate() == null).findFirst()
                    .orElse(allocs.isEmpty() ? null : allocs.get(0));
            result.add(new AdmissionDto(id, (Long) o[0], (String) o[1], (String) o[2], (Long) o[3], (String) o[4],
                    (LocalDate) o[5], (LocalDate) o[6], (Boolean) o[7], (LocalDate) o[8], (java.math.BigDecimal) o[9],
                    (java.math.BigDecimal) o[10], (String) o[11], (Integer) o[12], (String) o[13], (String) o[14],
                    (String) o[15], current, allocs));
        });
        return result;
    }

    private Map<Long, List<AllocationDto>> allocations(Collection<Long> admissionIds) {
        Map<Long, List<AllocationDto>> map = new LinkedHashMap<>();
        jdbc.query("""
                SELECT ba.id, ba.admission_id, ba.bed_id, b.bed_label, r.id room_id, r.room_number, f.name floor_name,
                       bl.name building_name, h.id hostel_id, h.name hostel_name, ba.start_date, ba.end_date,
                       ba.end_reason, ba.notes
                FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id JOIN rooms r ON r.id = b.room_id
                JOIN floors f ON f.id = r.floor_id JOIN buildings bl ON bl.id = f.building_id
                JOIN hostels h ON h.id = r.hostel_id
                WHERE ba.admission_id IN (:ids) ORDER BY ba.start_date DESC, ba.id DESC""",
                new MapSqlParameterSource("ids", admissionIds), rs -> {
                    map.computeIfAbsent(rs.getLong("admission_id"), k -> new ArrayList<>()).add(new AllocationDto(
                            rs.getLong("id"), rs.getLong("bed_id"), rs.getString("bed_label"), rs.getLong("room_id"),
                            rs.getString("room_number"), rs.getString("floor_name"), rs.getString("building_name"),
                            rs.getLong("hostel_id"), rs.getString("hostel_name"), date(rs, "start_date"),
                            date(rs, "end_date"), rs.getString("end_reason"), rs.getString("notes")));
                });
        return map;
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }
}
