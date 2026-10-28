-- =====================================================================
-- Hostel Management System - initial schema
-- All timestamps are stored in UTC (DATETIME(6)); business dates as DATE.
-- Money columns use DECIMAL(12,2).
-- Ownership path: every hostel-scoped table reaches owners via hostels.owner_id.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tenancy, users and access control
-- ---------------------------------------------------------------------
CREATE TABLE owners (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    email        VARCHAR(150) NOT NULL,
    phone        VARCHAR(20),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_owners_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE owner_settings (
    owner_id               BIGINT PRIMARY KEY,
    vacancy_warning_days   INT           NOT NULL DEFAULT 7,
    payment_grace_days     INT           NOT NULL DEFAULT 5,
    late_fee_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    reminder_days_before   INT           NOT NULL DEFAULT 2,
    currency_code          VARCHAR(3)    NOT NULL DEFAULT 'INR',
    updated_at             DATETIME(6)   NOT NULL,
    CONSTRAINT fk_owner_settings_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;

CREATE TABLE users (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id               BIGINT       NOT NULL,
    full_name              VARCHAR(150) NOT NULL,
    email                  VARCHAR(150) NOT NULL,
    phone                  VARCHAR(20),
    password_hash          VARCHAR(100) NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           DATETIME(6),
    last_login_at          DATETIME(6),
    password_changed_at    DATETIME(6),
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;
CREATE INDEX idx_users_owner ON users (owner_id);

CREATE TABLE roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE permissions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    category    VARCHAR(40)  NOT NULL,
    CONSTRAINT uk_permissions_code UNIQUE (code)
) ENGINE = InnoDB;

-- Default permission set for each role (template used when creating staff users)
CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL,
    permission_id  BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_perm_perm FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

-- Effective, owner-configurable permissions of a (non-owner) user
CREATE TABLE user_permissions (
    user_id        BIGINT NOT NULL,
    permission_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_perm_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_perm_perm FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6),
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE password_reset_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_pwd_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

-- Per-owner numbering for invoices / receipts
CREATE TABLE number_sequences (
    owner_id    BIGINT      NOT NULL,
    seq_name    VARCHAR(30) NOT NULL,
    seq_year    INT         NOT NULL,
    next_value  BIGINT      NOT NULL,
    PRIMARY KEY (owner_id, seq_name, seq_year),
    CONSTRAINT fk_number_seq_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Property structure: hostels > buildings > floors > rooms > beds
-- ---------------------------------------------------------------------
CREATE TABLE hostels (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    name             VARCHAR(150) NOT NULL,
    hostel_type      VARCHAR(20)  NOT NULL,
    address          VARCHAR(255) NOT NULL,
    city             VARCHAR(80)  NOT NULL,
    state            VARCHAR(80)  NOT NULL,
    pin_code         VARCHAR(10)  NOT NULL,
    contact_number   VARCHAR(20),
    email            VARCHAR(150),
    manager_user_id  BIGINT,
    image_file_id    BIGINT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at       DATETIME(6),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT fk_hostels_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_hostels_manager FOREIGN KEY (manager_user_id) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_hostels_owner ON hostels (owner_id, deleted_at);

CREATE TABLE user_hostel_access (
    user_id    BIGINT NOT NULL,
    hostel_id  BIGINT NOT NULL,
    PRIMARY KEY (user_id, hostel_id),
    CONSTRAINT fk_uha_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_uha_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id)
) ENGINE = InnoDB;

CREATE TABLE buildings (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id    BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    sort_order   INT          NOT NULL DEFAULT 0,
    deleted_at   DATETIME(6),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_buildings_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id)
) ENGINE = InnoDB;
CREATE INDEX idx_buildings_hostel ON buildings (hostel_id, deleted_at);

CREATE TABLE floors (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id  BIGINT       NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    level_number INT          NOT NULL,
    description  VARCHAR(500),
    sort_order   INT          NOT NULL DEFAULT 0,
    deleted_at   DATETIME(6),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_floors_building FOREIGN KEY (building_id) REFERENCES buildings (id)
) ENGINE = InnoDB;
CREATE INDEX idx_floors_building ON floors (building_id, deleted_at);

CREATE TABLE rooms (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id             BIGINT        NOT NULL,
    floor_id              BIGINT        NOT NULL,
    room_number           VARCHAR(20)   NOT NULL,
    room_type             VARCHAR(30)   NOT NULL,
    monthly_rent_per_bed  DECIMAL(12,2) NOT NULL,
    security_deposit      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    amenities             VARCHAR(500),
    status                VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    deleted_at            DATETIME(6),
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT fk_rooms_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_rooms_floor FOREIGN KEY (floor_id) REFERENCES floors (id)
) ENGINE = InnoDB;
CREATE INDEX idx_rooms_floor ON rooms (floor_id, deleted_at);
CREATE INDEX idx_rooms_hostel ON rooms (hostel_id, deleted_at);
CREATE INDEX idx_rooms_number ON rooms (room_number);

CREATE TABLE beds (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id       BIGINT        NOT NULL,
    bed_label     VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    rent_override DECIMAL(12,2),
    deleted_at    DATETIME(6),
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_beds_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE = InnoDB;
CREATE INDEX idx_beds_room ON beds (room_id, deleted_at);

-- ---------------------------------------------------------------------
-- Files (resident photos, documents, maintenance images, hostel images)
-- ---------------------------------------------------------------------
CREATE TABLE file_attachments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT       NOT NULL,
    hostel_id      BIGINT,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      BIGINT,
    category       VARCHAR(40),
    original_name  VARCHAR(255) NOT NULL,
    stored_name    VARCHAR(100) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_by    BIGINT,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uk_files_stored UNIQUE (stored_name),
    CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_files_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_files_entity ON file_attachments (entity_type, entity_id);

ALTER TABLE hostels
    ADD CONSTRAINT fk_hostels_image FOREIGN KEY (image_file_id) REFERENCES file_attachments (id);

-- ---------------------------------------------------------------------
-- Residents, admissions and bed allocations
-- ---------------------------------------------------------------------
CREATE TABLE residents (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id                 BIGINT       NOT NULL,
    hostel_id                BIGINT       NOT NULL,
    full_name                VARCHAR(150) NOT NULL,
    photo_file_id            BIGINT,
    mobile                   VARCHAR(20)  NOT NULL,
    alternate_mobile         VARCHAR(20),
    email                    VARCHAR(150),
    date_of_birth            DATE,
    gender                   VARCHAR(20),
    address                  VARCHAR(500),
    emergency_contact_name   VARCHAR(150),
    emergency_contact_phone  VARCHAR(20),
    id_document_type         VARCHAR(40),
    id_document_number       VARCHAR(60),
    guardian_name            VARCHAR(150),
    guardian_phone           VARCHAR(20),
    guardian_relation        VARCHAR(40),
    occupation               VARCHAR(100),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
    whatsapp_opt_in          BOOLEAN      NOT NULL DEFAULT FALSE,
    whatsapp_opt_in_at       DATETIME(6),
    notes                    VARCHAR(1000),
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    CONSTRAINT fk_residents_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_residents_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_residents_photo FOREIGN KEY (photo_file_id) REFERENCES file_attachments (id)
) ENGINE = InnoDB;
CREATE INDEX idx_residents_hostel_status ON residents (hostel_id, status);
CREATE INDEX idx_residents_name ON residents (full_name);
CREATE INDEX idx_residents_mobile ON residents (mobile);

CREATE TABLE admissions (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    resident_id               BIGINT        NOT NULL,
    hostel_id                 BIGINT        NOT NULL,
    admission_date            DATE          NOT NULL,
    expected_checkout_date    DATE,
    checkout_confirmed        BOOLEAN       NOT NULL DEFAULT FALSE,
    actual_checkout_date      DATE,
    monthly_rent              DECIMAL(12,2) NOT NULL,
    security_deposit          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    billing_cycle             VARCHAR(20)   NOT NULL DEFAULT 'MONTHLY',
    cycle_months              INT           NOT NULL DEFAULT 1,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    checkout_notes            VARCHAR(1000),
    notes                     VARCHAR(1000),
    booking_id                BIGINT,
    created_by                BIGINT,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    -- A resident can have at most one ACTIVE admission
    active_resident_id        BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN resident_id END) STORED,
    CONSTRAINT uk_admissions_active_resident UNIQUE (active_resident_id),
    CONSTRAINT fk_admissions_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_admissions_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_admissions_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_admissions_hostel_status ON admissions (hostel_id, status);
CREATE INDEX idx_admissions_checkout ON admissions (status, expected_checkout_date);

CREATE TABLE bed_allocations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    admission_id    BIGINT       NOT NULL,
    bed_id          BIGINT       NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE,
    end_reason      VARCHAR(20),
    notes           VARCHAR(500),
    created_by      BIGINT,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    -- Database-level guarantee: one active allocation per bed and per admission
    active_bed_id        BIGINT GENERATED ALWAYS AS (CASE WHEN end_date IS NULL THEN bed_id END) STORED,
    active_admission_id  BIGINT GENERATED ALWAYS AS (CASE WHEN end_date IS NULL THEN admission_id END) STORED,
    CONSTRAINT uk_alloc_active_bed UNIQUE (active_bed_id),
    CONSTRAINT uk_alloc_active_admission UNIQUE (active_admission_id),
    CONSTRAINT fk_alloc_admission FOREIGN KEY (admission_id) REFERENCES admissions (id),
    CONSTRAINT fk_alloc_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    CONSTRAINT fk_alloc_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_alloc_bed ON bed_allocations (bed_id, end_date);

-- ---------------------------------------------------------------------
-- Enquiries and bookings (reservations)
-- ---------------------------------------------------------------------
CREATE TABLE enquiries (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id                 BIGINT        NOT NULL,
    hostel_id                BIGINT,
    full_name                VARCHAR(150)  NOT NULL,
    mobile                   VARCHAR(20)   NOT NULL,
    email                    VARCHAR(150),
    gender                   VARCHAR(20),
    preferred_room_type      VARCHAR(30),
    expected_admission_date  DATE,
    budget                   DECIMAL(12,2),
    source                   VARCHAR(40),
    status                   VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    next_follow_up_date      DATE,
    notes                    VARCHAR(1000),
    converted_resident_id    BIGINT,
    whatsapp_opt_in          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by               BIGINT,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    CONSTRAINT fk_enquiries_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_enquiries_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_enquiries_resident FOREIGN KEY (converted_resident_id) REFERENCES residents (id)
) ENGINE = InnoDB;
CREATE INDEX idx_enquiries_owner_status ON enquiries (owner_id, status);
CREATE INDEX idx_enquiries_follow_up ON enquiries (next_follow_up_date);

CREATE TABLE bookings (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id                BIGINT        NOT NULL,
    bed_id                   BIGINT        NOT NULL,
    enquiry_id               BIGINT,
    resident_id              BIGINT,
    guest_name               VARCHAR(150)  NOT NULL,
    guest_mobile             VARCHAR(20)   NOT NULL,
    expected_admission_date  DATE          NOT NULL,
    hold_until               DATE,
    advance_amount           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'RESERVED',
    notes                    VARCHAR(1000),
    created_by               BIGINT,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    -- At most one live reservation per bed
    reserved_bed_id          BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'RESERVED' THEN bed_id END) STORED,
    CONSTRAINT uk_bookings_reserved_bed UNIQUE (reserved_bed_id),
    CONSTRAINT fk_bookings_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_bookings_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    CONSTRAINT fk_bookings_enquiry FOREIGN KEY (enquiry_id) REFERENCES enquiries (id),
    CONSTRAINT fk_bookings_resident FOREIGN KEY (resident_id) REFERENCES residents (id)
) ENGINE = InnoDB;
CREATE INDEX idx_bookings_hostel_status ON bookings (hostel_id, status);

ALTER TABLE admissions
    ADD CONSTRAINT fk_admissions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id);

-- ---------------------------------------------------------------------
-- Billing and payments
-- ---------------------------------------------------------------------
CREATE TABLE invoices (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT        NOT NULL,
    hostel_id        BIGINT        NOT NULL,
    resident_id      BIGINT        NOT NULL,
    admission_id     BIGINT,
    invoice_number   VARCHAR(40)   NOT NULL,
    invoice_type     VARCHAR(20)   NOT NULL,
    period_start     DATE,
    period_end       DATE,
    issue_date       DATE          NOT NULL,
    due_date         DATE          NOT NULL,
    subtotal         DECIMAL(12,2) NOT NULL,
    discount_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    late_fee_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount     DECIMAL(12,2) NOT NULL,
    amount_paid      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status           VARCHAR(20)   NOT NULL,
    notes            VARCHAR(500),
    created_by       BIGINT,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT uk_invoices_number UNIQUE (owner_id, invoice_number),
    -- Prevents generating the same rent period twice for an admission
    CONSTRAINT uk_invoices_period UNIQUE (admission_id, invoice_type, period_start),
    CONSTRAINT fk_invoices_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_invoices_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_invoices_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_invoices_admission FOREIGN KEY (admission_id) REFERENCES admissions (id)
) ENGINE = InnoDB;
CREATE INDEX idx_invoices_hostel_due ON invoices (hostel_id, status, due_date);
CREATE INDEX idx_invoices_resident ON invoices (resident_id, status);

CREATE TABLE invoice_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id   BIGINT        NOT NULL,
    item_type    VARCHAR(20)   NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE TABLE payments (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id               BIGINT        NOT NULL,
    hostel_id              BIGINT        NOT NULL,
    resident_id            BIGINT        NOT NULL,
    admission_id           BIGINT,
    receipt_number         VARCHAR(40)   NOT NULL,
    amount                 DECIMAL(12,2) NOT NULL,
    unallocated_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_date           DATE          NOT NULL,
    method                 VARCHAR(20)   NOT NULL,
    transaction_reference  VARCHAR(100),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',
    notes                  VARCHAR(500),
    recorded_by            BIGINT        NOT NULL,
    reversed_at            DATETIME(6),
    reversed_by            BIGINT,
    reversal_reason        VARCHAR(500),
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    CONSTRAINT uk_payments_receipt UNIQUE (owner_id, receipt_number),
    CONSTRAINT fk_payments_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_payments_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_payments_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_payments_admission FOREIGN KEY (admission_id) REFERENCES admissions (id),
    CONSTRAINT fk_payments_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT fk_payments_reversed_by FOREIGN KEY (reversed_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_payments_hostel_date ON payments (hostel_id, payment_date);
CREATE INDEX idx_payments_resident ON payments (resident_id);

CREATE TABLE payment_allocations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id  BIGINT        NOT NULL,
    invoice_id  BIGINT        NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT fk_pay_alloc_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_pay_alloc_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE TABLE deposits (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id        BIGINT        NOT NULL,
    resident_id      BIGINT        NOT NULL,
    admission_id     BIGINT        NOT NULL,
    invoice_id       BIGINT,
    amount_required  DECIMAL(12,2) NOT NULL,
    amount_received  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    amount_refunded  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    amount_adjusted  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status           VARCHAR(25)   NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT uk_deposits_admission UNIQUE (admission_id),
    CONSTRAINT fk_deposits_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_deposits_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_deposits_admission FOREIGN KEY (admission_id) REFERENCES admissions (id),
    CONSTRAINT fk_deposits_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB;

CREATE TABLE refunds (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id               BIGINT        NOT NULL,
    hostel_id              BIGINT        NOT NULL,
    resident_id            BIGINT        NOT NULL,
    deposit_id             BIGINT,
    refund_type            VARCHAR(20)   NOT NULL,
    amount                 DECIMAL(12,2) NOT NULL,
    refund_date            DATE          NOT NULL,
    method                 VARCHAR(20)   NOT NULL,
    transaction_reference  VARCHAR(100),
    reason                 VARCHAR(500),
    recorded_by            BIGINT        NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    CONSTRAINT fk_refunds_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_refunds_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_refunds_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_refunds_deposit FOREIGN KEY (deposit_id) REFERENCES deposits (id),
    CONSTRAINT fk_refunds_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_refunds_resident ON refunds (resident_id);

CREATE TABLE expenses (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id        BIGINT        NOT NULL,
    category         VARCHAR(40)   NOT NULL,
    description      VARCHAR(500)  NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    expense_date     DATE          NOT NULL,
    payment_method   VARCHAR(20)   NOT NULL,
    vendor           VARCHAR(150),
    reference        VARCHAR(100),
    maintenance_request_id BIGINT,
    recorded_by      BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT fk_expenses_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_expenses_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_expenses_hostel_date ON expenses (hostel_id, expense_date);

-- ---------------------------------------------------------------------
-- Staff and maintenance
-- ---------------------------------------------------------------------
CREATE TABLE staff (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id      BIGINT        NOT NULL,
    hostel_id     BIGINT,
    user_id       BIGINT,
    full_name     VARCHAR(150)  NOT NULL,
    mobile        VARCHAR(20)   NOT NULL,
    designation   VARCHAR(60)   NOT NULL,
    monthly_salary DECIMAL(12,2),
    joined_on     DATE,
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT uk_staff_user UNIQUE (user_id),
    CONSTRAINT fk_staff_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_staff_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_staff_owner ON staff (owner_id, status);

CREATE TABLE maintenance_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostel_id           BIGINT        NOT NULL,
    room_id             BIGINT,
    bed_id              BIGINT,
    title               VARCHAR(150)  NOT NULL,
    description         VARCHAR(2000),
    category            VARCHAR(40),
    priority            VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    blocks_availability BOOLEAN       NOT NULL DEFAULT FALSE,
    estimated_cost      DECIMAL(12,2),
    actual_cost         DECIMAL(12,2),
    reported_by         BIGINT,
    assigned_staff_id   BIGINT,
    resolved_at         DATETIME(6),
    resolution_notes    VARCHAR(1000),
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_maint_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_maint_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_maint_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    CONSTRAINT fk_maint_reported_by FOREIGN KEY (reported_by) REFERENCES users (id),
    CONSTRAINT fk_maint_staff FOREIGN KEY (assigned_staff_id) REFERENCES staff (id)
) ENGINE = InnoDB;
CREATE INDEX idx_maint_hostel_status ON maintenance_requests (hostel_id, status);
CREATE INDEX idx_maint_room ON maintenance_requests (room_id, status);
CREATE INDEX idx_maint_bed ON maintenance_requests (bed_id, status);

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_maint FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_requests (id);

CREATE TABLE maintenance_assignments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id      BIGINT       NOT NULL,
    staff_id        BIGINT,
    status_from     VARCHAR(20),
    status_to       VARCHAR(20)  NOT NULL,
    note            VARCHAR(1000),
    changed_by      BIGINT,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_maint_assign_request FOREIGN KEY (request_id) REFERENCES maintenance_requests (id),
    CONSTRAINT fk_maint_assign_staff FOREIGN KEY (staff_id) REFERENCES staff (id),
    CONSTRAINT fk_maint_assign_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Notifications, WhatsApp and announcements
-- ---------------------------------------------------------------------
CREATE TABLE notification_templates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id            BIGINT,
    code                VARCHAR(40)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    channel             VARCHAR(20)   NOT NULL,
    body                VARCHAR(1500) NOT NULL,
    provider_template   VARCHAR(100),
    language_code       VARCHAR(10)   NOT NULL DEFAULT 'en',
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    -- owner_id NULL = system default; an owner may override a code with its own row
    CONSTRAINT uk_templates_owner_code UNIQUE (owner_id, code, channel),
    CONSTRAINT fk_templates_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;

CREATE TABLE notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id     BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    hostel_id    BIGINT,
    type         VARCHAR(40)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    link         VARCHAR(255),
    dedupe_key   VARCHAR(120),
    read_at      DATETIME(6),
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_notifications_dedupe UNIQUE (user_id, dedupe_key),
    CONSTRAINT fk_notifications_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id)
) ENGINE = InnoDB;
CREATE INDEX idx_notifications_user ON notifications (user_id, read_at, created_at);

CREATE TABLE notification_preferences (
    user_id     BIGINT      NOT NULL,
    type        VARCHAR(40) NOT NULL,
    in_app      BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, type),
    CONSTRAINT fk_notif_pref_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE whatsapp_messages (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id             BIGINT        NOT NULL,
    hostel_id            BIGINT,
    resident_id          BIGINT,
    to_phone             VARCHAR(20)   NOT NULL,
    template_code        VARCHAR(40),
    template_params      VARCHAR(1500),
    body                 VARCHAR(2000) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    simulated            BOOLEAN       NOT NULL,
    provider_message_id  VARCHAR(120),
    error_message        VARCHAR(500),
    attempts             INT           NOT NULL DEFAULT 0,
    next_attempt_at      DATETIME(6),
    related_type         VARCHAR(40),
    related_id           BIGINT,
    sent_by              BIGINT,
    sent_at              DATETIME(6),
    delivered_at         DATETIME(6),
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT fk_wa_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_wa_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_wa_resident FOREIGN KEY (resident_id) REFERENCES residents (id),
    CONSTRAINT fk_wa_sent_by FOREIGN KEY (sent_by) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX idx_wa_owner_created ON whatsapp_messages (owner_id, created_at);
CREATE INDEX idx_wa_retry ON whatsapp_messages (status, next_attempt_at);
CREATE INDEX idx_wa_provider_id ON whatsapp_messages (provider_message_id);

CREATE TABLE announcements (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id      BIGINT        NOT NULL,
    hostel_id     BIGINT,
    title         VARCHAR(200)  NOT NULL,
    body          VARCHAR(2000) NOT NULL,
    send_whatsapp BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by    BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_announcements_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_announcements_hostel FOREIGN KEY (hostel_id) REFERENCES hostels (id),
    CONSTRAINT fk_announcements_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Audit trail
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id     BIGINT,
    user_id      BIGINT,
    action       VARCHAR(60)   NOT NULL,
    entity_type  VARCHAR(40)   NOT NULL,
    entity_id    BIGINT,
    details      VARCHAR(2000),
    ip_address   VARCHAR(45),
    created_at   DATETIME(6)   NOT NULL
) ENGINE = InnoDB;
CREATE INDEX idx_audit_owner_created ON audit_logs (owner_id, created_at);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
