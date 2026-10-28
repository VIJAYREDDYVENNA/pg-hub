package com.hostelmanagement.admin;

import com.hostelmanagement.auth.RefreshTokenRepository;
import com.hostelmanagement.auth.Role;
import com.hostelmanagement.auth.RoleRepository;
import com.hostelmanagement.auth.User;
import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.auth.dto.AuthDtos;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.owner.Owner;
import com.hostelmanagement.owner.OwnerRepository;
import com.hostelmanagement.owner.OwnerSettings;
import com.hostelmanagement.owner.OwnerSettingsRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.subscription.SubscriptionDtos;
import com.hostelmanagement.subscription.SubscriptionService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Platform administration: owner accounts, the hostels and managers beneath them, platform statistics and
 * application settings. Only reachable by SUPER_ADMIN; it exposes account and count data, never residents,
 * payments or other operational records.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "INACTIVE");
    private static final String PHONE_REGEX = "^[+0-9 ]{7,20}$";

    private final JdbcTemplate jdbc;
    private final OwnerRepository ownerRepository;
    private final OwnerSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SubscriptionService subscriptionService;

    // ------------------------------------------------------------------ DTOs

    public record CreateOwnerRequest(@NotBlank @Size(max = 150) String businessName,
                                     @NotBlank @Size(max = 150) String fullName,
                                     @NotBlank @Email @Size(max = 150) String email,
                                     @NotBlank @Pattern(regexp = PHONE_REGEX, message = "Enter a valid mobile number") String phone,
                                     @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String password,
                                     @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|INACTIVE") String status,
                                     @jakarta.validation.constraints.NotNull @jakarta.validation.Valid SubscriptionDtos.SubscriptionInput subscription) {
    }

    public record UpdateOwnerRequest(@NotBlank @Size(max = 150) String businessName,
                                     @NotBlank @Size(max = 150) String fullName,
                                     @NotBlank @Email @Size(max = 150) String email,
                                     @NotBlank @Pattern(regexp = PHONE_REGEX, message = "Enter a valid mobile number") String phone) {
    }

    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|INACTIVE") String status,
                                @Size(max = 300) String reason) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record SettingsRequest(@NotBlank @Size(max = 100) String platformName,
                                  @NotBlank @Email @Size(max = 150) String supportEmail) {
    }

    public record OwnerRow(Long id, String businessName, String contactName, String email, String phone, String status,
                           Instant createdAt, int hostels, int managers, int rooms, int beds, int residents,
                           String planName, String subscriptionStatus, java.time.LocalDate subscriptionEndsOn) {
    }

    public record HostelRow(Long id, String name, String hostelType, String city, String state, String status,
                            Long ownerId, String ownerName, int rooms, int beds, int residents, int managers,
                            Instant createdAt) {
    }

    public record ManagerRow(Long id, String fullName, String email, String phone, boolean enabled, int hostels,
                             Instant createdAt) {
    }

    public record ActivityRow(Long id, String action, String entityType, Long entityId, String details,
                              String actorName, Long ownerId, String ownerName, Instant createdAt) {
    }

    public record OwnerDetail(OwnerRow owner, List<HostelRow> hostels, List<ManagerRow> managers,
                              List<ActivityRow> activity) {
    }

    public record Count(String label, int value) {
    }

    public record GrowthPoint(String month, int owners, int hostels) {
    }

    public record Dashboard(int totalOwners, int activeOwners, int suspendedOwners, int inactiveOwners, int totalHostels,
                            int totalManagers, int totalResidents, int totalBeds,
                            List<OwnerRow> recentOwners, List<HostelRow> recentHostels, List<ActivityRow> recentActivity,
                            List<Count> ownersByStatus, List<Count> hostelsByOwner, List<GrowthPoint> growth) {
    }

    // ------------------------------------------------------------------ SQL

    private static final String OWNER_SQL = """
            SELECT o.id, o.name, o.email, o.phone, o.status, o.created_at,
              (SELECT u.full_name FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                 WHERE u.owner_id = o.id AND r.code = 'OWNER' ORDER BY u.id LIMIT 1) AS contact_name,
              (SELECT COUNT(*) FROM hostels h WHERE h.owner_id = o.id AND h.deleted_at IS NULL) AS hostels,
              (SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                 WHERE u.owner_id = o.id AND r.code = 'MANAGER') AS managers,
              (SELECT COUNT(*) FROM rooms rm JOIN hostels h ON h.id = rm.hostel_id
                 WHERE h.owner_id = o.id AND h.deleted_at IS NULL AND rm.deleted_at IS NULL) AS room_count,
              (SELECT COUNT(*) FROM beds b JOIN rooms rm ON rm.id = b.room_id JOIN hostels h ON h.id = rm.hostel_id
                 WHERE h.owner_id = o.id AND h.deleted_at IS NULL AND rm.deleted_at IS NULL AND b.deleted_at IS NULL) AS bed_count,
              (SELECT COUNT(*) FROM admissions a JOIN hostels h ON h.id = a.hostel_id
                 WHERE h.owner_id = o.id AND h.deleted_at IS NULL AND a.status = 'ACTIVE') AS resident_count,
              (SELECT p.name FROM owner_subscriptions s JOIN subscription_plans p ON p.id = s.plan_id WHERE s.owner_id = o.id) AS plan_name,
              (SELECT s.status FROM owner_subscriptions s WHERE s.owner_id = o.id) AS subscription_status,
              (SELECT CASE WHEN s.status = 'TRIAL' THEN s.trial_ends_on ELSE s.period_ends_on END
                 FROM owner_subscriptions s WHERE s.owner_id = o.id) AS subscription_ends_on
            FROM owners o""";

    private static final String HOSTEL_SQL = """
            SELECT h.id, h.name, h.hostel_type, h.city, h.state, h.status, h.owner_id, o.name AS owner_name, h.created_at,
              (SELECT COUNT(*) FROM rooms rm WHERE rm.hostel_id = h.id AND rm.deleted_at IS NULL) AS room_count,
              (SELECT COUNT(*) FROM beds b JOIN rooms rm ON rm.id = b.room_id
                 WHERE rm.hostel_id = h.id AND rm.deleted_at IS NULL AND b.deleted_at IS NULL) AS bed_count,
              (SELECT COUNT(*) FROM admissions a WHERE a.hostel_id = h.id AND a.status = 'ACTIVE') AS resident_count,
              (SELECT COUNT(DISTINCT a.user_id) FROM user_hostel_access a JOIN user_roles ur ON ur.user_id = a.user_id
                 JOIN roles r ON r.id = ur.role_id WHERE a.hostel_id = h.id AND r.code = 'MANAGER') AS manager_count
            FROM hostels h JOIN owners o ON o.id = h.owner_id WHERE h.deleted_at IS NULL""";

    // ------------------------------------------------------------------ owners

    @Transactional(readOnly = true)
    public List<OwnerRow> owners() {
        return jdbc.query(OWNER_SQL + " ORDER BY o.created_at DESC, o.id DESC", (rs, i) -> ownerRow(rs));
    }

    @Transactional(readOnly = true)
    public OwnerDetail owner(Long id) {
        OwnerRow owner = requireOwnerRow(id);
        List<HostelRow> hostels = jdbc.query(HOSTEL_SQL + " AND h.owner_id = ? ORDER BY h.name", (rs, i) -> hostelRow(rs), id);
        List<ManagerRow> managers = jdbc.query("""
                        SELECT u.id, u.full_name, u.email, u.phone, u.enabled, u.created_at,
                          (SELECT COUNT(*) FROM user_hostel_access a JOIN hostels h ON h.id = a.hostel_id
                             WHERE a.user_id = u.id AND h.deleted_at IS NULL) AS hostels
                        FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                        WHERE u.owner_id = ? AND r.code = 'MANAGER' ORDER BY u.full_name""",
                (rs, i) -> new ManagerRow(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("phone"), rs.getBoolean("enabled"), rs.getInt("hostels"), instant(rs, "created_at")), id);
        return new OwnerDetail(owner, hostels, managers, activity("WHERE l.owner_id = ?", 15, 0, id));
    }

    @Transactional
    public OwnerRow createOwner(CreateOwnerRequest req) {
        AuthUser me = CurrentUser.get();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String phone = req.phone().trim();
        assertEmailFree(email, null);
        assertPhoneFree(phone, null);
        Owner o = new Owner();
        o.setName(req.businessName().trim());
        o.setEmail(email);
        o.setPhone(phone);
        o.setStatus(req.status());
        ownerRepository.save(o);
        settingsRepository.save(OwnerSettings.defaults(o.getId()));
        Role ownerRole = roleRepository.findByCode("OWNER").orElseThrow();
        User u = new User();
        u.setOwnerId(o.getId());
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setPasswordChangedAt(Instant.now().minusSeconds(5));
        u.setRoles(new HashSet<>(Set.of(ownerRole)));
        userRepository.saveAndFlush(u);
        // Same transaction: an owner can never exist without a subscription
        subscriptionService.initialize(o.getId(), req.subscription(), me.userId());
        auditService.log(o.getId(), me.userId(), "OWNER_CREATED", "OWNER", o.getId(), o.getName() + " <" + email + ">");
        return requireOwnerRow(o.getId());
    }

    @Transactional
    public OwnerRow updateOwner(Long id, UpdateOwnerRequest req) {
        AuthUser me = CurrentUser.get();
        Owner o = ownerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Owner"));
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String phone = req.phone().trim();
        assertEmailFree(email, id);
        assertPhoneFree(phone, id);
        o.setName(req.businessName().trim());
        o.setEmail(email);
        o.setPhone(phone);
        for (User u : ownerUsers(id)) {
            u.setFullName(req.fullName().trim());
            u.setEmail(email);
            u.setPhone(phone);
        }
        userRepository.flush();
        ownerRepository.flush();
        auditService.log(id, me.userId(), "OWNER_UPDATED", "OWNER", id, o.getName());
        return requireOwnerRow(id);
    }

    @Transactional
    public OwnerRow setStatus(Long id, StatusRequest req) {
        AuthUser me = CurrentUser.get();
        Owner o = ownerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Owner"));
        if (!STATUSES.contains(req.status())) {
            throw ApiException.badRequest("Unknown status");
        }
        String before = o.getStatus();
        o.setStatus(req.status());
        ownerRepository.flush();
        if (!"ACTIVE".equals(req.status())) {
            // Access tokens stop working on the next request (the principal loader checks the owner status);
            // revoking refresh tokens signs everyone out for good.
            Instant now = Instant.now();
            jdbc.queryForList("SELECT id FROM users WHERE owner_id = ?", Long.class, id)
                    .forEach(uid -> refreshTokenRepository.revokeAllForUser(uid, now));
        }
        String reason = req.reason() == null || req.reason().isBlank() ? "" : " (" + req.reason().trim() + ")";
        auditService.log(id, me.userId(), "OWNER_STATUS_CHANGED", "OWNER", id, before + " -> " + req.status() + reason);
        return requireOwnerRow(id);
    }

    @Transactional
    public void resetOwnerPassword(Long id, String newPassword) {
        AuthUser me = CurrentUser.get();
        ownerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Owner"));
        Instant now = Instant.now();
        for (User u : ownerUsers(id)) {
            u.setPasswordHash(passwordEncoder.encode(newPassword));
            u.setPasswordChangedAt(now);
            u.setFailedLoginAttempts(0);
            u.setLockedUntil(null);
            refreshTokenRepository.revokeAllForUser(u.getId(), now);
        }
        auditService.log(id, me.userId(), "OWNER_PASSWORD_RESET", "OWNER", id, null);
    }

    // ------------------------------------------------------------------ hostels, activity, dashboard, settings

    @Transactional(readOnly = true)
    public List<HostelRow> hostels() {
        return jdbc.query(HOSTEL_SQL + " ORDER BY h.created_at DESC, h.id DESC", (rs, i) -> hostelRow(rs));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityRow> activityPage(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        long count = total == null ? 0 : total;
        List<ActivityRow> rows = activity("", safeSize, safePage * safeSize);
        return new PageResponse<>(rows, safePage, safeSize, count, (int) Math.ceil(count / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<OwnerRow> owners = owners();
        List<HostelRow> hostels = hostels();
        int active = (int) owners.stream().filter(o -> "ACTIVE".equals(o.status())).count();
        int suspended = (int) owners.stream().filter(o -> "SUSPENDED".equals(o.status())).count();
        int inactive = owners.size() - active - suspended;
        List<Count> byStatus = List.of(new Count("Active", active), new Count("Suspended", suspended),
                new Count("Inactive", inactive));
        List<Count> byOwner = owners.stream().filter(o -> o.hostels() > 0)
                .sorted((a, b) -> Integer.compare(b.hostels(), a.hostels())).limit(8)
                .map(o -> new Count(o.businessName(), o.hostels())).toList();
        return new Dashboard(owners.size(), active, suspended, inactive, hostels.size(),
                owners.stream().mapToInt(OwnerRow::managers).sum(), owners.stream().mapToInt(OwnerRow::residents).sum(),
                owners.stream().mapToInt(OwnerRow::beds).sum(),
                owners.stream().limit(5).toList(), hostels.stream().limit(5).toList(), activity("", 8, 0),
                byStatus, byOwner, growth(owners, hostels));
    }

    /** Cumulative owners and hostels at the end of each of the last six months. */
    private static List<GrowthPoint> growth(List<OwnerRow> owners, List<HostelRow> hostels) {
        List<GrowthPoint> points = new ArrayList<>();
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        for (int back = 5; back >= 0; back--) {
            YearMonth m = now.minusMonths(back);
            Instant end = m.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            int o = (int) owners.stream().filter(x -> x.createdAt().isBefore(end)).count();
            int h = (int) hostels.stream().filter(x -> x.createdAt().isBefore(end)).count();
            points.add(new GrowthPoint(m.toString(), o, h));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public Map<String, String> settings() {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT setting_key, setting_value FROM platform_settings ORDER BY setting_key",
                rs -> {
                    out.put(rs.getString(1), rs.getString(2));
                });
        return out;
    }

    @Transactional
    public Map<String, String> updateSettings(SettingsRequest req) {
        AuthUser me = CurrentUser.get();
        upsert("PLATFORM_NAME", req.platformName().trim());
        upsert("SUPPORT_EMAIL", req.supportEmail().trim());
        auditService.log(null, me.userId(), "PLATFORM_SETTINGS_UPDATED", "PLATFORM", null, req.platformName().trim());
        return settings();
    }

    private void upsert(String key, String value) {
        jdbc.update("""
                INSERT INTO platform_settings (setting_key, setting_value, updated_at) VALUES (?, ?, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)""", key, value);
    }

    // ------------------------------------------------------------------ helpers

    private List<User> ownerUsers(Long ownerId) {
        return userRepository.findByOwnerIdOrderByFullName(ownerId).stream()
                .filter(u -> u.hasRole("OWNER")).toList();
    }

    private void assertEmailFree(String email, Long exceptOwnerId) {
        Integer owners = jdbc.queryForObject(
                "SELECT COUNT(*) FROM owners WHERE LOWER(email) = ? AND (? IS NULL OR id <> ?)",
                Integer.class, email, exceptOwnerId, exceptOwnerId);
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = ? AND (? IS NULL OR owner_id IS NULL OR owner_id <> ?)",
                Integer.class, email, exceptOwnerId, exceptOwnerId);
        if ((owners != null && owners > 0) || (users != null && users > 0)) {
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    private void assertPhoneFree(String phone, Long exceptOwnerId) {
        Integer owners = jdbc.queryForObject("SELECT COUNT(*) FROM owners WHERE phone = ? AND (? IS NULL OR id <> ?)",
                Integer.class, phone, exceptOwnerId, exceptOwnerId);
        if (owners != null && owners > 0) {
            throw ApiException.conflict("An owner with this mobile number already exists");
        }
    }

    private OwnerRow requireOwnerRow(Long id) {
        List<OwnerRow> rows = jdbc.query(OWNER_SQL + " WHERE o.id = ?", (rs, i) -> ownerRow(rs), id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Owner");
        }
        return rows.get(0);
    }

    private List<ActivityRow> activity(String where, int limit, int offset, Object... args) {
        List<Object> params = new ArrayList<>(List.of(args));
        params.add(limit);
        params.add(offset);
        String sql = """
                SELECT l.id, l.action, l.entity_type, l.entity_id, l.details, l.owner_id, l.created_at,
                       u.full_name AS actor, o.name AS owner_name
                FROM audit_logs l LEFT JOIN users u ON u.id = l.user_id LEFT JOIN owners o ON o.id = l.owner_id
                """ + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql,
                (rs, i) -> new ActivityRow(rs.getLong("id"), rs.getString("action"), rs.getString("entity_type"),
                        (Long) rs.getObject("entity_id"), platformVisibleDetails(rs.getString("action"), rs.getString("details")),
                        rs.getString("actor"),
                        (Long) rs.getObject("owner_id"), rs.getString("owner_name"), instant(rs, "created_at")),
                params.toArray());
    }

    /** Action prefixes whose audit details describe platform/account/structure events, not residents or money. */
    private static final List<String> PLATFORM_SAFE_ACTIONS = List.of("OWNER_", "PLATFORM_", "LOGIN", "ACCOUNT_LOCKED",
            "PASSWORD_", "PROFILE_", "USER_", "SUBSCRIPTION_", "PLAN_", "HOSTEL_", "BUILDING_", "FLOOR_", "ROOM_", "BED_CREATED", "BED_UPDATED",
            "BED_DELETED", "SETTINGS_", "STAFF_", "TEMPLATE_");

    /**
     * The platform administrator manages accounts but must not read tenants' resident or payment data. Audit details
     * for operational actions (payments, admissions, refunds, reminders ...) name residents and amounts, so the feed
     * shows only that the action happened.
     */
    static String platformVisibleDetails(String action, String details) {
        if (details == null || action == null) {
            return null;
        }
        return PLATFORM_SAFE_ACTIONS.stream().anyMatch(action::startsWith) ? details : null;
    }

    private static OwnerRow ownerRow(ResultSet rs) throws SQLException {
        return new OwnerRow(rs.getLong("id"), rs.getString("name"), rs.getString("contact_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("status"), instant(rs, "created_at"), rs.getInt("hostels"),
                rs.getInt("managers"), rs.getInt("room_count"), rs.getInt("bed_count"), rs.getInt("resident_count"),
                rs.getString("plan_name"), rs.getString("subscription_status"),
                rs.getObject("subscription_ends_on", java.time.LocalDate.class));
    }

    private static HostelRow hostelRow(ResultSet rs) throws SQLException {
        return new HostelRow(rs.getLong("id"), rs.getString("name"), rs.getString("hostel_type"), rs.getString("city"),
                rs.getString("state"), rs.getString("status"), rs.getLong("owner_id"), rs.getString("owner_name"),
                rs.getInt("room_count"), rs.getInt("bed_count"), rs.getInt("resident_count"), rs.getInt("manager_count"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime t = rs.getObject(column, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
