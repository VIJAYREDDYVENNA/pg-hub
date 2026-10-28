package com.hostelmanagement.notification;

/**
 * Delivery channel abstraction for internal notifications. The in-app channel is always active; e-mail or push
 * channels can be added later by implementing this interface (each implementation receives every notification
 * persisted for a user and decides whether to forward it).
 */
public interface NotificationChannel {

    String name();

    void deliver(Notification notification);
}
