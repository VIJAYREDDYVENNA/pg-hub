package com.hostelmanagement.subscription;

import com.hostelmanagement.subscription.SubscriptionDtos.FeatureDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of feature codes that plans can enable. Code constants exist so call sites never spell a feature as a
 * string literal; whether a plan includes a feature is data in {@code plan_entitlements}.
 */
public final class Features {

    public static final String ONLINE_PAYMENTS = "ONLINE_PAYMENTS";
    public static final String WHATSAPP_MESSAGING = "WHATSAPP_MESSAGING";
    public static final String EXPENSE_TRACKING = "EXPENSE_TRACKING";
    public static final String ADVANCED_REPORTS = "ADVANCED_REPORTS";
    public static final String BULK_IMPORT_EXPORT = "BULK_IMPORT_EXPORT";
    public static final String CUSTOM_BRANDING = "CUSTOM_BRANDING";
    public static final String EXTENDED_AUDIT_RETENTION = "EXTENDED_AUDIT_RETENTION";

    private static final Map<String, FeatureDto> CATALOG = new LinkedHashMap<>();

    static {
        add(ONLINE_PAYMENTS, "Online rent payments", "Collect rent through the payment gateway and pay links");
        add(WHATSAPP_MESSAGING, "WhatsApp messaging", "Send WhatsApp reminders and notices to residents");
        add(EXPENSE_TRACKING, "Expense tracking", "Record and analyse hostel expenses");
        add(ADVANCED_REPORTS, "Advanced reports", "Dues ageing, profit and loss and occupancy trends (when available)");
        add(BULK_IMPORT_EXPORT, "Bulk import / export", "Import residents and rooms, export data (when available)");
        add(CUSTOM_BRANDING, "Custom branding", "Owner logo and colours on receipts and pay pages (when available)");
        add(EXTENDED_AUDIT_RETENTION, "Extended audit retention", "Keep audit history longer (when available)");
    }

    private Features() {
    }

    private static void add(String code, String label, String description) {
        CATALOG.put(code, new FeatureDto(code, label, description));
    }

    public static List<FeatureDto> all() {
        return List.copyOf(CATALOG.values());
    }

    public static boolean isKnown(String code) {
        return CATALOG.containsKey(code);
    }
}
