package com.hostelmanagement.mail;

/** A plain-text e-mail. {@code containsSecret} marks messages whose body must never reach the logs outside development. */
public record EmailMessage(String to, String subject, String body, boolean containsSecret) {
}
