package com.hostelmanagement.security;

import java.util.Set;

/** Permission codes (mirrors the permissions reference table). */
public final class Permissions {

    public static final String HOSTEL_VIEW = "HOSTEL_VIEW";
    public static final String HOSTEL_MANAGE = "HOSTEL_MANAGE";
    public static final String ROOM_MANAGE = "ROOM_MANAGE";
    public static final String RESIDENT_VIEW = "RESIDENT_VIEW";
    public static final String RESIDENT_MANAGE = "RESIDENT_MANAGE";
    public static final String ENQUIRY_MANAGE = "ENQUIRY_MANAGE";
    public static final String PAYMENT_VIEW = "PAYMENT_VIEW";
    public static final String PAYMENT_COLLECT = "PAYMENT_COLLECT";
    public static final String PAYMENT_ADJUST = "PAYMENT_ADJUST";
    public static final String FINANCE_REPORTS = "FINANCE_REPORTS";
    public static final String EXPENSE_MANAGE = "EXPENSE_MANAGE";
    public static final String MAINTENANCE_MANAGE = "MAINTENANCE_MANAGE";
    public static final String NOTIFICATION_SEND = "NOTIFICATION_SEND";
    public static final String STAFF_MANAGE = "STAFF_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    public static final Set<String> ALL = Set.of(HOSTEL_VIEW, HOSTEL_MANAGE, ROOM_MANAGE, RESIDENT_VIEW,
            RESIDENT_MANAGE, ENQUIRY_MANAGE, PAYMENT_VIEW, PAYMENT_COLLECT, PAYMENT_ADJUST, FINANCE_REPORTS,
            EXPENSE_MANAGE, MAINTENANCE_MANAGE, NOTIFICATION_SEND, STAFF_MANAGE, SETTINGS_MANAGE);

    private Permissions() {
    }
}
