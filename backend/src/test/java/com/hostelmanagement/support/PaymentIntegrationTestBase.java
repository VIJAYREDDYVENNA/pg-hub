package com.hostelmanagement.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.payments.gateway.MockPaymentGatewayService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared fixtures for the payment integration tests. Real controllers, services, Flyway-migrated MySQL schema and the
 * real security chain are used; only the payment provider is the in-memory mock gateway. Every test creates its own
 * uniquely named owners/hostels/residents, so tests do not interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
public abstract class PaymentIntegrationTestBase {

    private static final AtomicInteger IPS = new AtomicInteger(10);

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;

    protected Tenant t;

    /** One owner with two hostels, a manager of the first hostel, and a resident in each hostel. */
    public record Tenant(long ownerId, AuthUser owner, AuthUser manager, AuthUser viewer, long hostelId, long hostel2Id,
                            long residentId, long resident2Id) {
    }

    @BeforeEach
    void createTenant() {
        t = newTenant();
    }

    protected Tenant newTenant() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        long ownerId = insert("INSERT INTO owners (name, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', ?, ?)",
                "Owner " + s, "owner-" + s + "@test.dev", now(), now());
        subscribe(ownerId, "ENTERPRISE");
        jdbc.update("INSERT INTO owner_settings (owner_id, updated_at) VALUES (?, UTC_TIMESTAMP(6))", ownerId);
        long ownerUser = user(ownerId, "owner-user-" + s);
        long managerUser = user(ownerId, "manager-user-" + s);
        long viewerUser = user(ownerId, "viewer-user-" + s);
        long h1 = hostel(ownerId, "Hostel One " + s);
        long h2 = hostel(ownerId, "Hostel Two " + s);
        jdbc.update("INSERT INTO user_hostel_access (user_id, hostel_id) VALUES (?, ?)", managerUser, h1);
        jdbc.update("INSERT INTO user_hostel_access (user_id, hostel_id) VALUES (?, ?)", viewerUser, h1);
        long r1 = resident(ownerId, h1, "Asha Rao " + s);
        long r2 = resident(ownerId, h2, "Bala Nair " + s);
        AuthUser owner = new AuthUser(ownerUser, ownerId, "o@test.dev", "Owner", Set.of("OWNER"), Permissions.ALL);
        AuthUser manager = new AuthUser(managerUser, ownerId, "m@test.dev", "Manager", Set.of("MANAGER"),
                Set.of(Permissions.HOSTEL_VIEW, Permissions.RESIDENT_VIEW, Permissions.PAYMENT_VIEW, Permissions.PAYMENT_COLLECT));
        AuthUser viewer = new AuthUser(viewerUser, ownerId, "v@test.dev", "Viewer", Set.of("STAFF"),
                Set.of(Permissions.HOSTEL_VIEW, Permissions.PAYMENT_VIEW));
        return new Tenant(ownerId, owner, manager, viewer, h1, h2, r1, r2);
    }

    // ------------------------------------------------------------------ data

    protected long invoice(Tenant tenant, long hostelId, long residentId, String total, LocalDate due) {
        String number = "INV-T-" + UUID.randomUUID().toString().substring(0, 8);
        return insert("""
                INSERT INTO invoices (owner_id, hostel_id, resident_id, invoice_number, invoice_type, issue_date, due_date,
                    subtotal, discount_amount, late_fee_amount, total_amount, amount_paid, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'RENT', ?, ?, ?, 0, 0, ?, 0, 'PENDING', 0, ?, ?)""",
                tenant.ownerId(), hostelId, residentId, number, java.sql.Date.valueOf(LocalDate.now().minusDays(20)),
                java.sql.Date.valueOf(due), new BigDecimal(total), new BigDecimal(total), now(), now());
    }

    protected long invoice(String total) {
        return invoice(t, t.hostelId(), t.residentId(), total, LocalDate.now().plusDays(5));
    }

    protected long user(long ownerId, String name) {
        return insert("""
                INSERT INTO users (owner_id, full_name, email, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, 'x', TRUE, ?, ?)""", ownerId, name, name + "@test.dev", now(), now());
    }

    protected long hostel(long ownerId, String name) {
        return insert("""
                INSERT INTO hostels (owner_id, name, hostel_type, address, city, state, pin_code, status, created_at, updated_at)
                VALUES (?, ?, 'BOYS', '1 Test Road', 'Pune', 'MH', '411001', 'ACTIVE', ?, ?)""", ownerId, name, now(), now());
    }

    protected long resident(long ownerId, long hostelId, String name) {
        return insert("""
                INSERT INTO residents (owner_id, hostel_id, full_name, mobile, status, created_at, updated_at)
                VALUES (?, ?, ?, '9876500000', 'ACTIVE', ?, ?)""", ownerId, hostelId, name, now(), now());
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    protected long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Gives an owner an open-ended ACTIVE subscription on the given seeded plan (no overrides). */
    protected void subscribe(long ownerId, String planCode) {
        jdbc.update("""
                INSERT INTO owner_subscriptions (owner_id, plan_id, status, starts_on, grace_days, billing_status, created_at, updated_at)
                SELECT ?, id, 'ACTIVE', CURDATE(), 7, 'NOT_REQUIRED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM subscription_plans WHERE code = ?""", ownerId, planCode);
    }

    // ------------------------------------------------------------------ structure, occupancy and user fixtures

    protected long building(long hostelId) {
        return insert("INSERT INTO buildings (hostel_id, name, created_at, updated_at) VALUES (?, 'Block A', ?, ?)",
                hostelId, now(), now());
    }

    protected long floor(long buildingId) {
        return insert("INSERT INTO floors (building_id, name, level_number, created_at, updated_at) VALUES (?, 'Ground', 0, ?, ?)",
                buildingId, now(), now());
    }

    protected long room(long hostelId, long floorId, String number) {
        return insert("""
                INSERT INTO rooms (hostel_id, floor_id, room_number, room_type, monthly_rent_per_bed, security_deposit,
                    created_at, updated_at) VALUES (?, ?, ?, 'DOUBLE', 6000, 5000, ?, ?)""",
                hostelId, floorId, number, now(), now());
    }

    protected long bed(long roomId, String label) {
        return insert("INSERT INTO beds (room_id, bed_label, created_at, updated_at) VALUES (?, ?, ?, ?)",
                roomId, label, now(), now());
    }

    /** An ACTIVE admission with an open bed allocation. */
    protected long admit(long residentId, long hostelId, long bedId, LocalDate expectedCheckout) {
        long admission = insert("""
                INSERT INTO admissions (resident_id, hostel_id, admission_date, expected_checkout_date, monthly_rent,
                    security_deposit, created_at, updated_at) VALUES (?, ?, ?, ?, 6000, 5000, ?, ?)""",
                residentId, hostelId, java.sql.Date.valueOf(LocalDate.now().minusDays(10)),
                expectedCheckout == null ? null : java.sql.Date.valueOf(expectedCheckout), now(), now());
        jdbc.update("INSERT INTO bed_allocations (admission_id, bed_id, start_date, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                admission, bedId, java.sql.Date.valueOf(LocalDate.now().minusDays(10)), now(), now());
        return admission;
    }

    /** A real user row (needed for FK columns and audit) plus its role, permissions and hostel access. */
    protected AuthUser makeUser(Tenant tenant, String role, Set<String> permissions, long... hostelIds) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        long id = user(tenant.ownerId(), role.toLowerCase() + "-" + s);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, role);
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_permissions (user_id, permission_id) SELECT ?, id FROM permissions WHERE code = ?", id, p);
        }
        for (long h : hostelIds) {
            jdbc.update("INSERT INTO user_hostel_access (user_id, hostel_id) VALUES (?, ?)", id, h);
        }
        return new AuthUser(id, tenant.ownerId(), role.toLowerCase() + "-" + s + "@test.dev", role + " " + s,
                Set.of(role), Set.copyOf(permissions));
    }

    /** Turns the owner's automatic reminders on or off and sets how many days before the due date the first one goes out. */
    protected void autoReminders(long ownerId, boolean enabled, int daysBefore) {
        jdbc.update("UPDATE owner_settings SET auto_reminders_enabled = ?, reminder_days_before = ? WHERE owner_id = ?", enabled, daysBefore, ownerId);
    }

    protected void whatsAppConsent(long residentId, boolean optedIn) {
        jdbc.update("UPDATE residents SET whatsapp_opt_in = ? WHERE id = ?", optedIn, residentId);
    }

    protected Set<Long> accessOf(long userId) {
        return new java.util.HashSet<>(jdbc.queryForList("SELECT hostel_id FROM user_hostel_access WHERE user_id = ?", Long.class, userId));
    }

    // ------------------------------------------------------------------ database assertions helpers

    protected BigDecimal amountPaid(long invoiceId) {
        return jdbc.queryForObject("SELECT amount_paid FROM invoices WHERE id = ?", BigDecimal.class, invoiceId);
    }

    protected String invoiceStatus(long invoiceId) {
        return jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?", String.class, invoiceId);
    }

    protected int onlinePaymentCount(long residentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE resident_id = ? AND method = 'ONLINE'",
                Integer.class, residentId);
    }

    protected int paymentCount(long residentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE resident_id = ?", Integer.class, residentId);
    }

    protected String attemptStatus(long attemptId) {
        return jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id = ?", String.class, attemptId);
    }

    protected static void assertMoney(String expected, BigDecimal actual) {
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------------ http

    protected RequestPostProcessor as(AuthUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    /** A distinct client IP per call keeps the public-endpoint rate limiter out of unrelated tests. */
    protected RequestPostProcessor freshIp() {
        String ip = "10.9." + (IPS.incrementAndGet() / 250) + "." + (IPS.get() % 250);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected MvcResult send(MockHttpServletRequestBuilder req, AuthUser as, Object body) throws Exception {
        if (as != null) {
            req.with(as(as));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Starts a staff checkout and returns the response JSON; asserts nothing so callers can test failures. */
    protected MvcResult startOrder(AuthUser user, long invoiceId, String amount) throws Exception {
        return send(post("/api/online-payments/invoices/{id}/orders", invoiceId), user, Map.of("amount", new BigDecimal(amount)));
    }

    protected record Order(long attemptId, String orderId) {
    }

    protected Order order(AuthUser user, long invoiceId, String amount) throws Exception {
        MvcResult r = startOrder(user, invoiceId, amount);
        org.junit.jupiter.api.Assertions.assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode n = body(r);
        return new Order(n.get("attemptId").asLong(), n.get("checkout").get("orderId").asText());
    }

    protected MvcResult confirm(AuthUser user, String orderId, String outcome) throws Exception {
        return send(post("/api/online-payments/confirm"), user, Map.of("providerOrderId", orderId,
                "fields", Map.of("mock_payment_id", "pay_mock_" + UUID.randomUUID().toString().substring(0, 8),
                        "mock_outcome", outcome)));
    }

    protected MvcResult webhook(String eventId, String type, String orderId, String paymentId, String state,
                                long amountMinor, String currency) throws Exception {
        String payload = json.writeValueAsString(Map.of("id", eventId, "type", type, "orderId", orderId,
                "paymentId", paymentId, "state", state, "amountMinor", amountMinor, "currency", currency));
        return rawWebhook(payload, signature(payload));
    }

    protected MvcResult rawWebhook(String payload, String signature) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/payments/webhook/mock").contentType(MediaType.APPLICATION_JSON)
                .content(payload);
        if (signature != null) {
            req.header(MockPaymentGatewayService.SIGNATURE_HEADER, signature);
        }
        return mvc.perform(req).andReturn();
    }

    protected static String signature(String payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(MockPaymentGatewayService.WEBHOOK_SECRET.getBytes(), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    protected MvcResult getAs(AuthUser user, String url, Object... vars) throws Exception {
        return send(get(url, vars), user, null);
    }
}
