-- Optional client-supplied key that makes "record payment" safe to retry: a double click, a flaky network retry or a
-- second browser tab sending the same key can never create a second receipt. NULL keys (older clients, system
-- generated payments) are unconstrained; MySQL allows any number of NULLs in a unique index.
ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER transaction_reference;
CREATE UNIQUE INDEX uk_payments_idempotency ON payments (owner_id, idempotency_key);
