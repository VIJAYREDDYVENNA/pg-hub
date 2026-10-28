package com.hostelmanagement.common.events;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Events published after important business actions; consumed (after commit) by notifications/WhatsApp. */
public final class DomainEvents {

    private DomainEvents() {
    }

    public record AdmissionCreated(Long ownerId, Long hostelId, Long admissionId, Long residentId, String residentName,
                                   String roomNumber, String bedLabel, LocalDate admissionDate, BigDecimal monthlyRent,
                                   boolean sendWhatsApp, Long userId) {
    }

    public record ResidentCheckedOut(Long ownerId, Long hostelId, Long admissionId, Long residentId,
                                     String residentName, String roomNumber, LocalDate checkoutDate, Long userId) {
    }

    public record PaymentRecorded(Long ownerId, Long hostelId, Long paymentId, Long residentId, String residentName,
                                  BigDecimal amount, String receiptNumber, String method, LocalDate paymentDate,
                                  boolean sendWhatsApp, Long userId) {
    }

    public record MaintenanceChanged(Long ownerId, Long hostelId, Long requestId, String title, String status,
                                     boolean created, Long userId) {
    }

    public record EnquiryCreated(Long ownerId, Long hostelId, Long enquiryId, String name, Long userId) {
    }
}
