package com.hostelmanagement.dashboard;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.hostel.dto.HostelDtos.OccupancyStats;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative vacancy forecast. A bed becomes available when the active allocation on it has an expected
 * checkout date in the window. Each bed has at most one active allocation (enforced by a unique index), so a bed
 * can never be counted twice. Beds with a live reservation are reported as "reserved", not as net vacancies.
 */
@Service
@RequiredArgsConstructor
public class VacancyForecastService {

    private final NamedParameterJdbcTemplate jdbc;
    private final OccupancyQueries occupancyQueries;
    private final HostelAccessService hostelAccess;
    private final AppClock clock;

    public record VacancyItem(Long hostelId, String hostelName, String buildingName, String floorName, Long roomId,
                              String roomNumber, Long bedId, String bedLabel, Long residentId, String residentName,
                              String residentMobile, Long admissionId, LocalDate expectedCheckoutDate,
                              boolean confirmed, String source, long daysRemaining, boolean overdue,
                              int bedsBecomingAvailable, int roomBedsFreeingInWindow, String bookingStatus,
                              Long bookingId, String reservedFor, LocalDate reservedFrom) {
    }

    public record VacancySummary(long currentlyAvailableBeds, long reservedBeds, long maintenanceBeds,
                                 long occupiedBeds, long totalBeds, long expectedToBecomeAvailable,
                                 long expectedButReserved, long overdueCheckouts, long confirmedCheckouts,
                                 long estimatedCheckouts, long within7Days, long within15Days, long within30Days) {
    }

    public record VacancyForecast(LocalDate from, LocalDate to, VacancySummary summary, List<VacancyItem> items) {
    }

