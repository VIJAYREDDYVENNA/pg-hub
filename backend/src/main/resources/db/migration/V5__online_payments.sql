-- Online (payment gateway) collection. Verified gateway payments are converted into the existing
-- payments / payment_allocations rows by PaymentService; these tables only track the gateway side:
-- one row per checkout attempt, tokenised pay links for residents, and processed webhook events.

CREATE TABLE payment_attempts (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id             BIGINT        NOT NULL,
    hostel_id            BIGINT        NOT NULL,
    invoice_id           BIGINT        NOT NULL,
    resident_id          BIGINT        NOT NULL,
    provider             VARCHAR(30)   NOT NULL,
    provider_order_id    VARCHAR(100)  NOT NULL,
    -- Set only once a captured payment has been verified and applied
    provider_payment_id  VARCHAR(100),
    amount               DECIMAL(12,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    -- CREATED, PENDING, SUCCEEDED, FAILED, EXPIRED, MISMATCH
    status               VARCHAR(20)   NOT NULL,
    failure_reason       VARCHAR(500),
    method               VARCHAR(40),
    payment_id           BIGINT,
    initiated_by         BIGINT        NOT NULL,
    -- STAFF (signed-in user) or LINK (resident opened a pay link)
    initiated_via        VARCHAR(10)   NOT NULL,
    payment_link_id      BIGINT,
    simulated            BOOLEAN       NOT NULL,
    completed_at         DATETIME(6),
    last_checked_at      DATETIME(6),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT uk_attempt_order UNIQUE (provider, provider_order_id),
    -- A gateway payment can be applied to exactly one attempt, and an attempt yields at most one payment
    CONSTRAINT uk_attempt_provider_payment UNIQUE (provider, provider_payment_id),
    CONSTRAINT uk_attempt_payment UNIQUE (payment_id),
    CONSTRAINT fk_attempt_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_attempt_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_attempt_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_attempt_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_attempt_initiated_by FOREIGN KEY (initiated_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_attempt_invoice ON payment_attempts (invoice_id, status);
CREATE INDEX idx_attempt_open ON payment_attempts (status, created_at);

CREATE TABLE payment_links (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    hostel_id   BIGINT       NOT NULL,
    invoice_id  BIGINT       NOT NULL,
    -- SHA-256 of the random token; the token itself is shown once and never stored
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6),
    created_by  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_payment_links_token UNIQUE (token_hash),
    CONSTRAINT fk_plink_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_plink_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_plink_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_plink_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_plink_invoice ON payment_links (invoice_id);

ALTER TABLE payment_attempts
    ADD CONSTRAINT fk_attempt_link FOREIGN KEY (payment_link_id) REFERENCES payment_links (id);

CREATE TABLE payment_webhook_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider     VARCHAR(30)  NOT NULL,
    event_id     VARCHAR(120) NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    received_at  DATETIME(6)  NOT NULL,
    -- Idempotency key: a redelivered event hits this constraint and is acknowledged without being re-applied
    CONSTRAINT uk_webhook_event UNIQUE (provider, event_id)
) ENGINE = InnoDB;
