package com.hostelmanagement.enquiry;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.events.DomainEvents;
import com.hostelmanagement.resident.Resident;
import com.hostelmanagement.resident.ResidentService;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentRequest;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnquiryService {

    private final EnquiryRepository repository;
    private final ResidentService residentService;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public record EnquiryRequest(Long hostelId,
                                 @NotBlank @Size(max = 150) String fullName,
                                 @NotBlank @Pattern(regexp = "^[+]?[0-9 ]{10,15}$", message = "Enter a valid mobile number") String mobile,
                                 @Email @Size(max = 150) String email,
                                 @Size(max = 20) String gender,
                                 @Size(max = 30) String preferredRoomType,
                                 LocalDate expectedAdmissionDate,
                                 @DecimalMin("0.00") BigDecimal budget,
                                 @Size(max = 40) String source,
                                 @Pattern(regexp = "NEW|CONTACTED|FOLLOW_UP|BOOKED|CONVERTED|LOST") String status,
                                 LocalDate nextFollowUpDate,
                                 @Size(max = 1000) String notes,
                                 boolean whatsappOptIn) {
    }

    public record EnquiryDto(Long id, Long hostelId, String fullName, String mobile, String email, String gender,
                             String preferredRoomType, LocalDate expectedAdmissionDate, BigDecimal budget,
                             String source, String status, LocalDate nextFollowUpDate, String notes,
                             Long convertedResidentId, boolean whatsappOptIn, Instant createdAt) {
        static EnquiryDto from(Enquiry e) {
            return new EnquiryDto(e.getId(), e.getHostelId(), e.getFullName(), e.getMobile(), e.getEmail(),
                    e.getGender(), e.getPreferredRoomType(), e.getExpectedAdmissionDate(), e.getBudget(),
                    e.getSource(), e.getStatus(), e.getNextFollowUpDate(), e.getNotes(), e.getConvertedResidentId(),
                    e.isWhatsappOptIn(), e.getCreatedAt());
        }
    }

    public record ConvertRequest(@NotNull Long hostelId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<EnquiryDto> list(Long hostelId, String status, String q, int page, int size) {
        AuthUser user = CurrentUser.get();
        if (hostelId != null) {
            hostelAccess.checkHostel(hostelId);
        }
        var ids = hostelAccess.scope((Long) null);
        var pageable = PageRequests.of(page, size, null, Set.of(),
                Sort.by(Sort.Order.asc("nextFollowUpDate").nullsLast(), Sort.Order.desc("createdAt")));
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(repository.search(user.ownerId(), ids, hostelId,
                status == null || status.isBlank() || "ALL".equals(status) ? null : status, like, pageable), EnquiryDto::from);
    }

    Enquiry getEntity(Long id) {
        Enquiry e = repository.findById(id).filter(x -> x.getOwnerId().equals(CurrentUser.get().ownerId()))
                .orElseThrow(() -> ApiException.notFound("Enquiry"));
        if (e.getHostelId() != null) {
            hostelAccess.checkEntity(e.getHostelId(), "Enquiry");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public EnquiryDto get(Long id) {
        return EnquiryDto.from(getEntity(id));
    }

    @Transactional
    public EnquiryDto create(EnquiryRequest req) {
        AuthUser user = CurrentUser.get();
        Enquiry e = new Enquiry();
        e.setOwnerId(user.ownerId());
        e.setCreatedBy(user.userId());
        apply(e, req);
        repository.save(e);
        auditService.log("ENQUIRY_CREATED", "ENQUIRY", e.getId(), e.getFullName());
        events.publishEvent(new DomainEvents.EnquiryCreated(user.ownerId(), e.getHostelId(), e.getId(),
                e.getFullName(), user.userId()));
        return EnquiryDto.from(e);
    }

    @Transactional
    public EnquiryDto update(Long id, EnquiryRequest req) {
        Enquiry e = getEntity(id);
        apply(e, req);
        auditService.log("ENQUIRY_UPDATED", "ENQUIRY", id, e.getStatus());
        return EnquiryDto.from(e);
    }

    /** Creates a registered resident from the enquiry so that it can be admitted to a bed. */
    @Transactional
    public Long convert(Long id, ConvertRequest req) {
        Enquiry e = getEntity(id);
        if (e.getConvertedResidentId() != null) {
            return e.getConvertedResidentId();
        }
        Resident r = residentService.create(new ResidentRequest(req.hostelId(), e.getFullName(), e.getMobile(), null,
                e.getEmail(), null, e.getGender(), null, null, null, null, null, null, null, null, null,
                e.isWhatsappOptIn(), "Converted from enquiry #" + e.getId()));
        e.setConvertedResidentId(r.getId());
        e.setStatus("CONVERTED");
        auditService.log("ENQUIRY_CONVERTED", "ENQUIRY", id, "resident " + r.getId());
        return r.getId();
    }

    private void apply(Enquiry e, EnquiryRequest req) {
        if (req.hostelId() != null) {
            hostelAccess.checkHostel(req.hostelId());
        }
        e.setHostelId(req.hostelId());
        e.setFullName(req.fullName().trim());
        e.setMobile(req.mobile().trim());
        e.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        e.setGender(req.gender());
        e.setPreferredRoomType(req.preferredRoomType());
        e.setExpectedAdmissionDate(req.expectedAdmissionDate());
        e.setBudget(req.budget());
        e.setSource(req.source());
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        e.setNextFollowUpDate(req.nextFollowUpDate());
        e.setNotes(req.notes());
        e.setWhatsappOptIn(req.whatsappOptIn());
    }
}
