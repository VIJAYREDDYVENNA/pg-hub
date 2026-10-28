package com.hostelmanagement.notification;

import com.hostelmanagement.common.events.DomainEvents.AdmissionCreated;
import com.hostelmanagement.common.events.DomainEvents.EnquiryCreated;
import com.hostelmanagement.common.events.DomainEvents.MaintenanceChanged;
import com.hostelmanagement.common.events.DomainEvents.PaymentRecorded;
import com.hostelmanagement.common.events.DomainEvents.ResidentCheckedOut;
import com.hostelmanagement.notification.whatsapp.WhatsAppService;
import com.hostelmanagement.notification.whatsapp.WhatsAppService.SendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Turns committed business events into in-app notifications and (when requested) WhatsApp messages.
 * Runs after the business transaction commits, so a failure here can never roll back an admission or payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final NotificationService notifications;
    private final WhatsAppService whatsApp;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAdmission(AdmissionCreated e) {
        safely(() -> {
            notifications.notifyHostel(e.ownerId(), e.hostelId(), NotificationTypes.NEW_ADMISSION, "New admission",
                    e.residentName() + " admitted to Room " + e.roomNumber() + " / Bed " + e.bedLabel(),
                    "/residents/" + e.residentId(), "admission-" + e.admissionId());
            if (e.sendWhatsApp()) {
                whatsApp.send(new SendCommand(e.ownerId(), e.hostelId(), e.residentId(), null,
                        "ADMISSION_CONFIRMATION", Map.of("roomNumber", e.roomNumber(), "bedLabel", e.bedLabel(),
                        "admissionDate", DATE.format(e.admissionDate()), "amount", e.monthlyRent().toPlainString()),
                        null, "ADMISSION", e.admissionId(), e.userId()));
            }
        });
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckout(ResidentCheckedOut e) {
        safely(() -> notifications.notifyHostel(e.ownerId(), e.hostelId(), NotificationTypes.CHECKOUT, "Resident checked out",
                e.residentName() + " checked out of Room " + e.roomNumber() + " on " + DATE.format(e.checkoutDate()),
                "/residents/" + e.residentId(), "checkout-" + e.admissionId()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPayment(PaymentRecorded e) {
        safely(() -> {
            notifications.notifyHostel(e.ownerId(), e.hostelId(), NotificationTypes.PAYMENT_RECEIVED, "Payment received",
                    "Rs. " + e.amount().toPlainString() + " from " + e.residentName() + " (" + e.receiptNumber() + ")",
                    "/payments/" + e.paymentId(), "payment-" + e.paymentId());
            if (e.sendWhatsApp()) {
                whatsApp.send(new SendCommand(e.ownerId(), e.hostelId(), e.residentId(), null, "PAYMENT_CONFIRMATION",
                        Map.of("amount", e.amount().toPlainString(), "paymentDate", DATE.format(e.paymentDate()),
                                "method", e.method().replace('_', ' '), "receiptNumber", e.receiptNumber()),
                        null, "PAYMENT", e.paymentId(), e.userId()));
            }
        });
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenance(MaintenanceChanged e) {
        safely(() -> notifications.notifyHostel(e.ownerId(), e.hostelId(), NotificationTypes.MAINTENANCE,
                e.created() ? "New maintenance request" : "Maintenance updated",
                e.title() + " - " + e.status().replace('_', ' ').toLowerCase(),
                "/maintenance?id=" + e.requestId(), "maint-" + e.requestId() + "-" + e.status()));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEnquiry(EnquiryCreated e) {
        safely(() -> notifications.notifyHostel(e.ownerId(), e.hostelId(), NotificationTypes.NEW_ENQUIRY, "New enquiry",
                e.name() + " enquired about a bed", "/enquiries?id=" + e.enquiryId(), "enquiry-" + e.enquiryId()));
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            log.error("Failed to create notification", ex);
        }
    }
}
