package com.hostelmanagement.admission;

import com.hostelmanagement.admission.dto.AdmissionDtos.AdmitRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.CheckoutRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.TransferRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.UpdateAdmissionRequest;
import com.hostelmanagement.bed.Bed;
import com.hostelmanagement.bed.BedRepository;
import com.hostelmanagement.booking.Booking;
import com.hostelmanagement.booking.BookingRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.BedLocationQueries;
import com.hostelmanagement.common.BedLocationQueries.BedLocation;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.events.DomainEvents;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.payments.InvoiceService;
import com.hostelmanagement.payments.PaymentService;
import com.hostelmanagement.payments.PaymentService.PaymentCommand;
import com.hostelmanagement.resident.Resident;
import com.hostelmanagement.resident.ResidentService;
import com.hostelmanagement.room.Room;
import com.hostelmanagement.room.RoomRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Admission lifecycle. Every operation runs in one transaction and locks the affected bed row(s), so two users
 * can never allocate the same bed; the unique "active bed" index in bed_allocations is a second safety net.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final BedAllocationRepository allocationRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final ResidentService residentService;
    private final OccupancyQueries occupancyQueries;
    private final BedLocationQueries locationQueries;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public Admission getEntity(Long id) {
        Admission a = admissionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Admission"));
        hostelAccess.checkEntity(a.getHostelId(), "Admission");
        return a;
    }

    @Transactional
    public Long admit(AdmitRequest req) {
        AuthUser user = CurrentUser.get();
        LocalDate today = clock.today();
        if (req.admissionDate().isAfter(today)) {
            throw ApiException.badRequest("Admission date cannot be in the future. Use a booking to reserve a bed for a future date.");
        }
        if (req.expectedCheckoutDate() != null && !req.expectedCheckoutDate().isAfter(req.admissionDate())) {
            throw ApiException.badRequest("Expected checkout date must be after the admission date");
        }
        Bed bed = lockAvailableBed(req.bedId(), req.bookingId());
        Room room = roomRepository.findById(bed.getRoomId()).orElseThrow();

        Resident resident;
        if (req.residentId() != null) {
            resident = residentService.getEntity(req.residentId());
        } else if (req.newResident() != null) {
            resident = residentService.create(req.newResident());
        } else {
            throw ApiException.badRequest("Select an existing resident or enter the new resident's details");
        }
        if (admissionRepository.existsByResidentIdAndStatus(resident.getId(), "ACTIVE")) {
            throw ApiException.conflict(resident.getFullName() + " already has an active admission");
        }

        BigDecimal rent = req.monthlyRent() != null ? Money.of(req.monthlyRent())
                : Money.of(bed.getRentOverride() != null ? bed.getRentOverride() : room.getMonthlyRentPerBed());
        BigDecimal deposit = req.securityDeposit() != null ? Money.of(req.securityDeposit())
                : Money.of(room.getSecurityDeposit());

        Admission a = new Admission();
        a.setResidentId(resident.getId());
        a.setHostelId(room.getHostelId());
        a.setAdmissionDate(req.admissionDate());
        a.setExpectedCheckoutDate(req.expectedCheckoutDate());
        a.setCheckoutConfirmed(req.expectedCheckoutDate() != null && req.checkoutConfirmed());
        a.setMonthlyRent(rent);
        a.setSecurityDeposit(deposit);
        String cycle = req.billingCycle() == null ? "MONTHLY" : req.billingCycle();
        a.setBillingCycle(cycle);
        a.setCycleMonths(cycleMonths(cycle, req.cycleMonths()));
        a.setNotes(req.notes());
        a.setBookingId(req.bookingId());
        a.setCreatedBy(user.userId());
        admissionRepository.save(a);

        BedAllocation alloc = new BedAllocation();
        alloc.setAdmissionId(a.getId());
        alloc.setBedId(bed.getId());
        alloc.setStartDate(req.admissionDate());
        alloc.setCreatedBy(user.userId());
        allocationRepository.saveAndFlush(alloc);

        resident.setHostelId(room.getHostelId());
        resident.setStatus("ACTIVE");

        if (req.bookingId() != null) {
            Booking booking = bookingRepository.findById(req.bookingId()).orElseThrow();
            booking.setStatus("CONVERTED");
            booking.setResidentId(resident.getId());
        }

        invoiceService.createDepositInvoice(a, user.ownerId(), user.userId());
        invoiceService.generateRentInvoices(a, user.ownerId(), today, user.userId());

        if (req.advancePayment() != null && Money.isPositive(req.advancePayment().amount())) {
            paymentService.record(new PaymentCommand(user.ownerId(), user.userId(), resident.getId(), a.getHostelId(),
                    a.getId(), Money.of(req.advancePayment().amount()), today, req.advancePayment().method(),
                    req.advancePayment().transactionReference(), "Advance collected at admission", null, false, null));
        }

        auditService.log("RESIDENT_ADMITTED", "ADMISSION", a.getId(),
                resident.getFullName() + " -> room " + room.getRoomNumber() + " bed " + bed.getBedLabel());
        events.publishEvent(new DomainEvents.AdmissionCreated(user.ownerId(), a.getHostelId(), a.getId(),
                resident.getId(), resident.getFullName(), room.getRoomNumber(), bed.getBedLabel(), a.getAdmissionDate(),
                rent, req.sendWhatsApp(), user.userId()));
        return a.getId();
    }

    @Transactional
    public void checkout(Long admissionId, CheckoutRequest req) {
        AuthUser user = CurrentUser.get();
        Admission a = lockActive(admissionId);
        LocalDate today = clock.today();
        if (req.checkoutDate().isAfter(today)) {
            throw ApiException.badRequest("Checkout date cannot be in the future. Set an expected checkout date instead.");
        }
        if (req.checkoutDate().isBefore(a.getAdmissionDate())) {
            throw ApiException.badRequest("Checkout date cannot be before the admission date");
        }
        BedAllocation alloc = allocationRepository.findByAdmissionIdAndEndDateIsNull(admissionId)
                .orElseThrow(() -> ApiException.conflict("No active bed allocation found for this admission"));
        if (req.checkoutDate().isBefore(alloc.getStartDate())) {
            throw ApiException.badRequest("Checkout date cannot be before the current bed allocation started");
        }
        alloc.setEndDate(req.checkoutDate());
        alloc.setEndReason("CHECKOUT");
        a.setStatus("CHECKED_OUT");
        a.setActualCheckoutDate(req.checkoutDate());
        a.setCheckoutNotes(req.notes());
        Resident resident = residentService.getEntity(a.getResidentId());
        resident.setStatus("CHECKED_OUT");
        int cancelled = invoiceService.cancelUnpaidRentAfter(a, req.checkoutDate());
        BedLocation loc = locationQueries.locate(alloc.getBedId());
        auditService.log("RESIDENT_CHECKED_OUT", "ADMISSION", a.getId(),
                resident.getFullName() + " on " + req.checkoutDate() + "; cancelled future invoices: " + cancelled);
        events.publishEvent(new DomainEvents.ResidentCheckedOut(user.ownerId(), a.getHostelId(), a.getId(),
                resident.getId(), resident.getFullName(), loc == null ? "" : loc.roomNumber(), req.checkoutDate(),
                user.userId()));
    }

    @Transactional
    public void transfer(Long admissionId, TransferRequest req) {
        AuthUser user = CurrentUser.get();
        Admission a = lockActive(admissionId);
        LocalDate today = clock.today();
        if (req.transferDate().isAfter(today)) {
            throw ApiException.badRequest("Transfer date cannot be in the future");
        }
        BedAllocation current = allocationRepository.findByAdmissionIdAndEndDateIsNull(admissionId)
                .orElseThrow(() -> ApiException.conflict("No active bed allocation found for this admission"));
        if (current.getBedId().equals(req.newBedId())) {
            throw ApiException.badRequest("The resident is already in this bed");
        }
        if (req.transferDate().isBefore(current.getStartDate())) {
            throw ApiException.badRequest("Transfer date cannot be before the current allocation started");
        }
        Bed newBed = lockAvailableBed(req.newBedId(), null);
        Room newRoom = roomRepository.findById(newBed.getRoomId()).orElseThrow();

        current.setEndDate(req.transferDate());
        current.setEndReason("TRANSFER");
        current.setNotes(req.reason());
        allocationRepository.saveAndFlush(current);

        BedAllocation next = new BedAllocation();
        next.setAdmissionId(a.getId());
        next.setBedId(newBed.getId());
        next.setStartDate(req.transferDate());
        next.setCreatedBy(user.userId());
        next.setNotes(req.reason());
        allocationRepository.saveAndFlush(next);

        if (!a.getHostelId().equals(newRoom.getHostelId())) {
            a.setHostelId(newRoom.getHostelId());
            Resident resident = residentService.getEntity(a.getResidentId());
            resident.setHostelId(newRoom.getHostelId());
        }
        if (req.newMonthlyRent() != null) {
            a.setMonthlyRent(Money.of(req.newMonthlyRent()));
        }
        BedLocation from = locationQueries.locate(current.getBedId());
        auditService.log("BED_TRANSFERRED", "ADMISSION", a.getId(),
                (from == null ? "?" : from.display()) + " -> room " + newRoom.getRoomNumber() + " bed " + newBed.getBedLabel());
    }

    @Transactional
    public void update(Long admissionId, UpdateAdmissionRequest req) {
        Admission a = getEntity(admissionId);
        if (!"ACTIVE".equals(a.getStatus())) {
            throw ApiException.conflict("Only active admissions can be changed");
        }
        if (req.expectedCheckoutDate() != null && !req.expectedCheckoutDate().isAfter(a.getAdmissionDate())) {
            throw ApiException.badRequest("Expected checkout date must be after the admission date");
        }
        a.setExpectedCheckoutDate(req.expectedCheckoutDate());
        a.setCheckoutConfirmed(req.expectedCheckoutDate() != null && req.checkoutConfirmed());
        a.setMonthlyRent(Money.of(req.monthlyRent()));
        a.setNotes(req.notes());
        auditService.log("ADMISSION_UPDATED", "ADMISSION", a.getId(),
                "expectedCheckout=" + req.expectedCheckoutDate() + ", confirmed=" + req.checkoutConfirmed()
                        + ", rent=" + req.monthlyRent());
    }

    private Admission lockActive(Long admissionId) {
        Admission a = admissionRepository.lockById(admissionId).orElseThrow(() -> ApiException.notFound("Admission"));
        hostelAccess.checkEntity(a.getHostelId(), "Admission");
        if (!"ACTIVE".equals(a.getStatus())) {
            throw ApiException.conflict("This admission is not active");
        }
        return a;
    }

    /**
     * Locks the bed and verifies it can be allocated: accessible, not occupied, not under maintenance and either
     * not reserved or reserved by the booking being converted.
     */
    private Bed lockAvailableBed(Long bedId, Long bookingId) {
        Bed bed = bedRepository.lockById(bedId).orElseThrow(() -> ApiException.notFound("Bed"));
        Room room = roomRepository.findByIdAndDeletedAtIsNull(bed.getRoomId())
                .orElseThrow(() -> ApiException.notFound("Bed"));
        hostelAccess.checkEntity(room.getHostelId(), "Bed");
        if (occupancyQueries.bedIsOccupied(bedId)) {
            throw ApiException.conflict("This bed is already occupied. Please choose another bed.");
        }
        if (occupancyQueries.bedUnderMaintenance(bedId)) {
            throw ApiException.conflict("This bed or room is under maintenance / unavailable and cannot be allocated.");
        }
        var reservation = bookingRepository.findByBedIdAndStatus(bedId, "RESERVED");
        if (reservation.isPresent() && !reservation.get().getId().equals(bookingId)) {
            throw ApiException.conflict("This bed is reserved for " + reservation.get().getGuestName()
                    + ". Convert or cancel that booking first.");
        }
        if (bookingId != null && reservation.isEmpty()) {
            throw ApiException.conflict("The selected booking is not an active reservation for this bed");
        }
        return bed;
    }

    static int cycleMonths(String cycle, Integer custom) {
        return switch (cycle) {
            case "QUARTERLY" -> 3;
            case "HALF_YEARLY" -> 6;
            case "YEARLY" -> 12;
            case "CUSTOM" -> {
                if (custom == null) {
                    throw ApiException.badRequest("Enter the number of months for a custom billing cycle");
                }
                yield custom;
            }
            default -> 1;
        };
    }
}
