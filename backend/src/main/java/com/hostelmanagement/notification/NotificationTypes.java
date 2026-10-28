package com.hostelmanagement.notification;

import java.util.List;

public final class NotificationTypes {

    public static final String PAYMENT_DUE = "PAYMENT_DUE";
    public static final String PAYMENT_OVERDUE = "PAYMENT_OVERDUE";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String UPCOMING_CHECKOUT = "UPCOMING_CHECKOUT";
    public static final String NEW_ADMISSION = "NEW_ADMISSION";
    public static final String CHECKOUT = "CHECKOUT";
    public static final String MAINTENANCE = "MAINTENANCE";
    public static final String NEW_ENQUIRY = "NEW_ENQUIRY";
    public static final String ENQUIRY_FOLLOW_UP = "ENQUIRY_FOLLOW_UP";
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";

    public static final List<String> ALL = List.of(PAYMENT_DUE, PAYMENT_OVERDUE, PAYMENT_RECEIVED, UPCOMING_CHECKOUT,
            NEW_ADMISSION, CHECKOUT, MAINTENANCE, NEW_ENQUIRY, ENQUIRY_FOLLOW_UP, ANNOUNCEMENT);

    private NotificationTypes() {
    }
}
