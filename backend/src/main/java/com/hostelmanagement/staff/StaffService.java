package com.hostelmanagement.staff;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository repository;
    private final UserRepository userRepository;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;

    public record StaffRequest(Long hostelId,
                               Long userId,
                               @NotBlank @Size(max = 150) String fullName,
                               @NotBlank @Pattern(regexp = "^[+]?[0-9 ]{10,15}$", message = "Enter a valid mobile number") String mobile,
                               @NotBlank @Size(max = 60) String designation,
                               @DecimalMin("0.00") BigDecimal monthlySalary,
                               LocalDate joinedOn,
                               @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    public record StaffDto(Long id, Long hostelId, Long userId, String fullName, String mobile, String designation,
                           BigDecimal monthlySalary, LocalDate joinedOn, String status) {
        static StaffDto from(Staff s, boolean showSalary) {
            return new StaffDto(s.getId(), s.getHostelId(), s.getUserId(), s.getFullName(), s.getMobile(),
                    s.getDesignation(), showSalary ? s.getMonthlySalary() : null, s.getJoinedOn(), s.getStatus());
        }
    }

    /** Visible to anyone who can manage maintenance (to assign work); salaries only with STAFF_MANAGE. */
    @Transactional(readOnly = true)
    public List<StaffDto> list(Long hostelId) {
        AuthUser user = CurrentUser.get();
        if (hostelId != null) {
            hostelAccess.checkHostel(hostelId);
        }
        boolean salaries = user.has("STAFF_MANAGE");
        // Owner-wide staff (no hostel) are the owner's own records: their salaries are not shown to delegated managers
        return repository.visible(user.ownerId(), hostelAccess.scope((Long) null), hostelId).stream()
                .map(s -> StaffDto.from(s, salaries && (user.isOwner() || s.getHostelId() != null))).toList();
    }

    @Transactional
    public StaffDto create(StaffRequest req) {
        Staff s = new Staff();
        s.setOwnerId(CurrentUser.get().ownerId());
        apply(s, req);
        repository.save(s);
        auditService.log("STAFF_CREATED", "STAFF", s.getId(), s.getFullName());
        return StaffDto.from(s, true);
    }

    @Transactional
    public StaffDto update(Long id, StaffRequest req) {
        Staff s = repository.findById(id).filter(x -> x.getOwnerId().equals(CurrentUser.get().ownerId()))
                .orElseThrow(() -> ApiException.notFound("Staff member"));
        if (s.getHostelId() != null) {
            hostelAccess.checkEntity(s.getHostelId(), "Staff member");
        } else if (!CurrentUser.get().isOwner()) {
            throw ApiException.forbidden("Only the owner can change owner-wide staff records");
        }
        apply(s, req);
        auditService.log("STAFF_UPDATED", "STAFF", id, s.getFullName());
        return StaffDto.from(s, true);
    }

    private void apply(Staff s, StaffRequest req) {
        if (req.hostelId() != null) {
            hostelAccess.checkHostel(req.hostelId());
        } else if (!CurrentUser.get().isOwner()) {
            throw ApiException.badRequest("Select the hostel this staff member works at");
        }
        if (req.userId() != null) {
            userRepository.findByIdAndOwnerId(req.userId(), CurrentUser.get().ownerId())
                    .orElseThrow(() -> ApiException.badRequest("Linked user does not exist"));
        }
        s.setHostelId(req.hostelId());
        s.setUserId(req.userId());
        s.setFullName(req.fullName().trim());
        s.setMobile(req.mobile().trim());
        s.setDesignation(req.designation().trim());
        s.setMonthlySalary(req.monthlySalary() == null ? null : Money.of(req.monthlySalary()));
        s.setJoinedOn(req.joinedOn());
        s.setStatus(req.status() == null ? "ACTIVE" : req.status());
    }
}
