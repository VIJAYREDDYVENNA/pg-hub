-- Super Admin-controlled subscription plans, per-owner subscriptions, usage-limit overrides and history.
-- Plan values below are editable defaults (Super Admin > Subscription plans); the application never hardcodes them.

CREATE TABLE subscription_plans (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                  VARCHAR(30)   NOT NULL,
    name                  VARCHAR(80)   NOT NULL,
    description           VARCHAR(500),
    -- NULL price = "custom / contact sales" (Enterprise)
    monthly_price         DECIMAL(12,2),
    annual_price          DECIMAL(12,2),
    currency              VARCHAR(3)    NOT NULL DEFAULT 'INR',
    billing_interval      VARCHAR(10)   NOT NULL DEFAULT 'MONTHLY',
    max_hostels           INT           NOT NULL,
    max_beds_per_hostel   INT           NOT NULL,
    max_managers          INT           NOT NULL,
    trial_days            INT           NOT NULL DEFAULT 14,
    grace_days            INT           NOT NULL DEFAULT 7,
    custom_pricing        BOOLEAN       NOT NULL DEFAULT FALSE,
    -- ACTIVE plans can be assigned to owners; INACTIVE are hidden from new assignments; ARCHIVED are history only
    status                VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    sort_order            INT           NOT NULL DEFAULT 0,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT uk_subscription_plans_code UNIQUE (code),
    CONSTRAINT ck_plan_limits CHECK (max_hostels >= 1 AND max_beds_per_hostel >= 1 AND max_managers >= 0
                                     AND trial_days >= 0 AND grace_days >= 0)
) ENGINE = InnoDB;

CREATE TABLE plan_entitlements (
    plan_id       BIGINT      NOT NULL,
    feature_code  VARCHAR(40) NOT NULL,
    enabled       BOOLEAN     NOT NULL,
    PRIMARY KEY (plan_id, feature_code),
    CONSTRAINT fk_plan_entitlements_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id)
) ENGINE = InnoDB;

CREATE TABLE owner_subscriptions (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id                 BIGINT        NOT NULL,
    plan_id                  BIGINT        NOT NULL,
    -- TRIAL, ACTIVE, SUSPENDED, CANCELLED. Expiry/grace/restriction are derived from the dates, never stored.
    status                   VARCHAR(12)   NOT NULL,
    starts_on                DATE          NOT NULL,
    trial_ends_on            DATE,
    -- NULL = open-ended (no expiry)
    period_ends_on           DATE,
    grace_days               INT           NOT NULL,
    -- Owner-specific limit overrides; NULL = use the plan's value
    custom_max_hostels       INT,
    custom_max_beds_per_hostel INT,
    custom_max_managers      INT,
    custom_monthly_price     DECIMAL(12,2),
    -- Billing is tracked separately from subscription status: NOT_REQUIRED, UNPAID, PAID
    billing_status           VARCHAR(14)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    CONSTRAINT uk_owner_subscriptions_owner UNIQUE (owner_id),
    CONSTRAINT fk_owner_subscriptions_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_owner_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT ck_owner_sub_custom CHECK ((custom_max_hostels IS NULL OR custom_max_hostels >= 1)
        AND (custom_max_beds_per_hostel IS NULL OR custom_max_beds_per_hostel >= 1)
        AND (custom_max_managers IS NULL OR custom_max_managers >= 0))
) ENGINE = InnoDB;
CREATE INDEX idx_owner_subscriptions_plan ON owner_subscriptions (plan_id);

-- Append-only history of every plan/limit/date/status change and who made it
CREATE TABLE subscription_events (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    details          VARCHAR(1000),
    actor_user_id    BIGINT,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT fk_subscription_events_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;
CREATE INDEX idx_subscription_events_owner ON subscription_events (owner_id, created_at);

-- Subscription fees recorded manually by the Super Admin (separate from residents' rent collection)
CREATE TABLE subscription_payments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT        NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    paid_on        DATE          NOT NULL,
    method         VARCHAR(20)   NOT NULL,
    reference      VARCHAR(100),
    notes          VARCHAR(500),
    extended_to    DATE,
    recorded_by    BIGINT,
    created_at     DATETIME(6)   NOT NULL,
    CONSTRAINT fk_subscription_payments_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT ck_subscription_payment_amount CHECK (amount > 0)
) ENGINE = InnoDB;
CREATE INDEX idx_subscription_payments_owner ON subscription_payments (owner_id, paid_on);

-- Initial plan catalogue (editable by Super Admin)
INSERT INTO subscription_plans (code, name, description, monthly_price, annual_price, currency, max_hostels,
    max_beds_per_hostel, max_managers, trial_days, grace_days, custom_pricing, status, sort_order, created_at, updated_at) VALUES
 ('STARTER',    'Starter',    'One hostel for small PG owners',            199.00,  NULL, 'INR', 1,  50,   1,   14, 7, FALSE, 'ACTIVE', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('GROWTH',     'Growth',     'A few hostels with a small team',           399.00,  NULL, 'INR', 3,  100,  5,   14, 7, FALSE, 'ACTIVE', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('BUSINESS',   'Business',   'Multi-property operators',                  799.00,  NULL, 'INR', 10, 200,  15,  14, 7, FALSE, 'ACTIVE', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 ('ENTERPRISE', 'Enterprise', 'Custom price and limits agreed per owner',  NULL,    NULL, 'INR', 50, 1000, 100, 14, 7, TRUE,  'ACTIVE', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Feature entitlements. Features that already exist stay on for every plan so no current behaviour changes;
-- the advanced ones are placeholders gated by plan for when those modules are built.
INSERT INTO plan_entitlements (plan_id, feature_code, enabled)
SELECT p.id, f.code,
       CASE WHEN f.always_on THEN TRUE
            WHEN f.code IN ('ADVANCED_REPORTS', 'BULK_IMPORT_EXPORT') THEN p.code IN ('GROWTH', 'BUSINESS', 'ENTERPRISE')
            ELSE p.code IN ('BUSINESS', 'ENTERPRISE') END
FROM subscription_plans p
JOIN (SELECT 'ONLINE_PAYMENTS' code, TRUE always_on UNION ALL SELECT 'WHATSAPP_MESSAGING', TRUE
      UNION ALL SELECT 'EXPENSE_TRACKING', TRUE UNION ALL SELECT 'ADVANCED_REPORTS', FALSE
      UNION ALL SELECT 'BULK_IMPORT_EXPORT', FALSE UNION ALL SELECT 'CUSTOM_BRANDING', FALSE
      UNION ALL SELECT 'EXTENDED_AUDIT_RETENTION', FALSE) f;
