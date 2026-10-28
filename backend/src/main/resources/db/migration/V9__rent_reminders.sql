-- Automated WhatsApp rent reminders.
--  * owner_settings.auto_reminders_enabled: an owner must switch automation on; it is OFF for every existing owner, so
--    applying this migration never starts messaging anybody.
--  * rent_reminders: exactly one row per (invoice, stage). The unique key is what makes a reminder impossible to send
--    twice, whatever runs the job (two instances, a retry, a manual re-run).

ALTER TABLE owner_settings
    ADD COLUMN auto_reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER reminder_days_before;

CREATE TABLE rent_reminders (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id              BIGINT       NOT NULL,
    invoice_id            BIGINT       NOT NULL,
    -- BEFORE, DUE, OVERDUE_1, OVERDUE_2 ...
    stage                 VARCHAR(20)  NOT NULL,
    scheduled_for         DATE         NOT NULL,
    whatsapp_message_id   BIGINT,
    created_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uk_rent_reminders_invoice_stage UNIQUE (invoice_id, stage),
    CONSTRAINT fk_rent_reminders_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_rent_reminders_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_rent_reminders_message FOREIGN KEY (whatsapp_message_id) REFERENCES whatsapp_messages (id)
) ENGINE = InnoDB;
CREATE INDEX idx_rent_reminders_owner_created ON rent_reminders (owner_id, created_at);
