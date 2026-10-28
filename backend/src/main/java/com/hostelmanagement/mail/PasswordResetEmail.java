package com.hostelmanagement.mail;

import java.time.Duration;

/** The password-reset message. Plain text on purpose: nothing to render, nothing to mis-render. */
public final class PasswordResetEmail {

    private PasswordResetEmail() {
    }

    public static EmailMessage build(String to, String fullName, String link, Duration validFor) {
        String greeting = fullName == null || fullName.isBlank() ? "Hello," : "Hello " + fullName.trim() + ",";
        String body = greeting + "\n\n"
                + "We received a request to reset the password of your HostelHub account (" + to + ").\n\n"
                + "Open this link to choose a new password. It works once and expires in " + validFor.toMinutes() + " minutes:\n"
                + link + "\n\n"
                + "If you did not ask for this, you can ignore this e-mail: your password stays as it is and nobody can use the link "
                + "without access to this mailbox.\n\n"
                + "HostelHub";
        return new EmailMessage(to, "Reset your HostelHub password", body, true);
    }
}
