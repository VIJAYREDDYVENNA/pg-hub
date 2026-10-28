package com.hostelmanagement.resident;

import com.hostelmanagement.admission.AdmissionQueries;
import com.hostelmanagement.admission.dto.AdmissionDtos.AdmissionDto;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.files.FileAttachment;
import com.hostelmanagement.common.files.FileStorageService;
import com.hostelmanagement.common.files.FileStorageService.FileDto;
import com.hostelmanagement.hostel.HostelService;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.payments.PaymentQueries;
import com.hostelmanagement.resident.dto.ResidentDtos.Balance;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentProfile;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentRequest;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentSummary;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResidentService {

    private static final Map<String, String> SORTS = Map.of(
            "name", "res.full_name", "createdAt", "res.created_at", "admissionDate", "a.admission_date",
            "checkoutDate", "a.expected_checkout_date", "room", "r.room_number");

    private final ResidentRepository repository;
    private final HostelAccessService hostelAccess;
    private final HostelService hostelService;
    private final AdmissionQueries admissionQueries;
    private final PaymentQueries paymentQueries;
    private final OccupancyQueries occupancyQueries;
    private final FileStorageService fileStorage;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppClock clock;

    public Resident getEntity(Long id) {
        Resident r = repository.findById(id).orElseThrow(() -> ApiException.notFound("Resident"));
        if (!r.getOwnerId().equals(CurrentUser.get().ownerId())) {
            throw ApiException.notFound("Resident");
        }
        hostelAccess.checkEntity(r.getHostelId(), "Resident");
        return r;
    }

    @Transactional(readOnly = true)
    public PageResponse<ResidentSummary> list(Long hostelId, String status, String q, int page, int size, String sort) {
        List<Long> scope = hostelAccess.scope(hostelId);
        StringBuilder where = new StringBuilder(" WHERE res.hostel_id IN (:ids)");
        MapSqlParameterSource p = new MapSqlParameterSource("ids", scope);
        if (status != null && !status.isBlank() && !"ALL".equals(status)) {
            if ("VACATING_SOON".equals(status)) {
                where.append(" AND a.id IS NOT NULL AND a.expected_checkout_date <= :soon");
                p.addValue("soon", Date.valueOf(clock.today().plusDays(30)));
            } else {
                where.append(" AND res.status = :status");
                p.addValue("status", status);
            }
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (res.full_name LIKE :q OR res.mobile LIKE :q OR res.email LIKE :q OR r.room_number LIKE :q)");
            p.addValue("q", "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%");
            p.addValue("qExact", q.trim());
        }
        String from = """
                 FROM residents res JOIN hostels h ON h.id = res.hostel_id
                 LEFT JOIN admissions a ON a.resident_id = res.id AND a.status = 'ACTIVE'
                 LEFT JOIN bed_allocations ba ON ba.admission_id = a.id AND ba.end_date IS NULL
                 LEFT JOIN beds b ON b.id = ba.bed_id
                 LEFT JOIN rooms r ON r.id = b.room_id
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, p, Long.class);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        String order = " ORDER BY " + resolveSort(sort) + ", res.id DESC LIMIT :limit OFFSET :offset";
        p.addValue("limit", safeSize).addValue("offset", safePage * safeSize);
        List<Object[]> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        jdbc.query("""
                SELECT res.id, res.full_name, res.mobile, res.email, res.hostel_id, h.name hostel_name, res.status,
                       res.photo_file_id, res.whatsapp_opt_in, a.id admission_id, a.admission_date,
                       a.expected_checkout_date, r.room_number, b.bed_label, r.id room_id""" + from + where + order, p, rs -> {
            Long id = rs.getLong("id");
            ids.add(id);
            long photo = rs.getLong("photo_file_id");
            boolean noPhoto = rs.wasNull();
            long admissionId = rs.getLong("admission_id");
            boolean noAdmission = rs.wasNull();
            long roomId = rs.getLong("room_id");
            boolean noRoom = rs.wasNull();
            Date ad = rs.getDate("admission_date");
            Date cd = rs.getDate("expected_checkout_date");
            rows.add(new Object[]{id, rs.getString("full_name"), rs.getString("mobile"), rs.getString("email"),
                    rs.getLong("hostel_id"), rs.getString("hostel_name"), rs.getString("status"),
                    noAdmission ? null : admissionId, ad == null ? null : ad.toLocalDate(),
                    cd == null ? null : cd.toLocalDate(), rs.getString("room_number"), rs.getString("bed_label"),
                    noRoom ? null : roomId, noPhoto ? null : "/api/files/" + photo, rs.getBoolean("whatsapp_opt_in")});
        });
        Map<Long, BigDecimal[]> balances = occupancyQueries.balancesByResident(ids, clock.today());
        List<ResidentSummary> content = rows.stream().map(o -> {
            BigDecimal[] bal = balances.getOrDefault((Long) o[0], new BigDecimal[]{Money.ZERO, Money.ZERO});
            return new ResidentSummary((Long) o[0], (String) o[1], (String) o[2], (String) o[3], (Long) o[4],
                    (String) o[5], (String) o[6], (Long) o[7], (java.time.LocalDate) o[8], (java.time.LocalDate) o[9],
                    (String) o[10], (String) o[11], (Long) o[12], (String) o[13], (Boolean) o[14],
                    Money.of(bal[0]), Money.of(bal[1]));
        }).toList();
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, safePage, safeSize, t, (int) Math.ceil(t / (double) safeSize));
    }

    private static String resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "res.full_name ASC";
        }
        String[] parts = sort.split(",");
        String col = SORTS.get(parts[0].trim());
        if (col == null) {
            return "res.full_name ASC";
        }
        return col + (parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? " DESC" : " ASC");
    }

    @Transactional(readOnly = true)
    public ResidentProfile profile(Long id) {
        Resident r = getEntity(id);
        List<AdmissionDto> history = admissionQueries.forResident(id);
        AdmissionDto current = history.stream().filter(a -> "ACTIVE".equals(a.status())).findFirst().orElse(null);
        BigDecimal[] bal = occupancyQueries.balancesByResident(List.of(id), clock.today())
                .getOrDefault(id, new BigDecimal[]{Money.ZERO, Money.ZERO});
        Balance balance = new Balance(Money.of(bal[0]), Money.of(bal[1]), paymentQueries.residentCredit(id),
                paymentQueries.depositHeld(id));
        String hostelName = hostelService.getEntity(r.getHostelId()).getName();
        List<FileDto> docs = fileStorage.list("RESIDENT", id);
        return new ResidentProfile(r.getId(), r.getHostelId(), hostelName, r.getFullName(),
                r.getPhotoFileId() == null ? null : "/api/files/" + r.getPhotoFileId(), r.getMobile(),
                r.getAlternateMobile(), r.getEmail(), r.getDateOfBirth(), r.getGender(), r.getAddress(),
                r.getEmergencyContactName(), r.getEmergencyContactPhone(), r.getIdDocumentType(),
                r.getIdDocumentNumber(), r.getGuardianName(), r.getGuardianPhone(), r.getGuardianRelation(),
                r.getOccupation(), r.getStatus(), r.isWhatsappOptIn(), r.getWhatsappOptInAt(), r.getNotes(),
                r.getCreatedAt(), current, history, balance, docs);
    }

    @Transactional
    public Resident create(ResidentRequest req) {
        hostelAccess.checkHostel(req.hostelId());
        Resident r = new Resident();
        r.setOwnerId(CurrentUser.get().ownerId());
        r.setHostelId(req.hostelId());
        apply(r, req);
        repository.save(r);
        auditService.log("RESIDENT_CREATED", "RESIDENT", r.getId(), r.getFullName());
        return r;
    }

    @Transactional
    public Resident update(Long id, ResidentRequest req) {
        Resident r = getEntity(id);
        if (!r.getHostelId().equals(req.hostelId())) {
            if ("ACTIVE".equals(r.getStatus())) {
                throw ApiException.badRequest("Use a bed transfer to move an admitted resident to another hostel");
            }
            hostelAccess.checkHostel(req.hostelId());
            r.setHostelId(req.hostelId());
        }
        apply(r, req);
        auditService.log("RESIDENT_UPDATED", "RESIDENT", id, r.getFullName());
        return r;
    }

    @Transactional
    public void setConsent(Long id, boolean optIn) {
        Resident r = getEntity(id);
        setConsentInternal(r, optIn);
        auditService.log(optIn ? "WHATSAPP_OPT_IN" : "WHATSAPP_OPT_OUT", "RESIDENT", id, null);
    }

    @Transactional
    public void deactivate(Long id) {
        Resident r = getEntity(id);
        if ("ACTIVE".equals(r.getStatus())) {
            throw ApiException.conflict("Check the resident out before deactivating the record");
        }
        r.setStatus("INACTIVE");
        auditService.log("RESIDENT_DEACTIVATED", "RESIDENT", id, r.getFullName());
    }

    @Transactional
    public Resident uploadPhoto(Long id, MultipartFile file) {
        Resident r = getEntity(id);
        FileAttachment f = fileStorage.store(file, "RESIDENT", id, r.getHostelId(), "PHOTO", true);
        r.setPhotoFileId(f.getId());
        return r;
    }

    @Transactional
    public FileDto uploadDocument(Long id, MultipartFile file, String category) {
        Resident r = getEntity(id);
        String cat = category == null || category.isBlank() ? "DOCUMENT" : category.trim().toUpperCase();
        if (cat.length() > 40) {
            cat = cat.substring(0, 40);
        }
        FileAttachment f = fileStorage.store(file, "RESIDENT", id, r.getHostelId(), cat, false);
        auditService.log("RESIDENT_DOCUMENT_UPLOADED", "RESIDENT", id, f.getOriginalName());
        return FileDto.from(f);
    }

    private void apply(Resident r, ResidentRequest req) {
        r.setFullName(req.fullName().trim());
        r.setMobile(req.mobile().trim());
        r.setAlternateMobile(blank(req.alternateMobile()));
        r.setEmail(blank(req.email()));
        r.setDateOfBirth(req.dateOfBirth());
        r.setGender(blank(req.gender()));
        r.setAddress(blank(req.address()));
        r.setEmergencyContactName(blank(req.emergencyContactName()));
        r.setEmergencyContactPhone(blank(req.emergencyContactPhone()));
        r.setIdDocumentType(blank(req.idDocumentType()));
        r.setIdDocumentNumber(blank(req.idDocumentNumber()));
        r.setGuardianName(blank(req.guardianName()));
        r.setGuardianPhone(blank(req.guardianPhone()));
        r.setGuardianRelation(blank(req.guardianRelation()));
        r.setOccupation(blank(req.occupation()));
        r.setNotes(blank(req.notes()));
        setConsentInternal(r, req.whatsappOptIn());
    }

    private void setConsentInternal(Resident r, boolean optIn) {
        if (optIn && !r.isWhatsappOptIn()) {
            r.setWhatsappOptInAt(Instant.now());
        }
        if (!optIn) {
            r.setWhatsappOptInAt(null);
        }
        r.setWhatsappOptIn(optIn);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
