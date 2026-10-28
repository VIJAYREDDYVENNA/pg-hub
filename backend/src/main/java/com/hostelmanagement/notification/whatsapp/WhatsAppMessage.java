package com.hostelmanagement.notification.whatsapp;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_messages")
public class WhatsAppMessage extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "resident_id")
    private Long residentId;

    @Column(name = "to_phone", nullable = false, length = 20)
    private String toPhone;

    @Column(name = "template_code", length = 40)
    private String templateCode;

    @Column(nullable = false, length = 2000)
    private String body;

    /** QUEUED, SENT, DELIVERED, READ, FAILED, SKIPPED_NO_CONSENT. */
    @Column(nullable = false, length = 20)
    private String status;

    /** True when produced by the development mock provider: nothing was sent to WhatsApp. */
    @Column(nullable = false)
    private boolean simulated;

    /** Ordered provider template parameters (unit-separator delimited) so retries resend the same template. */
    @Column(name = "template_params", length = 1500)
    private String templateParams;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
