package com.hostelmanagement.admission.dto;

import com.hostelmanagement.resident.dto.ResidentDtos.ResidentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AdmissionDtos {

    private AdmissionDtos() {
    }

    public record AdvancePayment(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotNull @Pattern(regexp = "CASH|UPI|BANK_TRANSFER|CARD|OTHER") String method,
                                 @Size(max = 100) String transactionReference) {
    }

    /** Either residentId (existing resident) or newResident must be provided. */
    public record AdmitRequest(Long residentId,
                               @Valid ResidentRequest newResident,
                               @NotNull Long bedId,
                               @NotNull LocalDate admissionDate,
                               LocalDate expectedCheckoutDate,
                               boolean checkoutConfirmed,
                               @DecimalMin("0.00") BigDecimal monthlyRent,
                               @DecimalMin("0.00") BigDecimal securityDeposit,
                               @Pattern(regexp = "MONTHLY|QUARTERLY|HALF_YEARLY|YEARLY|CUSTOM") String billingCycle,
                               @Min(1) @Max(12) Integer cycleMonths,
                               @Valid AdvancePayment advancePayment,
                               Long bookingId,
                               @Size(max = 1000) String notes,
                               boolean sendWhatsApp) {
    }

    public record CheckoutRequest(@NotNull LocalDate checkoutDate, @Size(max = 1000) String notes) {
    }

    public record TransferRequest(@NotNull Long newBedId, @NotNull LocalDate transferDate,
                                  @DecimalMin("0.00") BigDecimal newMonthlyRent, @Size(max = 500) String reason) {
    }

    public record UpdateAdmissionRequest(LocalDate expectedCheckoutDate, boolean checkoutConfirmed,
                                         @NotNull @DecimalMin("0.00") BigDecimal monthlyRent,
                                         @Size(max = 1000) String notes) {
    }

    public record AllocationDto(Long id, Long bedId, String bedLabel, Long roomId, String roomNumber,
                                String floorName, String buildingName, Long hostelId, String hostelName,
                                LocalDate startDate, LocalDate endDate, String endReason, String notes) {
    }

    public record AdmissionDto(Long id, Long residentId, String residentName, String residentMobile, Long hostelId,
                               String hostelName, LocalDate admissionDate, LocalDate expectedCheckoutDate,
                               boolean checkoutConfirmed, LocalDate actualCheckoutDate, BigDecimal monthlyRent,
                               BigDecimal securityDeposit, String billingCycle, int cycleMonths, String status,
                               String notes, String checkoutNotes, AllocationDto currentBed,
                               List<AllocationDto> allocations) {
    }
}
