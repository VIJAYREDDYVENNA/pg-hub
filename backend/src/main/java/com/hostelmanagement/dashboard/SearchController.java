package com.hostelmanagement.dashboard;

import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import com.hostelmanagement.security.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Search")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_HOSTEL_VIEW')")
public class SearchController {

    private final NamedParameterJdbcTemplate jdbc;
    private final HostelAccessService hostelAccess;

    public record Hit(String type, Long id, String title, String subtitle, String link) {
    }

    @Operation(summary = "Global search across hostels, rooms, residents, invoices and payments")
    @GetMapping
    public List<Hit> search(@RequestParam String q) {
        String term = q == null ? "" : q.trim();
        if (term.length() < 2) {
            return List.of();
        }
        AuthUser user = CurrentUser.get();
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelAccess.scope((Long) null))
                .addValue("q", "%" + term.replace("%", "\\%").replace("_", "\\_") + "%")
                .addValue("exact", term);
        List<Hit> hits = new java.util.ArrayList<>(jdbc.query("""
                SELECT id, name, city FROM hostels WHERE id IN (:ids) AND (name LIKE :q OR city LIKE :q) LIMIT 5""", p,
                (rs, n) -> new Hit("HOSTEL", rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                        "/hostels/" + rs.getLong("id"))));
        hits.addAll(jdbc.query("""
                SELECT r.id, r.room_number, h.name hostel, f.name floor FROM rooms r JOIN hostels h ON h.id = r.hostel_id
                JOIN floors f ON f.id = r.floor_id
                WHERE r.hostel_id IN (:ids) AND r.deleted_at IS NULL AND (r.room_number = :exact OR r.room_number LIKE :q)
                ORDER BY r.room_number = :exact DESC LIMIT 5""", p,
                (rs, n) -> new Hit("ROOM", rs.getLong("id"), "Room " + rs.getString("room_number"),
                        rs.getString("hostel") + " - " + rs.getString("floor"), "/rooms?roomId=" + rs.getLong("id"))));
        if (user.has(Permissions.RESIDENT_VIEW)) {
            hits.addAll(jdbc.query("""
                    SELECT res.id, res.full_name, res.mobile, res.status, h.name hostel FROM residents res
                    JOIN hostels h ON h.id = res.hostel_id
                    WHERE res.hostel_id IN (:ids) AND (res.full_name LIKE :q OR res.mobile LIKE :q) LIMIT 8""", p,
                    (rs, n) -> new Hit("RESIDENT", rs.getLong("id"), rs.getString("full_name"),
                            rs.getString("mobile") + " - " + rs.getString("hostel") + " - " + rs.getString("status"),
                            "/residents/" + rs.getLong("id"))));
        }
        if (user.has(Permissions.PAYMENT_VIEW)) {
            hits.addAll(jdbc.query("""
                    SELECT p.id, p.receipt_number, p.amount, res.full_name FROM payments p
                    JOIN residents res ON res.id = p.resident_id
                    WHERE p.hostel_id IN (:ids) AND (p.receipt_number LIKE :q OR p.transaction_reference LIKE :q) LIMIT 5""", p,
                    (rs, n) -> new Hit("PAYMENT", rs.getLong("id"), rs.getString("receipt_number"),
                            rs.getString("full_name") + " - Rs. " + rs.getBigDecimal("amount").toPlainString(),
                            "/payments/" + rs.getLong("id"))));
            hits.addAll(jdbc.query("""
                    SELECT i.id, i.invoice_number, i.total_amount - i.amount_paid bal, res.full_name, res.id rid
                    FROM invoices i JOIN residents res ON res.id = i.resident_id
                    WHERE i.hostel_id IN (:ids) AND i.invoice_number LIKE :q LIMIT 5""", p,
                    (rs, n) -> new Hit("INVOICE", rs.getLong("id"), rs.getString("invoice_number"),
                            rs.getString("full_name") + " - balance Rs. " + ((BigDecimal) rs.getBigDecimal("bal")).toPlainString(),
                            "/invoices/" + rs.getLong("id"))));
        }
        return hits;
    }
}
