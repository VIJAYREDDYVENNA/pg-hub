package com.hostelmanagement.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.Permissions;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Plans, owner subscriptions, usage limits, expiry states and entitlements through the real API and MySQL. */
class SubscriptionTest extends PaymentIntegrationTestBase {

    @Autowired SubscriptionBackfill backfill;

    // ------------------------------------------------------------------ helpers

    private AuthUser superAdmin() {
        return new AuthUser(t.owner().userId(), null, "sa@test.dev", "Super Admin", Set.of("SUPER_ADMIN"), Set.of());
    }

    private long planId(String code) {
        return jdbc.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
    }

    private Map<String, Object> sub(String plan, Object... extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("planId", planId(plan));
        for (int i = 0; i < extra.length; i += 2) {
            m.put((String) extra[i], extra[i + 1]);
        }
        return m;
    }

    private Map<String, Object> ownerRequest(Map<String, Object> subscription) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> m = new HashMap<>();
        m.put("businessName", "Biz " + s);
        m.put("fullName", "Owner " + s);
        m.put("email", "new-" + s + "@test.dev");
        m.put("phone", "+91 9" + String.format("%09d", Math.abs(s.hashCode()) % 1_000_000_000L));
        m.put("password", "Passw0rd1x");
        m.put("status", "ACTIVE");
        if (subscription != null) {
            m.put("subscription", subscription);
        }
        return m;
    }

    private record NewOwner(long ownerId, AuthUser user) {
    }

    private NewOwner createOwner(Map<String, Object> subscription) throws Exception {
        MvcResult r = send(post("/api/admin/owners"), superAdmin(), ownerRequest(subscription));
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        long ownerId = body(r).get("id").asLong();
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE owner_id = ? ORDER BY id LIMIT 1", Long.class, ownerId);
        return new NewOwner(ownerId, new AuthUser(userId, ownerId, "o@test.dev", "Owner", Set.of("OWNER"), Permissions.ALL));
    }

    private JsonNode subscriptionOf(long ownerId) throws Exception {
        return body(getAs(superAdmin(), "/api/admin/owners/{id}/subscription", ownerId));
    }

    private Map<String, Object> hostelBody(String name) {
        return Map.of("name", name, "hostelType", "BOYS", "address", "1 Road", "city", "Pune", "state", "MH", "pinCode", "411001");
    }

    private MvcResult newHostel(AuthUser owner) throws Exception {
        return send(post("/api/hostels"), owner, hostelBody("H-" + UUID.randomUUID().toString().substring(0, 6)));
    }

    private long floorOf(long ownerId, long hostelId) {
        return floor(building(hostelId));
    }

    private MvcResult newRoom(AuthUser owner, long floorId, String number, int beds) throws Exception {
        return send(post("/api/rooms"), owner, Map.of("floorId", floorId, "roomNumber", number, "roomType", "DOUBLE",
                "totalBeds", beds, "monthlyRentPerBed", 5000, "securityDeposit", 1000));
    }

    private Map<String, Object> updateBody(long ownerId, String plan, String status, LocalDate start, LocalDate trialEnd,
                                           LocalDate periodEnd, Object hostels, Object beds, Object managers) {
        Map<String, Object> m = new HashMap<>();
        m.put("planId", planId(plan));
        m.put("status", status);
        m.put("startsOn", start.toString());
        m.put("trialEndsOn", trialEnd == null ? null : trialEnd.toString());
        m.put("periodEndsOn", periodEnd == null ? null : periodEnd.toString());
        m.put("graceDays", 7);
        m.put("customMaxHostels", hostels);
        m.put("customMaxBedsPerHostel", beds);
        m.put("customMaxManagers", managers);
        return m;
    }

    private MvcResult updateSubscription(long ownerId, Map<String, Object> body) throws Exception {
        return send(put("/api/admin/owners/{id}/subscription", ownerId), superAdmin(), body);
    }

    // ------------------------------------------------------------------ plan catalogue

    @Test
    void seededPlans_matchTheApprovedDefaults_andAreStoredInTheDatabase() throws Exception {
        JsonNode plans = body(getAs(superAdmin(), "/api/admin/plans"));
        Map<String, JsonNode> byCode = new HashMap<>();
        plans.forEach(p -> byCode.put(p.get("code").asText(), p));

        assertPlan(byCode.get("STARTER"), "199.00", 1, 50, 1);
        assertPlan(byCode.get("GROWTH"), "399.00", 3, 100, 5);
        assertPlan(byCode.get("BUSINESS"), "799.00", 10, 200, 15);
        assertTrue(byCode.get("ENTERPRISE").get("customPricing").asBoolean());
        assertTrue(byCode.get("ENTERPRISE").get("monthlyPrice").isNull());
        byCode.values().forEach(p -> {
            assertEquals(14, p.get("trialDays").asInt());
            assertEquals(7, p.get("graceDays").asInt());
        });
    }

    private void assertPlan(JsonNode p, String price, int hostels, int beds, int managers) {
        assertEquals(price, p.get("monthlyPrice").decimalValue().setScale(2).toPlainString());
        assertEquals(hostels, p.get("maxHostels").asInt());
        assertEquals(beds, p.get("maxBedsPerHostel").asInt());
        assertEquals(managers, p.get("maxManagers").asInt());
    }

    @Test
    void superAdmin_canCreateEditDeactivateAndArchivePlans() throws Exception {
        String code = "TEST" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Map<String, Object> plan = new HashMap<>(Map.of("code", code, "name", "Test plan", "monthlyPrice", 99, "maxHostels", 2,
                "maxBedsPerHostel", 20, "maxManagers", 2, "trialDays", 5, "graceDays", 3, "customPricing", false,
                "entitlements", Map.of("ADVANCED_REPORTS", true)));

        MvcResult created = send(post("/api/admin/plans"), superAdmin(), plan);
        long id = body(created).get("id").asLong();
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        assertTrue(body(created).get("entitlements").get("ADVANCED_REPORTS").asBoolean());
        assertFalse(body(created).get("entitlements").get("CUSTOM_BRANDING").asBoolean());

        plan.put("maxHostels", 4);
        plan.put("name", "Test plan v2");
        assertEquals(4, body(send(put("/api/admin/plans/{id}", id), superAdmin(), plan)).get("maxHostels").asInt());
        assertEquals(409, send(post("/api/admin/plans"), superAdmin(), plan).getResponse().getStatus(), "duplicate code");

        assertEquals("INACTIVE", body(send(put("/api/admin/plans/{id}/status", id), superAdmin(), Map.of("status", "INACTIVE"))).get("status").asText());
        assertEquals(400, send(post("/api/admin/owners"), superAdmin(), ownerRequest(Map.of("planId", id))).getResponse().getStatus(),
                "an inactive plan cannot be assigned");
        assertEquals("ARCHIVED", body(send(put("/api/admin/plans/{id}/status", id), superAdmin(), Map.of("status", "ARCHIVED"))).get("status").asText());
        assertEquals(409, send(put("/api/admin/plans/{id}/status", id), superAdmin(), Map.of("status", "ACTIVE")).getResponse().getStatus());
        assertEquals(409, send(put("/api/admin/plans/{id}", id), superAdmin(), plan).getResponse().getStatus(), "archived plans are frozen");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'PLAN_CREATED' AND entity_id = ?", Integer.class, id));
    }

    @Test
    void planValidation_rejectsInvalidConfiguration() throws Exception {
        Map<String, Object> base = Map.of("code", "BADPLAN", "name", "Bad", "monthlyPrice", 10, "maxHostels", 1,
                "maxBedsPerHostel", 10, "maxManagers", 1, "trialDays", 14, "graceDays", 7, "customPricing", false);
        for (Map.Entry<String, Object> bad : Map.<String, Object>of("maxHostels", 0, "maxBedsPerHostel", 0, "trialDays", -1,
                "graceDays", 91, "monthlyPrice", -5, "code", "lower case").entrySet()) {
            Map<String, Object> req = new HashMap<>(base);
            req.put(bad.getKey(), bad.getValue());
            assertEquals(400, send(post("/api/admin/plans"), superAdmin(), req).getResponse().getStatus(), bad.getKey());
        }
        Map<String, Object> unknownFeature = new HashMap<>(base);
        unknownFeature.put("entitlements", Map.of("TELEPORTATION", true));
        assertEquals(400, send(post("/api/admin/plans"), superAdmin(), unknownFeature).getResponse().getStatus());
        Map<String, Object> noPrice = new HashMap<>(base);
        noPrice.remove("monthlyPrice");
        assertEquals(400, send(post("/api/admin/plans"), superAdmin(), noPrice).getResponse().getStatus(), "price or custom pricing required");
    }

    @Test
    void onlySuperAdmin_canAdministerPlansAndSubscriptions() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));
        Map<String, Object> body = updateBody(o.ownerId(), "BUSINESS", "ACTIVE", LocalDate.now(), null, null, null, null, null);

        for (AuthUser user : List.of(t.owner(), t.manager(), o.user())) {
            assertEquals(403, getAs(user, "/api/admin/plans").getResponse().getStatus());
            assertEquals(403, send(put("/api/admin/owners/{id}/subscription", o.ownerId()), user, body).getResponse().getStatus());
            assertEquals(403, send(post("/api/admin/owners/{id}/subscription/payments", o.ownerId()), user, Map.of("amount", 10, "paidOn", LocalDate.now().toString(), "method", "CASH", "extendMonths", 1)).getResponse().getStatus());
        }
        assertEquals("STARTER", subscriptionOf(o.ownerId()).get("plan").get("code").asText());
    }

    // ------------------------------------------------------------------ owner creation

    @Test
    void creatingAnOwner_initialisesTheSubscriptionFromThePlan() throws Exception {
        NewOwner o = createOwner(sub("GROWTH"));

        JsonNode s = subscriptionOf(o.ownerId());
        assertEquals("GROWTH", s.get("plan").get("code").asText());
        assertEquals("TRIAL", s.get("status").asText());
        assertEquals("TRIAL", s.get("accessState").asText());
        assertEquals(LocalDate.now().toString(), s.get("startsOn").asText());
        assertEquals(LocalDate.now().plusDays(14).toString(), s.get("trialEndsOn").asText());
        assertEquals(7, s.get("graceDays").asInt());
        assertEquals("NOT_REQUIRED", s.get("billingStatus").asText());
        assertEquals(3, s.get("effectiveLimits").get("maxHostels").asInt());
        assertEquals(100, s.get("effectiveLimits").get("maxBedsPerHostel").asInt());
        assertEquals(5, s.get("effectiveLimits").get("maxManagers").asInt());
        assertFalse(s.get("overridesPlan").asBoolean());
        assertEquals("CREATED", s.get("events").get(0).get("eventType").asText());
        assertNotNull(s.get("events").get(0).get("actorName").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE owner_id = ? AND action = 'SUBSCRIPTION_CREATED'",
                Integer.class, o.ownerId()));
        // The owner list shows the plan
        JsonNode list = body(getAs(superAdmin(), "/api/admin/owners"));
        boolean listed = false;
        for (JsonNode row : list) {
            if (row.get("id").asLong() == o.ownerId()) {
                listed = "Growth".equals(row.get("planName").asText()) && "TRIAL".equals(row.get("subscriptionStatus").asText());
            }
        }
        assertTrue(listed);
    }

    @Test
    void creatingAnOwner_withCustomLimitsAndDates_savesTheOverrides() throws Exception {
        LocalDate start = LocalDate.now().plusDays(2);
        NewOwner o = createOwner(sub("STARTER", "customMaxHostels", 4, "customMaxBedsPerHostel", 75, "customMaxManagers", 3,
                "trialDays", 30, "graceDays", 10, "startsOn", start.toString()));

        JsonNode s = subscriptionOf(o.ownerId());
        assertTrue(s.get("overridesPlan").asBoolean());
        assertEquals(4, s.get("effectiveLimits").get("maxHostels").asInt());
        assertEquals(75, s.get("effectiveLimits").get("maxBedsPerHostel").asInt());
        assertEquals(3, s.get("effectiveLimits").get("maxManagers").asInt());
        assertEquals(1, s.get("planLimits").get("maxHostels").asInt(), "the plan itself is unchanged");
        assertEquals(start.plusDays(30).toString(), s.get("trialEndsOn").asText());
        assertEquals(10, s.get("graceDays").asInt());
    }

    @Test
    void explicitExpiryAndZeroTrial_createAnActivePaidPeriodSubscription() throws Exception {
        LocalDate expiry = LocalDate.now().plusMonths(3);
        NewOwner o = createOwner(sub("BUSINESS", "trialDays", 0, "expiresOn", expiry.toString()));

        JsonNode s = subscriptionOf(o.ownerId());
        assertEquals("ACTIVE", s.get("status").asText());
        assertEquals(expiry.toString(), s.get("periodEndsOn").asText());
        assertEquals("UNPAID", s.get("billingStatus").asText(), "billing is tracked separately from status");
        assertTrue(s.get("trialEndsOn").isNull());
    }

    @Test
    void invalidSubscriptionInput_isRejected_andNoOwnerIsLeftBehind() throws Exception {
        int ownersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM owners", Integer.class);
        long inactivePlan = insert("""
                INSERT INTO subscription_plans (code, name, monthly_price, max_hostels, max_beds_per_hostel, max_managers,
                    status, created_at, updated_at) VALUES (?, 'Hidden', 1, 1, 1, 1, 'INACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))""",
                "HID" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());

        List<Map<String, Object>> bad = List.of(
                new HashMap<>(), // missing plan id
                sub("STARTER", "planId", 999_999_999L),
                Map.of("planId", inactivePlan),
                sub("STARTER", "trialDays", -1),
                sub("STARTER", "trialDays", 400),
                sub("STARTER", "graceDays", 100),
                sub("STARTER", "customMaxHostels", 0),
                sub("STARTER", "customMaxBedsPerHostel", 0),
                sub("STARTER", "customMaxHostels", 5000),
                sub("STARTER", "startsOn", LocalDate.now().toString(), "expiresOn", LocalDate.now().minusDays(1).toString()));
        for (Map<String, Object> s : bad) {
            MvcResult r = send(post("/api/admin/owners"), superAdmin(), ownerRequest(s));
            assertEquals(400, r.getResponse().getStatus(), s + " -> " + r.getResponse().getContentAsString());
        }
        assertEquals(400, send(post("/api/admin/owners"), superAdmin(), ownerRequest(null)).getResponse().getStatus(),
                "a subscription is mandatory: there is no way to create an owner without one");
        assertEquals(ownersBefore, jdbc.queryForObject("SELECT COUNT(*) FROM owners", Integer.class),
                "failed creations must not leave owner rows (transactional)");
    }

    @Test
    void ownerAndSubscription_areCreatedAtomically() throws Exception {
        Map<String, Object> req = ownerRequest(sub("STARTER"));
        String email = (String) req.get("email");
        // Force a failure after the owner row is written: a duplicate email is detected before, so break the plan instead
        long deleted = planId("STARTER");
        jdbc.update("UPDATE subscription_plans SET status = 'INACTIVE' WHERE id = ?", deleted);
        try {
            MvcResult r = send(post("/api/admin/owners"), superAdmin(), req);
            assertEquals(400, r.getResponse().getStatus());
        } finally {
            jdbc.update("UPDATE subscription_plans SET status = 'ACTIVE' WHERE id = ?", deleted);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM owners WHERE email = ?", Integer.class, email));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email));
    }

    // ------------------------------------------------------------------ hostel limit

    @Test
    void hostelLimit_blocksTheExtraHostel_andUpgradeAllowsMore() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));

        assertEquals(201, newHostel(o.user()).getResponse().getStatus());
        MvcResult blocked = newHostel(o.user());
        assertEquals(403, blocked.getResponse().getStatus());
        assertEquals("PLAN_LIMIT_REACHED", body(blocked).get("code").asText());
        assertTrue(body(blocked).get("message").asText().contains("1 hostel"), body(blocked).get("message").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ?", Integer.class, o.ownerId()));

        assertEquals(200, updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "TRIAL", LocalDate.now(),
                LocalDate.now().plusDays(14), null, null, null, null)).getResponse().getStatus());
        assertEquals(201, newHostel(o.user()).getResponse().getStatus());
        assertEquals(201, newHostel(o.user()).getResponse().getStatus());
        assertEquals(403, newHostel(o.user()).getResponse().getStatus(), "Growth allows 3");
    }

    @Test
    void deletedHostels_doNotCountTowardTheLimit() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));
        long id = body(newHostel(o.user())).get("id").asLong();
        assertEquals(403, newHostel(o.user()).getResponse().getStatus());

        assertTrue(send(delete("/api/hostels/{id}", id), o.user(), null).getResponse().getStatus() < 300);

        assertEquals(201, newHostel(o.user()).getResponse().getStatus());
    }

    @Test
    void concurrentHostelCreation_neverExceedsTheLimit() throws Exception {
        NewOwner o = createOwner(sub("GROWTH"));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return newHostel(o.user()).getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();

        assertEquals(3, created, "exactly the Growth limit may be created");
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ? AND deleted_at IS NULL", Integer.class, o.ownerId()));
    }

    // ------------------------------------------------------------------ bed limit (per hostel)

    @Test
    void bedLimit_isPerHostel_andCountsEveryNonDeletedBed() throws Exception {
        NewOwner o = createOwner(sub("GROWTH", "customMaxBedsPerHostel", 6));
        long h1 = body(newHostel(o.user())).get("id").asLong();
        long h2 = body(newHostel(o.user())).get("id").asLong();
        long f1 = floorOf(o.ownerId(), h1);
        long f2 = floorOf(o.ownerId(), h2);

        assertEquals(201, newRoom(o.user(), f1, "1", 4).getResponse().getStatus());
        MvcResult tooMany = newRoom(o.user(), f1, "2", 3);
        assertEquals(403, tooMany.getResponse().getStatus());
        assertEquals("PLAN_LIMIT_REACHED", body(tooMany).get("code").asText());
        assertEquals(201, newRoom(o.user(), f1, "3", 2).getResponse().getStatus(), "4 + 2 = 6 exactly fits");
        assertEquals(403, newRoom(o.user(), f1, "4", 1).getResponse().getStatus(), "51st-style boundary: one more is refused");
        // The other hostel has its own allowance
        assertEquals(201, newRoom(o.user(), f2, "1", 6).getResponse().getStatus());
        assertEquals(6, jdbc.queryForObject("""
                SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id WHERE r.hostel_id = ? AND b.deleted_at IS NULL""",
                Integer.class, h1));
    }

    @Test
    void inactiveBeds_stillCount_andDeletingARoomFreesTheQuota() throws Exception {
        NewOwner o = createOwner(sub("GROWTH", "customMaxBedsPerHostel", 3));
        long h = body(newHostel(o.user())).get("id").asLong();
        long floor = floorOf(o.ownerId(), h);
        long room = body(newRoom(o.user(), floor, "1", 3)).get("id").asLong();
        jdbc.update("UPDATE beds SET status = 'INACTIVE' WHERE room_id = ?", room);

        assertEquals(403, newRoom(o.user(), floor, "2", 1).getResponse().getStatus(), "parking beds as INACTIVE must not evade the cap");

        assertTrue(send(delete("/api/rooms/{id}", room), o.user(), null).getResponse().getStatus() < 300);
        assertEquals(201, newRoom(o.user(), floor, "2", 3).getResponse().getStatus());
    }

    @Test
    void addingASingleBed_isLimited_andTheRoomCapOfThirtyAlsoApplies() throws Exception {
        NewOwner o = createOwner(sub("ENTERPRISE"));
        long h = body(newHostel(o.user())).get("id").asLong();
        long floor = floorOf(o.ownerId(), h);
        long room = body(newRoom(o.user(), floor, "1", 30)).get("id").asLong();

        MvcResult thirtyOne = send(post("/api/beds"), o.user(), Map.of("roomId", room, "bedLabel", "Z1"));

        assertEquals(409, thirtyOne.getResponse().getStatus(), "BedService.create used to bypass the 30-bed room cap");
        assertEquals(30, jdbc.queryForObject("SELECT COUNT(*) FROM beds WHERE room_id = ? AND deleted_at IS NULL", Integer.class, room));

        // Per-hostel limit through the single-bed endpoint
        NewOwner small = createOwner(sub("STARTER", "customMaxBedsPerHostel", 2));
        long sh = body(newHostel(small.user())).get("id").asLong();
        long sRoom = body(newRoom(small.user(), floorOf(small.ownerId(), sh), "1", 2)).get("id").asLong();
        MvcResult blocked = send(post("/api/beds"), small.user(), Map.of("roomId", sRoom, "bedLabel", "X9"));
        assertEquals(403, blocked.getResponse().getStatus());
        assertEquals("PLAN_LIMIT_REACHED", body(blocked).get("code").asText());
    }

    @Test
    void growingARoom_isLimited_butShrinkingIsAlwaysAllowed() throws Exception {
        NewOwner o = createOwner(sub("GROWTH", "customMaxBedsPerHostel", 4));
        long h = body(newHostel(o.user())).get("id").asLong();
        long floor = floorOf(o.ownerId(), h);
        long room = body(newRoom(o.user(), floor, "1", 3)).get("id").asLong();
        Map<String, Object> grow = Map.of("floorId", floor, "roomNumber", "1", "roomType", "DOUBLE", "totalBeds", 5,
                "monthlyRentPerBed", 5000, "securityDeposit", 1000);
        Map<String, Object> shrink = Map.of("floorId", floor, "roomNumber", "1", "roomType", "DOUBLE", "totalBeds", 2,
                "monthlyRentPerBed", 5000, "securityDeposit", 1000);

        assertEquals(403, send(put("/api/rooms/{id}", room), o.user(), grow).getResponse().getStatus());
        assertEquals(200, send(put("/api/rooms/{id}", room), o.user(), shrink).getResponse().getStatus());
    }

    @Test
    void concurrentBedCreation_neverExceedsThePerHostelLimit() throws Exception {
        NewOwner o = createOwner(sub("GROWTH", "customMaxBedsPerHostel", 10));
        long h = body(newHostel(o.user())).get("id").asLong();
        long floor = floorOf(o.ownerId(), h);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                go.await();
                return newRoom(o.user(), floor, "R" + n, 3).getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) {
                created++;
            }
        }
        pool.shutdown();

        assertEquals(3, created, "three rooms of 3 beds fit in a limit of 10");
        assertEquals(9, jdbc.queryForObject("""
                SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id WHERE r.hostel_id = ? AND b.deleted_at IS NULL""",
                Integer.class, h));
    }

    // ------------------------------------------------------------------ manager accounts

    private MvcResult newManager(AuthUser owner, long hostelId) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return send(post("/api/users"), owner, Map.of("fullName", "Mgr " + s, "email", "m-" + s + "@test.dev", "phone", "",
                "password", "Passw0rd1x", "roleCode", "MANAGER", "permissions", Set.of("HOSTEL_VIEW"), "hostelIds", Set.of(hostelId)));
    }

    @Test
    void managerLimit_countsEnabledAccountsOnce_andDisabledOnesDoNot() throws Exception {
        NewOwner o = createOwner(sub("BUSINESS", "customMaxManagers", 1, "customMaxHostels", 3));
        long h1 = body(newHostel(o.user())).get("id").asLong();
        long h2 = body(newHostel(o.user())).get("id").asLong();

        MvcResult first = newManager(o.user(), h1);
        assertEquals(201, first.getResponse().getStatus(), first.getResponse().getContentAsString());
        long managerId = body(first).get("id").asLong();
        MvcResult blocked = newManager(o.user(), h2);
        assertEquals(403, blocked.getResponse().getStatus());
        assertEquals("PLAN_LIMIT_REACHED", body(blocked).get("code").asText());

        // Reassigning the same account to more hostels does not use another slot
        Map<String, Object> reassign = Map.of("fullName", "Mgr", "phone", "", "roleCode", "MANAGER", "permissions", Set.of("HOSTEL_VIEW"),
                "hostelIds", Set.of(h1, h2), "enabled", true);
        assertEquals(200, send(put("/api/users/{id}", managerId), o.user(), reassign).getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE owner_id = ? AND enabled = TRUE AND id <> ?", Integer.class,
                o.ownerId(), o.user().userId()));

        // Disabling frees the slot; re-enabling beyond the limit is refused
        Map<String, Object> disable = new HashMap<>(reassign);
        disable.put("enabled", false);
        assertEquals(200, send(put("/api/users/{id}", managerId), o.user(), disable).getResponse().getStatus());
        MvcResult second = newManager(o.user(), h1);
        assertEquals(201, second.getResponse().getStatus());
        assertEquals(403, send(put("/api/users/{id}", managerId), o.user(), reassign).getResponse().getStatus(),
                "re-enabling the disabled manager would exceed the limit");
    }

    // ------------------------------------------------------------------ plan changes and grandfathering

    @Test
    void downgradingBelowCurrentUsage_keepsEverything_andOnlyBlocksGrowth() throws Exception {
        NewOwner o = createOwner(sub("BUSINESS"));
        long h1 = body(newHostel(o.user())).get("id").asLong();
        body(newHostel(o.user()));
        body(newHostel(o.user()));
        newRoom(o.user(), floorOf(o.ownerId(), h1), "1", 20);

        assertEquals(200, updateSubscription(o.ownerId(), updateBody(o.ownerId(), "STARTER", "ACTIVE", LocalDate.now(), null,
                LocalDate.now().plusMonths(1), null, null, null)).getResponse().getStatus());

        JsonNode s = subscriptionOf(o.ownerId());
        assertEquals(3, s.get("usage").get("hostels").asInt(), "no hostel is deleted");
        assertEquals(1, s.get("effectiveLimits").get("maxHostels").asInt());
        assertTrue(s.get("overLimit").toString().contains("hostels 3 / 1"), s.get("overLimit").toString());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ? AND deleted_at IS NULL", Integer.class, o.ownerId()));
        assertEquals(403, newHostel(o.user()).getResponse().getStatus());
        // A hostel with 20 beds is within Starter's 50-bed limit, so beds may still be added; only the hostel count is over
        assertEquals(201, newRoom(o.user(), floorOf(o.ownerId(), h1), "2", 1).getResponse().getStatus());
    }

    @Test
    void reducingAPlansLimits_appliesToOwnersWithoutOverrides_butKeepsTheirData() throws Exception {
        String code = "SHRINK" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        Map<String, Object> plan = new HashMap<>(Map.of("code", code, "name", "Shrinking", "monthlyPrice", 50, "maxHostels", 3,
                "maxBedsPerHostel", 50, "maxManagers", 2, "trialDays", 0, "graceDays", 7, "customPricing", false));
        long planId = body(send(post("/api/admin/plans"), superAdmin(), plan)).get("id").asLong();
        NewOwner plain = createOwner(Map.of("planId", planId));
        NewOwner custom = createOwner(Map.of("planId", planId, "customMaxHostels", 3));
        for (int i = 0; i < 2; i++) {
            newHostel(plain.user());
            newHostel(custom.user());
        }

        plan.put("maxHostels", 1);
        assertEquals(200, send(put("/api/admin/plans/{id}", planId), superAdmin(), plan).getResponse().getStatus());

        assertEquals(403, newHostel(plain.user()).getResponse().getStatus(), "plan limit applies at once");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ?", Integer.class, plain.ownerId()));
        assertEquals(201, newHostel(custom.user()).getResponse().getStatus(), "the owner-specific override still wins");
    }

    @Test
    void subscriptionChanges_areRecordedInHistoryAndAudit() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));

        MvcResult r = updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "ACTIVE", LocalDate.now(), null,
                LocalDate.now().plusMonths(2), 5, null, null));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        List<String> types = new ArrayList<>();
        body(r).get("events").forEach(e -> types.add(e.get("eventType").asText()));
        assertTrue(types.containsAll(List.of("CREATED", "PLAN_CHANGED", "LIMITS_CHANGED", "STATUS_CHANGED")), types.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE owner_id = ? AND action = 'SUBSCRIPTION_UPDATED'",
                Integer.class, o.ownerId()));
        JsonNode planChanged = null;
        for (JsonNode e : body(r).get("events")) {
            if ("PLAN_CHANGED".equals(e.get("eventType").asText())) {
                planChanged = e;
            }
        }
        assertEquals("STARTER -> GROWTH", planChanged.get("details").asText());
        assertEquals(t.owner().userId(), planChanged.get("actorUserId").asLong());
    }

    @Test
    void updateValidation_rejectsInconsistentDates_andUnknownPlans() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));
        LocalDate today = LocalDate.now();

        assertEquals(400, updateSubscription(o.ownerId(), updateBody(o.ownerId(), "STARTER", "TRIAL", today, null, null, null, null, null))
                .getResponse().getStatus(), "a trial needs an end date");
        assertEquals(400, updateSubscription(o.ownerId(), updateBody(o.ownerId(), "STARTER", "ACTIVE", today, null, today.minusDays(1), null, null, null))
                .getResponse().getStatus(), "expiry before start");
        Map<String, Object> badPlan = updateBody(o.ownerId(), "STARTER", "ACTIVE", today, null, null, null, null, null);
        badPlan.put("planId", 987_654_321L);
        assertEquals(400, updateSubscription(o.ownerId(), badPlan).getResponse().getStatus());
        assertEquals(400, updateSubscription(o.ownerId(), updateBody(o.ownerId(), "STARTER", "ACTIVE", today, null, null, 0, null, null))
                .getResponse().getStatus(), "limit of zero hostels");
        assertEquals(404, updateSubscription(987_654_321L, updateBody(0, "STARTER", "ACTIVE", today, null, null, null, null, null))
                .getResponse().getStatus());
    }

    // ------------------------------------------------------------------ expiry, grace and data preservation

    @Test
    void expiryStates_graceKeepsWorking_thenOnlyNewResourcesArePaused_andDataStaysAccessible() throws Exception {
        NewOwner o = createOwner(sub("GROWTH"));
        long hostel = body(newHostel(o.user())).get("id").asLong();
        long resident = resident(o.ownerId(), hostel, "Kept Resident");
        long invoice = invoice(new Tenant(o.ownerId(), o.user(), o.user(), o.user(), hostel, hostel, resident, resident),
                hostel, resident, "4000.00", LocalDate.now().plusDays(3));
        LocalDate today = LocalDate.now();

        // Period ended 3 days ago, grace is 7 days: still allowed
        updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "ACTIVE", today.minusMonths(1), null, today.minusDays(3), null, null, null));
        assertEquals("GRACE", subscriptionOf(o.ownerId()).get("accessState").asText());
        assertEquals(201, newHostel(o.user()).getResponse().getStatus());

        // Ended 10 days ago: restricted
        updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "ACTIVE", today.minusMonths(1), null, today.minusDays(10), null, null, null));
        assertEquals("RESTRICTED", subscriptionOf(o.ownerId()).get("accessState").asText());
        MvcResult paused = newHostel(o.user());
        assertEquals(403, paused.getResponse().getStatus());
        assertEquals("SUBSCRIPTION_RESTRICTED", body(paused).get("code").asText());
        assertEquals(403, newManager(o.user(), hostel).getResponse().getStatus());

        // Everything that exists is still there and usable: history, invoices, payments, receipts
        assertEquals(200, getAs(o.user(), "/api/hostels").getResponse().getStatus());
        assertEquals(200, getAs(o.user(), "/api/invoices/{id}", invoice).getResponse().getStatus());
        MvcResult cash = send(post("/api/payments"), o.user(), Map.of("residentId", resident, "amount", "1000.00",
                "paymentDate", today.toString(), "method", "CASH", "allocations", List.of(Map.of("invoiceId", invoice, "amount", "1000.00"))));
        assertEquals(201, cash.getResponse().getStatus(), cash.getResponse().getContentAsString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM residents WHERE owner_id = ?", Integer.class, o.ownerId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ? AND deleted_at IS NULL", Integer.class, o.ownerId()));
        assertEquals(200, getAs(o.user(), "/api/subscription").getResponse().getStatus(), "the owner can still see why");

        // Renewal restores creation
        updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "ACTIVE", today.minusMonths(1), null, today.plusMonths(1), null, null, null));
        assertEquals(201, newHostel(o.user()).getResponse().getStatus());
    }

    @Test
    void trialLifecycle_andSuspendedOrCancelledSubscriptions() throws Exception {
        NewOwner o = createOwner(sub("GROWTH"));
        LocalDate today = LocalDate.now();

        updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "TRIAL", today.minusDays(20), today.minusDays(6), null, null, null, null));
        assertEquals("GRACE", subscriptionOf(o.ownerId()).get("accessState").asText());
        updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", "TRIAL", today.minusDays(30), today.minusDays(16), null, null, null, null));
        assertEquals("RESTRICTED", subscriptionOf(o.ownerId()).get("accessState").asText());
        assertEquals(403, newHostel(o.user()).getResponse().getStatus());

        for (String status : List.of("SUSPENDED", "CANCELLED")) {
            updateSubscription(o.ownerId(), updateBody(o.ownerId(), "GROWTH", status, today, null, today.plusMonths(1), null, null, null));
            assertEquals("RESTRICTED", subscriptionOf(o.ownerId()).get("accessState").asText(), status);
            assertEquals(403, newHostel(o.user()).getResponse().getStatus(), status);
        }
        // Owner login itself is governed only by owner status (Super Admin suspend/reactivate), not by the subscription
        assertEquals(200, getAs(o.user(), "/api/hostels").getResponse().getStatus());
    }

    // ------------------------------------------------------------------ payments (manual, separate from status)

    @Test
    void recordedPayment_extendsThePeriod_andConvertsTheTrial() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));

        MvcResult r = send(post("/api/admin/owners/{id}/subscription/payments", o.ownerId()), superAdmin(), Map.of("amount", 199,
                "paidOn", LocalDate.now().toString(), "method", "UPI", "reference", "UTR123", "extendMonths", 1));

        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode s = body(r);
        assertEquals("ACTIVE", s.get("status").asText());
        assertEquals("PAID", s.get("billingStatus").asText());
        assertEquals(LocalDate.now().plusMonths(1).toString(), s.get("periodEndsOn").asText());
        assertEquals(1, s.get("payments").size());
        assertEquals("UTR123", s.get("payments").get(0).get("reference").asText());
    }

    @Test
    void recordingPayments_hasNoSideEffectUnlessRenewalIsExplicit_andRejectsBadInput() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));
        String endBefore = subscriptionOf(o.ownerId()).get("trialEndsOn").asText();

        MvcResult noExtension = send(post("/api/admin/owners/{id}/subscription/payments", o.ownerId()), superAdmin(), Map.of("amount", 50,
                "paidOn", LocalDate.now().toString(), "method", "CASH", "extendMonths", 0));

        assertEquals(201, noExtension.getResponse().getStatus());
        assertEquals("TRIAL", body(noExtension).get("status").asText(), "a payment without an extension does not renew");
        assertEquals(endBefore, body(noExtension).get("trialEndsOn").asText());
        assertEquals(400, send(post("/api/admin/owners/{id}/subscription/payments", o.ownerId()), superAdmin(), Map.of("amount", 0,
                "paidOn", LocalDate.now().toString(), "method", "CASH", "extendMonths", 1)).getResponse().getStatus());
        assertEquals(400, send(post("/api/admin/owners/{id}/subscription/payments", o.ownerId()), superAdmin(), Map.of("amount", 10,
                "paidOn", LocalDate.now().plusDays(2).toString(), "method", "CASH", "extendMonths", 1)).getResponse().getStatus(),
                "a future-dated payment is not a payment");
    }

    // ------------------------------------------------------------------ owner-facing usage and entitlements

    @Test
    void ownerSummary_showsUsageAgainstLimits_withWarningsNearTheLimit() throws Exception {
        NewOwner o = createOwner(sub("GROWTH", "customMaxBedsPerHostel", 10));
        long h = body(newHostel(o.user())).get("id").asLong();
        body(newHostel(o.user()));
        newRoom(o.user(), floorOf(o.ownerId(), h), "1", 9);

        JsonNode s = body(getAs(o.user(), "/api/subscription"));

        assertEquals("Growth", s.get("planName").asText());
        assertEquals(2, s.get("usage").get("hostels").asInt());
        assertEquals(3, s.get("limits").get("maxHostels").asInt());
        assertEquals(10, s.get("limits").get("maxBedsPerHostel").asInt());
        boolean bedsEntry = false;
        for (JsonNode hu : s.get("usage").get("hostelBeds")) {
            if (hu.get("hostelId").asLong() == h) {
                bedsEntry = hu.get("beds").asInt() == 9;
            }
        }
        assertTrue(bedsEntry);
        String warnings = s.get("warnings").toString();
        assertTrue(warnings.contains("9 / 10"), warnings);
        assertTrue(s.get("canAddResources").asBoolean());
        assertEquals(403, getAs(superAdmin(), "/api/subscription").getResponse().getStatus());
    }

    @Test
    void managerSummary_listsOnlyTheHostelsTheyCanSee() throws Exception {
        NewOwner o = createOwner(sub("GROWTH"));
        long h1 = body(newHostel(o.user())).get("id").asLong();
        long h2 = body(newHostel(o.user())).get("id").asLong();
        AuthUser mgr = new AuthUser(jdbc.queryForObject("SELECT id FROM users WHERE owner_id = ? LIMIT 1", Long.class, o.ownerId()),
                o.ownerId(), "m@test.dev", "Mgr", Set.of("MANAGER"), Set.of("HOSTEL_VIEW"));
        AuthUser realManager = makeUser(new Tenant(o.ownerId(), o.user(), mgr, mgr, h1, h2, 0, 0), "MANAGER", Set.of("HOSTEL_VIEW"), h1);

        JsonNode s = body(getAs(realManager, "/api/subscription"));

        assertEquals(1, s.get("usage").get("hostelBeds").size());
        assertEquals(h1, s.get("usage").get("hostelBeds").get(0).get("hostelId").asLong());
    }

    @Test
    void featureEntitlements_areReadFromThePlan_notHardcoded() throws Exception {
        NewOwner o = createOwner(sub("STARTER"));
        long hostel = body(newHostel(o.user())).get("id").asLong();
        long resident = resident(o.ownerId(), hostel, "Pays Online");
        long inv = invoice(new Tenant(o.ownerId(), o.user(), o.user(), o.user(), hostel, hostel, resident, resident),
                hostel, resident, "3000.00", LocalDate.now().plusDays(3));
        assertEquals(201, startOrder(o.user(), inv, "1000.00").getResponse().getStatus(), "included by default");

        // Super Admin turns the feature off for the plan: it stops immediately, without a code change
        JsonNode starter = null;
        for (JsonNode p : body(getAs(superAdmin(), "/api/admin/plans"))) {
            if ("STARTER".equals(p.get("code").asText())) {
                starter = p;
            }
        }
        Map<String, Object> edit = new HashMap<>();
        edit.put("name", starter.get("name").asText());
        edit.put("monthlyPrice", starter.get("monthlyPrice").decimalValue());
        edit.put("maxHostels", starter.get("maxHostels").asInt());
        edit.put("maxBedsPerHostel", starter.get("maxBedsPerHostel").asInt());
        edit.put("maxManagers", starter.get("maxManagers").asInt());
        edit.put("trialDays", 14);
        edit.put("graceDays", 7);
        edit.put("customPricing", false);
        Map<String, Boolean> ent = new HashMap<>();
        starter.get("entitlements").fields().forEachRemaining(e -> ent.put(e.getKey(), e.getValue().asBoolean()));
        try {
            ent.put("ONLINE_PAYMENTS", false);
            edit.put("entitlements", ent);
            assertEquals(200, send(put("/api/admin/plans/{id}", planId("STARTER")), superAdmin(), edit).getResponse().getStatus());

            MvcResult denied = startOrder(o.user(), inv, "1000.00");
            assertEquals(403, denied.getResponse().getStatus());
            assertEquals("FEATURE_NOT_IN_PLAN", body(denied).get("code").asText());
            assertEquals(403, send(post("/api/online-payments/invoices/{id}/links", inv), o.user(), null).getResponse().getStatus());
        } finally {
            ent.put("ONLINE_PAYMENTS", true);
            edit.put("entitlements", ent);
            send(put("/api/admin/plans/{id}", planId("STARTER")), superAdmin(), edit);
        }
        assertEquals(201, startOrder(o.user(), inv, "1000.00").getResponse().getStatus());
    }

    // ------------------------------------------------------------------ owners that predate subscriptions

    @Test
    void existingOwnersWithoutASubscription_areGrandfathered_withLimitsNeverBelowUsage() throws Exception {
        long ownerId = insert("INSERT INTO owners (name, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "Legacy", "legacy-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev");
        for (int i = 0; i < 60; i++) {
            hostel(ownerId, "Legacy hostel " + i);
        }
        user(ownerId, "legacy-user-" + UUID.randomUUID().toString().substring(0, 6));

        backfill.run(null);
        backfill.run(null); // idempotent

        JsonNode s = subscriptionOf(ownerId);
        assertEquals("ENTERPRISE", s.get("plan").get("code").asText());
        assertEquals("ACTIVE", s.get("status").asText());
        assertTrue(s.get("periodEndsOn").isNull(), "open-ended: no sudden expiry for existing customers");
        assertEquals(60, s.get("usage").get("hostels").asInt());
        assertTrue(s.get("effectiveLimits").get("maxHostels").asInt() >= 60, "limit is raised to current usage");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM owner_subscriptions WHERE owner_id = ?", Integer.class, ownerId));
        assertEquals("GRANDFATHERED", s.get("events").get(0).get("eventType").asText());
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ?", Integer.class, ownerId), "nothing deleted");
    }

    @Test
    void anOwnerWithoutASubscription_cannotCreateBillableResources() throws Exception {
        long ownerId = insert("INSERT INTO owners (name, email, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                "Orphan", "orphan-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev");
        long userId = user(ownerId, "orphan-owner-" + UUID.randomUUID().toString().substring(0, 6));
        AuthUser owner = new AuthUser(userId, ownerId, "o@test.dev", "Orphan", Set.of("OWNER"), Permissions.ALL);

        MvcResult r = newHostel(owner);

        assertEquals(403, r.getResponse().getStatus());
        assertEquals("SUBSCRIPTION_MISSING", body(r).get("code").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM hostels WHERE owner_id = ?", Integer.class, ownerId));
    }
}
