package com.hostelmanagement.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends mail after the surrounding transaction commits and off the request thread. Both matter for password reset:
 * the token must be saved before the link goes out, and the HTTP response time must not depend on whether a message was
 * sent, otherwise the endpoint would reveal which e-mail addresses have accounts.
 */
@Slf4j
@Component
public class MailDispatcher {

    private final EmailService email;
    private final TaskExecutor executor;

    public MailDispatcher(EmailService email, @Qualifier("mailExecutor") TaskExecutor executor) {
        this.email = email;
        this.executor = executor;
    }

    /** Queues the message for delivery once the current transaction (if any) has committed. */
    public void sendAfterCommit(EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(message);
                }
            });
        } else {
            enqueue(message);
        }
    }

    private void enqueue(EmailMessage message) {
        try {
            executor.execute(() -> deliver(message));
        } catch (RuntimeException e) {
            log.error("Could not queue e-mail \"{}\": {}", message.subject(), e.getMessage());
        }
    }

    void deliver(EmailMessage message) {
        try {
            email.send(message);
        } catch (RuntimeException e) {
            // Never log the body (it can contain a reset link) or the recipient's full address
            log.error("Could not deliver e-mail \"{}\" ({} mode): {}", message.subject(), email.mode(), e.getMessage());
        }
    }
}
