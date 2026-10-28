package com.hostelmanagement.notification;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends BaseEntity {

    /** NULL = system default template. */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 1500)
    private String body;

    /** Name of the approved template at the WhatsApp provider (required for business-initiated messages). */
    @Column(name = "provider_template", length = 100)
    private String providerTemplate;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode = "en";

    @Column(nullable = false)
    private boolean active = true;
}
