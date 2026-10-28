package com.hostelmanagement.booking;

import com.hostelmanagement.admission.Admission;
import com.hostelmanagement.admission.AdmissionRepository;
import com.hostelmanagement.admission.BedAllocationRepository;
import com.hostelmanagement.bed.Bed;
import com.hostelmanagement.bed.BedRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.BedLocationQueries;
import com.hostelmanagement.common.BedLocationQueries.BedLocation;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.enquiry.Enquiry;
import com.hostelmanagement.enquiry.EnquiryRepository;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.room.Room;
import com.hostelmanagement.room.RoomRepository;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository repository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final AdmissionRepository admissionRepository;
    private final BedAllocationRepository allocationRepository;
    private final EnquiryRepository enquiryRepository;
    private final OccupancyQueries occupancyQueries;
    private final BedLocationQueries locationQueries;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final AppClock clock;

    public record BookingRequest(@NotNull Long bedId, Long enquiryId,
                                 @NotBlank @Size(max = 150) String guestName,
                                 @NotBlank @Pattern(regexp = "^[+]?[0-9 ]{10,15}$", message = "Enter a valid mobile number") String guestMobile,
                                 @NotNull LocalDate expectedAdmissionDate,
                                 LocalDate holdUntil,
                                 @DecimalMin("0.00") BigDecimal advanceAmount,
                                 @Size(max = 1000) String notes) {
    }

    public record BookingDto(Long id, Long hostelId, String hostelName, Long bedId, String bedLabel, Long roomId,
                             String roomNumber, String location, Long enquiryId, Long residentId, String guestName,
                             String guestMobile, LocalDate expectedAdmissionDate, LocalDate holdUntil,
                             BigDecimal advanceAmount, String status, String notes, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDto> list(Long hostelId, String status, int page, int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by("expectedAdmissionDate"));
        var result = repository.search(hostelAccess.scope(hostelId),
                status == null || status.isBlank() || "ALL".equals(status) ? null : status, pageable);
        var locs = locationQueries.locate(result.getContent().stream().map(Booking::getBedId).toList());
        return PageResponse.of(result, b -> toDto(b, locs.get(b.getBedId())));
    }

    @Transactional(readOnly = true)
    public BookingDto get(Long id) {
        Booking b = getEntity(id);
        return toDto(b, locationQueries.locate(b.getBedId()));
    }

    Booking getEntity(Long id) {
        Booking b = repository.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
        hostelAccess.checkEntity(b.getHostelId(), "Booking");
        return b;
    }

    /**
     * Reserves a bed. Rules: bed must not be under maintenance, must not already be reserved, and if it is
     * currently occupied the occupant must have an expected checkout date before the guest's arrival.
     */
    @Transactional
    public BookingDto create(BookingRequest req) {
        LocalDate today = clock.today();
        if (req.expectedAdmissionDate().isBefore(today)) {
            throw ApiException.badRequest("Expected admission date cannot be in the past");
        }
        if (req.holdUntil() != null && req.holdUntil().isBefore(today)) {
            throw ApiException.badRequest("Hold-until date cannot be in the past");
        }
        Bed bed = bedRepository.lockById(req.bedId()).orElseThrow(() -> ApiException.notFound("Bed"));
        Room room = roomRepository.findByIdAndDeletedAtIsNull(bed.getRoomId()).orElseThrow(() -> ApiException.notFound("Bed"));
        hostelAccess.checkEntity(room.getHostelId(), "Bed");
        if (occupancyQueries.bedUnderMaintenance(bed.getId())) {
            throw ApiException.conflict("This bed is under maintenance and cannot be reserved");
        }
        repository.findByBedIdAndStatus(bed.getId(), "RESERVED").ifPresent(existing -> {
            throw ApiException.conflict("This bed is already reserved for " + existing.getGuestName());
        });
        allocationRepository.findByBedIdAndEndDateIsNull(bed.getId()).ifPresent(alloc -> {
            Admission occupant = admissionRepository.findById(alloc.getAdmissionId()).orElseThrow();
            if (occupant.getExpectedCheckoutDate() == null
                    || !occupant.getExpectedCheckoutDate().isBefore(req.expectedAdmissionDate())) {
                throw ApiException.conflict("This bed is occupied"
                        + (occupant.getExpectedCheckoutDate() == null ? " with no expected checkout date"
                        : " until " + occupant.getExpectedCheckoutDate())
                        + ". Choose a later admission date or another bed.");
            }
        });
        Booking b = new Booking();
        b.setHostelId(room.getHostelId());
        b.setBedId(bed.getId());
        if (req.enquiryId() != null) {
            Enquiry e = enquiryRepository.findById(req.enquiryId())
                    .filter(x -> x.getOwnerId().equals(CurrentUser.get().ownerId()))
                    .orElseThrow(() -> ApiException.notFound("Enquiry"));
            if (e.getHostelId() != null) {
                hostelAccess.checkEntity(e.getHostelId(), "Enquiry");
            }
            b.setEnquiryId(e.getId());
            if ("NEW".equals(e.getStatus()) || "CONTACTED".equals(e.getStatus()) || "FOLLOW_UP".equals(e.getStatus())) {
                e.setStatus("BOOKED");
            }
        }
        b.setGuestName(req.guestName().trim());
        b.setGuestMobile(req.guestMobile().trim());
        b.setExpectedAdmissionDate(req.expectedAdmissionDate());
        b.setHoldUntil(req.holdUntil());
        b.setAdvanceAmount(Money.of(req.advanceAmount()));
        b.setNotes(req.notes());
        b.setCreatedBy(CurrentUser.get().userId());
        repository.saveAndFlush(b);
        auditService.log("BED_RESERVED", "BOOKING", b.getId(), b.getGuestName() + " from " + b.getExpectedAdmissionDate());
        return toDto(b, locationQueries.locate(b.getBedId()));
    }

    @Transactional
    public BookingDto cancel(Long id, String reason) {
        Booking b = getEntity(id);
        if (!"RESERVED".equals(b.getStatus())) {
            throw ApiException.conflict("Only active reservations can be cancelled");
        }
        b.setStatus("CANCELLED");
        if (reason != null && !reason.isBlank()) {
            b.setNotes((b.getNotes() == null ? "" : b.getNotes() + "\n") + "Cancelled: " + reason.trim());
        }
        auditService.log("BOOKING_CANCELLED", "BOOKING", id, reason);
        return toDto(b, locationQueries.locate(b.getBedId()));
    }

    /** Releases reservations whose hold period has passed. Runs daily at 00:30 business time. */
    @Scheduled(cron = "0 30 0 * * *", zone = "${app.time-zone:Asia/Kolkata}")
    @Transactional
    public void expireReservations() {
        var expired = repository.findByStatusAndHoldUntilBefore("RESERVED", clock.today());
        expired.forEach(b -> b.setStatus("EXPIRED"));
        if (!expired.isEmpty()) {
            log.info("Expired {} bed reservations", expired.size());
        }
    }

    private BookingDto toDto(Booking b, BedLocation loc) {
        return new BookingDto(b.getId(), b.getHostelId(), loc == null ? null : loc.hostelName(), b.getBedId(),
                loc == null ? null : loc.bedLabel(), loc == null ? null : loc.roomId(),
                loc == null ? null : loc.roomNumber(), loc == null ? null : loc.display(), b.getEnquiryId(),
                b.getResidentId(), b.getGuestName(), b.getGuestMobile(), b.getExpectedAdmissionDate(), b.getHoldUntil(),
                b.getAdvanceAmount(), b.getStatus(), b.getNotes(), b.getCreatedAt());
    }
}
