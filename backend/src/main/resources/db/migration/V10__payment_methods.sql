-- How an owner accepts rent: cash, UPI (ID and an optional static QR picture), bank transfer instructions, and whether
-- online (gateway) payment links are allowed. One default row per owner (hostel_id NULL) plus optional per-hostel overrides.
-- Nothing here verifies a payment: a UPI ID, QR image or bank account only tells residents where to send money; confirming
-- that money arrived is still done by staff (or by the payment gateway for online payments).

CREATE TABLE payment_method_settings (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id                 BIGINT       NOT NULL,
    hostel_id                BIGINT,
    -- 0 for the owner default, so (owner, hostel) is unique even though MySQL allows many NULLs in a unique index
    hostel_key               BIGINT GENERATED ALWAYS AS (COALESCE(hostel_id, 0)) STORED,
    accepts_cash             BOOLEAN      NOT NULL DEFAULT TRUE,
    accepts_upi              BOOLEAN      NOT NULL DEFAULT FALSE,
    upi_id                   VARCHAR(100),
    upi_payee_name           VARCHAR(100),
    upi_qr_file_id           BIGINT,
    accepts_bank_transfer    BOOLEAN      NOT NULL DEFAULT FALSE,
    bank_account_holder      VARCHAR(150),
    bank_name                VARCHAR(100),
    bank_branch              VARCHAR(100),
    bank_ifsc                VARCHAR(11),
    -- AES-256-GCM (iv || ciphertext || tag), bound to the owner; never stored in plain text
    bank_account_number_enc  VARBINARY(255),
    bank_account_last4       VARCHAR(4),
    accepts_online           BOOLEAN      NOT NULL DEFAULT TRUE,
    instructions             VARCHAR(1000),
    updated_by               BIGINT,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    CONSTRAINT uk_payment_method_scope UNIQUE (owner_id, hostel_key),
    CONSTRAINT fk_pms_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_pms_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_pms_qr_file FOREIGN KEY (upi_qr_file_id) REFERENCES file_attachments (id),
    CONSTRAINT fk_pms_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE = InnoDB;