    @Transactional(readOnly = true)
    public VacancyForecast forecast(Long hostelId, Integer days, LocalDate from, LocalDate to) {
        LocalDate today = clock.today();
        // HOSTEL_VIEW alone shows the vacancy picture, not who is leaving or how to contact them
        boolean identity = com.hostelmanagement.occupancy.ResidentPrivacy.canSeeIdentity();
        LocalDate start = from == null ? today : from;
        LocalDate end = to != null ? to : start.plusDays(days == null ? 30 : days);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("End date must be on or after the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw ApiException.badRequest("Choose a range of at most one year");
        }
        boolean includeOverdue = !start.isAfter(today);
        List<Long> scope = hostelAccess.scope(hostelId);
        MapSqlParameterSource p = new MapSqlParameterSource("ids", scope)
                .addValue("from", Date.valueOf(includeOverdue ? LocalDate.of(1970, 1, 1) : start))
                .addValue("to", Date.valueOf(end));
        List<Object[]> raw = new ArrayList<>();
        jdbc.query("""
                SELECT a.id admission_id, a.expected_checkout_date, a.checkout_confirmed, res.id resident_id,
                       res.full_name, res.mobile, b.id bed_id, b.bed_label, r.id room_id, r.room_number,
                       f.name floor_name, bl.name building_name, h.id hostel_id, h.name hostel_name,
                       bk.id booking_id, bk.guest_name, bk.expected_admission_date
                FROM admissions a
                JOIN bed_allocations ba ON ba.admission_id = a.id AND ba.end_date IS NULL
                JOIN beds b ON b.id = ba.bed_id
                JOIN rooms r ON r.id = b.room_id
                JOIN floors f ON f.id = r.floor_id
                JOIN buildings bl ON bl.id = f.building_id
                JOIN hostels h ON h.id = r.hostel_id
                JOIN residents res ON res.id = a.resident_id
                LEFT JOIN bookings bk ON bk.bed_id = b.id AND bk.status = 'RESERVED'
                WHERE a.status = 'ACTIVE' AND r.hostel_id IN (:ids) AND a.expected_checkout_date IS NOT NULL
                  AND a.expected_checkout_date BETWEEN :from AND :to
                ORDER BY a.expected_checkout_date, h.name, LENGTH(r.room_number), r.room_number, b.bed_label""", p, rs -> {
            long bookingId = rs.getLong("booking_id");
            boolean noBooking = rs.wasNull();
            Date bkDate = rs.getDate("expected_admission_date");
            raw.add(new Object[]{rs.getLong("hostel_id"), rs.getString("hostel_name"), rs.getString("building_name"),
                    rs.getString("floor_name"), rs.getLong("room_id"), rs.getString("room_number"), rs.getLong("bed_id"),
                    rs.getString("bed_label"), rs.getLong("resident_id"), rs.getString("full_name"),
                    rs.getString("mobile"), rs.getLong("admission_id"), rs.getDate("expected_checkout_date").toLocalDate(),
                    rs.getBoolean("checkout_confirmed"), noBooking ? null : bookingId, rs.getString("guest_name"),
                    bkDate == null ? null : bkDate.toLocalDate()});
        });
        Map<Long, Integer> perRoom = new HashMap<>();
        raw.forEach(o -> perRoom.merge((Long) o[4], 1, Integer::sum));
        List<VacancyItem> items = new ArrayList<>();
        long reservedAmong = 0, overdue = 0, confirmed = 0, w7 = 0, w15 = 0, w30 = 0;
        for (Object[] o : raw) {
            LocalDate date = (LocalDate) o[12];
            long daysLeft = ChronoUnit.DAYS.between(today, date);
            boolean isOverdue = date.isBefore(today);
            boolean conf = (Boolean) o[13];
            Long bookingId = (Long) o[14];
            if (bookingId != null) {
                reservedAmong++;
            }
            if (isOverdue) {
                overdue++;
            }
            if (conf) {
                confirmed++;
            }
            if (daysLeft <= 7) {
                w7++;
            }
            if (daysLeft <= 15) {
                w15++;
            }
            if (daysLeft <= 30) {
                w30++;
            }
            items.add(new VacancyItem((Long) o[0], (String) o[1], (String) o[2], (String) o[3], (Long) o[4],
                    (String) o[5], (Long) o[6], (String) o[7], (Long) o[8], identity ? (String) o[9] : com.hostelmanagement.occupancy.ResidentPrivacy.HIDDEN_NAME, identity ? (String) o[10] : null,
                    (Long) o[11], date, conf, conf ? "CONFIRMED_CHECKOUT" : "ESTIMATED_CHECKOUT", daysLeft, isOverdue,
                    1, perRoom.get((Long) o[4]), bookingId == null ? "NOT_RESERVED" : "RESERVED", bookingId,
                    (String) o[15], (LocalDate) o[16]));
        }
        OccupancyStats totals = total(occupancyQueries.statsByHostel(scope));
        VacancySummary summary = new VacancySummary(totals.vacantBeds() - totals.reservedBeds(), totals.reservedBeds(),
                totals.maintenanceBeds(), totals.occupiedBeds(), totals.totalBeds(), items.size(), reservedAmong,
                overdue, confirmed, items.size() - confirmed, w7, w15, w30);
        return new VacancyForecast(start, end, summary, items);
    }

    /** Count of active admissions with expected checkout in [today, today+days]. */
    public long vacatingWithin(List<Long> scope, int days) {
        LocalDate today = clock.today();
        Long c = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admissions a JOIN bed_allocations ba ON ba.admission_id = a.id AND ba.end_date IS NULL
                WHERE a.status = 'ACTIVE' AND a.hostel_id IN (:ids)
                AND a.expected_checkout_date BETWEEN :from AND :to""",
                new MapSqlParameterSource("ids", scope).addValue("from", Date.valueOf(today))
                        .addValue("to", Date.valueOf(today.plusDays(days))), Long.class);
        return c == null ? 0 : c;
    }

    public long overdueCheckouts(List<Long> scope) {
        Long c = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admissions a WHERE a.status = 'ACTIVE' AND a.hostel_id IN (:ids)
                AND a.expected_checkout_date < :today""",
                new MapSqlParameterSource("ids", scope).addValue("today", Date.valueOf(clock.today())), Long.class);
        return c == null ? 0 : c;
    }

    static OccupancyStats total(Map<Long, OccupancyStats> byHostel) {
        long rooms = 0, beds = 0, occ = 0, vac = 0, res = 0, maint = 0;
        for (OccupancyStats s : byHostel.values()) {
            rooms += s.totalRooms();
            beds += s.totalBeds();
            occ += s.occupiedBeds();
            vac += s.vacantBeds();
            res += s.reservedBeds();
            maint += s.maintenanceBeds();
        }
        return new OccupancyStats(rooms, beds, occ, vac, res, maint, OccupancyQueries.percent(occ, beds));
    }
}
