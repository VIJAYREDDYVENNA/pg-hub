package com.hostelmanagement.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Regression tests for the Phase 1 security fixes (real controllers, security chain and MySQL). */
class Phase1SecurityTest extends PaymentIntegrationTestBase {

    // ------------------------------------------------------------------ user management escalation

    private Map<String, Object> userUpdate(Set<String> perms, Long... hostels) {
        return Map.of("fullName", "Changed Name", "phone", "", "roleCode", "MANAGER", "permissions", perms,
                "hostelIds", Set.of(hostels), "enabled", true);
    }

    @Test
    void delegatedManager_cannotResetAPeersPassword_butOwnerCan() throws Exception {
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("STAFF_MANAGE", "HOSTEL_VIEW", "PAYMENT_VIEW"), t.hostelId());
        AuthUser peer = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW"), t.hostelId());
        String before = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, peer.userId());

        MvcResult denied = send(post("/api/users/{id}/reset-password", peer.userId()), mgr, Map.of("newPassword", "Passw0rd1x"));

        assertEquals(403, denied.getResponse().getStatus());
        assertEquals(before, jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, peer.userId()));
        MvcResult allowed = send(post("/api/users/{id}/reset-password", peer.userId()), t.owner(), Map.of("newPassword", "Passw0rd1x"));
        assertTrue(allowed.getResponse().getStatus() < 300, allowed.getResponse().getContentAsString());
        assertFalse(before.equals(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, peer.userId())));
    }

    @Test
    void delegatedManager_cannotEditAUserWithWiderHostelsOrPermissions() throws Exception {
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("STAFF_MANAGE", "HOSTEL_VIEW", "PAYMENT_VIEW"), t.hostelId());
        AuthUser moreHostels = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW"), t.hostelId(), t.hostel2Id());
        AuthUser morePerms = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW", "EXPENSE_MANAGE"), t.hostelId());

        // Shares hostel 1, but also has hostel 2 that the caller cannot see: editing would strip it
        MvcResult stripping = send(put("/api/users/{id}", moreHostels.userId()), mgr,
                userUpdate(Set.of("PAYMENT_VIEW"), t.hostelId()));
        MvcResult widerPerms = send(put("/api/users/{id}", morePerms.userId()), mgr,
                userUpdate(Set.of("PAYMENT_VIEW"), t.hostelId()));

        assertEquals(403, stripping.getResponse().getStatus());
        assertEquals(403, widerPerms.getResponse().getStatus());
        assertEquals(Set.of(t.hostelId(), t.hostel2Id()), accessOf(moreHostels.userId()), "hostel access must be untouched");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_permissions WHERE user_id = ?", Integer.class, morePerms.userId()));
    }

    @Test
    void delegatedManager_canStillManageUsersWithinTheirOwnScope() throws Exception {
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("STAFF_MANAGE", "HOSTEL_VIEW", "PAYMENT_VIEW", "RESIDENT_VIEW"), t.hostelId());
        AuthUser junior = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW"), t.hostelId());

        MvcResult r = send(put("/api/users/{id}", junior.userId()), mgr, userUpdate(Set.of("RESIDENT_VIEW"), t.hostelId()));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("Changed Name", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, junior.userId()));
    }

    @Test
    void ownerCanStillEditAnyone() throws Exception {
        AuthUser multi = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW", "EXPENSE_MANAGE"), t.hostelId(), t.hostel2Id());

        MvcResult r = send(put("/api/users/{id}", multi.userId()), t.owner(), userUpdate(Set.of("PAYMENT_VIEW"), t.hostelId()));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(Set.of(t.hostelId()), accessOf(multi.userId()));
    }

    @Test
    void hostelManagerAssignment_needsUserManagementPermission() throws Exception {
        AuthUser hostelAdmin = makeUser(t, "MANAGER", Set.of("HOSTEL_VIEW", "HOSTEL_MANAGE"), t.hostelId());
        AuthUser other = makeUser(t, "MANAGER", Set.of("PAYMENT_VIEW"), t.hostel2Id());
        Map<String, Object> body = new java.util.HashMap<>(Map.of("name", "Hostel One Renamed", "hostelType", "BOYS",
                "address", "1 Test Road", "city", "Pune", "state", "MH", "pinCode", "411001", "status", "ACTIVE"));
        body.put("managerUserId", other.userId());

        MvcResult denied = send(put("/api/hostels/{id}", t.hostelId()), hostelAdmin, body);

        assertEquals(403, denied.getResponse().getStatus(), denied.getResponse().getContentAsString());
        assertFalse(accessOf(other.userId()).contains(t.hostelId()), "no access may be granted through the hostel form");
        body.remove("managerUserId");
        assertEquals(200, send(put("/api/hostels/{id}", t.hostelId()), hostelAdmin, body).getResponse().getStatus());
    }

    // ------------------------------------------------------------------ resident PII and balances

    private record Occupied(long roomId, long residentId, String name) {
    }

    private Occupied occupiedRoom(LocalDate expectedCheckout) {
        long b = building(t.hostelId());
        long f = floor(b);
        long room = room(t.hostelId(), f, "101");
        long bed = bed(room, "A");
        admit(t.residentId(), t.hostelId(), bed, expectedCheckout);
        invoice(t, t.hostelId(), t.residentId(), "5000.00", LocalDate.now().minusDays(3));
        String name = jdbc.queryForObject("SELECT full_name FROM residents WHERE id = ?", String.class, t.residentId());
        return new Occupied(room, t.residentId(), name);
    }

    private JsonNode firstResident(JsonNode room) {
        return room.get("beds").get(0).get("resident");
    }

    @Test
    void roomDetails_hideIdentityAndBalances_fromHostelViewOnly() throws Exception {
        Occupied o = occupiedRoom(null);
        AuthUser hostelOnly = makeUser(t, "STAFF", Set.of("HOSTEL_VIEW"), t.hostelId());
        AuthUser privileged = makeUser(t, "MANAGER", Set.of("HOSTEL_VIEW", "RESIDENT_VIEW", "PAYMENT_VIEW"), t.hostelId());

        JsonNode limited = firstResident(body(getAs(hostelOnly, "/api/rooms/{id}", o.roomId())));
        JsonNode full = firstResident(body(getAs(privileged, "/api/rooms/{id}", o.roomId())));

        assertEquals("Resident", limited.get("name").asText());
        assertTrue(limited.get("mobile").isNull());
        assertTrue(limited.get("outstandingAmount").isNull());
        assertTrue(limited.get("overdueAmount").isNull());
        assertTrue(limited.get("paymentStatus").isNull());
        assertTrue(limited.get("monthlyRent").isNull());
        assertEquals(o.name(), full.get("name").asText());
        assertEquals("9876500000", full.get("mobile").asText());
        assertEquals("5000.00", full.get("outstandingAmount").decimalValue().setScale(2).toPlainString());
    }

    @Test
    void identityAndMoneyPermissionsAreIndependent() throws Exception {
        Occupied o = occupiedRoom(null);
        AuthUser identityOnly = makeUser(t, "MANAGER", Set.of("HOSTEL_VIEW", "RESIDENT_VIEW"), t.hostelId());
        AuthUser moneyOnly = makeUser(t, "MANAGER", Set.of("HOSTEL_VIEW", "PAYMENT_VIEW"), t.hostelId());

        JsonNode a = firstResident(body(getAs(identityOnly, "/api/rooms/{id}", o.roomId())));
        JsonNode b = firstResident(body(getAs(moneyOnly, "/api/rooms/{id}", o.roomId())));

        assertEquals(o.name(), a.get("name").asText());
        assertTrue(a.get("outstandingAmount").isNull());
        assertEquals("Resident", b.get("name").asText());
        assertEquals("5000.00", b.get("outstandingAmount").decimalValue().setScale(2).toPlainString());
    }

    @Test
    void buildingViewAndRoomList_alsoRedact_andNameSearchDoesNotLeak() throws Exception {
        Occupied o = occupiedRoom(null);
        AuthUser hostelOnly = makeUser(t, "STAFF", Set.of("HOSTEL_VIEW"), t.hostelId());

        JsonNode view = body(getAs(hostelOnly, "/api/hostels/{id}/building-view", t.hostelId()));
        JsonNode list = body(getAs(hostelOnly, "/api/rooms?hostelId=" + t.hostelId() + "&q=" + o.name().split(" ")[0]));
        String listJson = getAs(hostelOnly, "/api/rooms?hostelId=" + t.hostelId()).getResponse().getContentAsString();

        assertEquals("Resident", firstResident(view.get("rooms").get(0)).get("name").asText());
        assertFalse(listJson.contains(o.name()), "room list must not contain the resident's name");
        assertFalse(listJson.contains("9876500000"), "room list must not contain the resident's mobile");
        assertEquals(0, list.get("content") == null ? list.size() : list.get("content").size(),
                "searching by resident name must find nothing for a HOSTEL_VIEW-only user");
    }

    @Test
    void vacancyForecast_hidesWhoIsLeaving_fromHostelViewOnly() throws Exception {
        Occupied o = occupiedRoom(LocalDate.now().plusDays(5));
        AuthUser hostelOnly = makeUser(t, "STAFF", Set.of("HOSTEL_VIEW"), t.hostelId());
        AuthUser privileged = makeUser(t, "MANAGER", Set.of("HOSTEL_VIEW", "RESIDENT_VIEW"), t.hostelId());

        JsonNode limited = body(getAs(hostelOnly, "/api/dashboard/vacancies?days=30&hostelId=" + t.hostelId())).get("items").get(0);
        JsonNode full = body(getAs(privileged, "/api/dashboard/vacancies?days=30&hostelId=" + t.hostelId())).get("items").get(0);

        assertEquals("Resident", limited.get("residentName").asText());
        assertTrue(limited.get("residentMobile").isNull());
        assertEquals(o.name(), full.get("residentName").asText());
        assertEquals("9876500000", full.get("residentMobile").asText());
    }

    // ------------------------------------------------------------------ file downloads

    private long storedFile(String entityType, Long hostelId, long ownerId, long entityId) throws Exception {
        String stored = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path dir = Path.of("target/test-uploads").toAbsolutePath();
        Files.createDirectories(dir);
        Files.write(dir.resolve(stored), "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
        return insert("""
                INSERT INTO file_attachments (owner_id, hostel_id, entity_type, entity_id, category, original_name,
                    stored_name, content_type, size_bytes, created_at) VALUES (?, ?, ?, ?, 'ID_PROOF', 'id.pdf', ?,
                    'application/pdf', 13, ?)""", ownerId, hostelId, entityType, entityId, stored, new Timestamp(System.currentTimeMillis()));
    }

    @Test
    void residentDocuments_needResidentViewPermission() throws Exception {
        long file = storedFile("RESIDENT", t.hostelId(), t.ownerId(), t.residentId());
        AuthUser maintenanceOnly = makeUser(t, "STAFF", Set.of("MAINTENANCE_MANAGE", "HOSTEL_VIEW"), t.hostelId());
        AuthUser residentViewer = makeUser(t, "MANAGER", Set.of("RESIDENT_VIEW"), t.hostelId());
        AuthUser otherHostel = makeUser(t, "MANAGER", Set.of("RESIDENT_VIEW"), t.hostel2Id());

        assertEquals(403, getAs(maintenanceOnly, "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(200, getAs(residentViewer, "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(404, getAs(otherHostel, "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(404, getAs(newTenant().owner(), "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(200, getAs(t.owner(), "/api/files/{id}", file).getResponse().getStatus());
    }

    @Test
    void maintenanceImages_followTheMaintenancePermission() throws Exception {
        long file = storedFile("MAINTENANCE", t.hostelId(), t.ownerId(), 1);
        AuthUser maintenance = makeUser(t, "STAFF", Set.of("MAINTENANCE_MANAGE"), t.hostelId());
        AuthUser residentViewer = makeUser(t, "MANAGER", Set.of("RESIDENT_VIEW"), t.hostelId());

        assertEquals(200, getAs(maintenance, "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(403, getAs(residentViewer, "/api/files/{id}", file).getResponse().getStatus());
    }

    @Test
    void filesOfUnknownKind_areOwnerOnly() throws Exception {
        long file = storedFile("SOMETHING_ELSE", null, t.ownerId(), 1);
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("RESIDENT_VIEW", "HOSTEL_VIEW"), t.hostelId());

        assertEquals(403, getAs(mgr, "/api/files/{id}", file).getResponse().getStatus());
        assertEquals(200, getAs(t.owner(), "/api/files/{id}", file).getResponse().getStatus());
    }

    // ------------------------------------------------------------------ Super Admin activity feed

    @Test
    void superAdminFeed_hidesResidentAndPaymentDetails_butKeepsAccountEvents() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Timestamp now = new Timestamp(System.currentTimeMillis() + 60_000);
        insert("INSERT INTO audit_logs (owner_id, user_id, action, entity_type, entity_id, details, created_at) VALUES (?, ?, 'PAYMENT_RECORDED', 'PAYMENT', 1, ?, ?)",
                t.ownerId(), t.owner().userId(), "RCP-1 500.00 CASH from SecretResident" + marker, now);
        insert("INSERT INTO audit_logs (owner_id, user_id, action, entity_type, entity_id, details, created_at) VALUES (?, ?, 'RESIDENT_ADMITTED', 'ADMISSION', 1, ?, ?)",
                t.ownerId(), t.owner().userId(), "SecretAdmitted" + marker + " to room 5", now);
        insert("INSERT INTO audit_logs (owner_id, user_id, action, entity_type, entity_id, details, created_at) VALUES (?, ?, 'OWNER_STATUS_CHANGED', 'OWNER', 1, ?, ?)",
                t.ownerId(), t.owner().userId(), "VisibleStatus" + marker, now);
        AuthUser superAdmin = new AuthUser(t.owner().userId(), null, "sa@test.dev", "Super", Set.of("SUPER_ADMIN"), Set.of());

        String feed = getAs(superAdmin, "/api/admin/activity?size=100").getResponse().getContentAsString();
        String ownerDetail = getAs(superAdmin, "/api/admin/owners/{id}", t.ownerId()).getResponse().getContentAsString();

        assertFalse(feed.contains("SecretResident" + marker), "payment details must not reach the platform feed");
        assertFalse(feed.contains("SecretAdmitted" + marker), "resident names must not reach the platform feed");
        assertTrue(feed.contains("VisibleStatus" + marker), "account events stay visible");
        assertTrue(feed.contains("PAYMENT_RECORDED"), "the fact that an action happened is still listed");
        assertFalse(ownerDetail.contains("SecretResident" + marker));
        assertFalse(ownerDetail.contains("SecretAdmitted" + marker));
    }

    @Test
    void tenantUsers_cannotReachPlatformAdminEndpoints() throws Exception {
        assertEquals(403, getAs(t.owner(), "/api/admin/activity").getResponse().getStatus());
        assertEquals(403, getAs(t.manager(), "/api/admin/owners").getResponse().getStatus());
    }

    // ------------------------------------------------------------------ cross-hostel links and owner-wide records

    @Test
    void booking_cannotAttachAnEnquiryFromAHostelTheManagerCannotAccess() throws Exception {
        long b = building(t.hostelId());
        long bed = bed(room(t.hostelId(), floor(b), "201"), "A");
        long foreignEnquiry = insert("""
                INSERT INTO enquiries (owner_id, hostel_id, full_name, mobile, created_at, updated_at)
                VALUES (?, ?, 'Other Hostel Lead', '9000000001', ?, ?)""", t.ownerId(), t.hostel2Id(),
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        long ownEnquiry = insert("""
                INSERT INTO enquiries (owner_id, hostel_id, full_name, mobile, created_at, updated_at)
                VALUES (?, ?, 'Own Hostel Lead', '9000000002', ?, ?)""", t.ownerId(), t.hostelId(),
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("ENQUIRY_MANAGE", "HOSTEL_VIEW"), t.hostelId());
        Map<String, Object> req = new java.util.HashMap<>(Map.of("bedId", bed, "guestName", "Guest", "guestMobile", "9876543210",
                "expectedAdmissionDate", LocalDate.now().plusDays(2).toString(), "holdUntil", LocalDate.now().plusDays(3).toString()));

        req.put("enquiryId", foreignEnquiry);
        MvcResult denied = send(post("/api/bookings"), mgr, req);
        req.put("enquiryId", ownEnquiry);
        MvcResult allowed = send(post("/api/bookings"), mgr, req);

        assertEquals(404, denied.getResponse().getStatus(), denied.getResponse().getContentAsString());
        assertEquals("NEW", jdbc.queryForObject("SELECT status FROM enquiries WHERE id = ?", String.class, foreignEnquiry));
        assertEquals(201, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
    }

    @Test
    void ownerWideStaff_areProtectedFromDelegatedManagers() throws Exception {
        long staff = insert("""
                INSERT INTO staff (owner_id, hostel_id, full_name, mobile, designation, monthly_salary, created_at, updated_at)
                VALUES (?, NULL, 'Head Cook', '9000000003', 'Cook', 25000.00, ?, ?)""", t.ownerId(),
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        AuthUser mgr = makeUser(t, "MANAGER", Set.of("STAFF_MANAGE", "HOSTEL_VIEW"), t.hostelId());
        Map<String, Object> update = Map.of("hostelId", t.hostelId(), "fullName", "Head Cook", "mobile", "9000000003",
                "designation", "Cook", "monthlySalary", 1);

        MvcResult denied = send(put("/api/staff/{id}", staff), mgr, update);
        JsonNode managerList = body(getAs(mgr, "/api/staff"));
        JsonNode ownerList = body(getAs(t.owner(), "/api/staff"));

        assertEquals(403, denied.getResponse().getStatus());
        assertNull(jdbc.queryForObject("SELECT hostel_id FROM staff WHERE id = ?", Long.class, staff), "must stay owner-wide");
        assertTrue(find(managerList, staff).get("monthlySalary").isNull(), "salary hidden from delegated manager");
        assertEquals("25000.00", find(ownerList, staff).get("monthlySalary").decimalValue().setScale(2).toPlainString());
    }

    private static JsonNode find(JsonNode list, long id) {
        for (JsonNode n : list) {
            if (n.get("id").asLong() == id) {
                return n;
            }
        }
        throw new AssertionError("staff " + id + " not in " + list);
    }
}
