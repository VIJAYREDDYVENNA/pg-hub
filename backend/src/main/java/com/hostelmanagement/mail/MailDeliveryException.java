package com.hostelmanagement.mail;

/** Delivery failed. The message deliberately never includes the e-mail body or the credentials. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
