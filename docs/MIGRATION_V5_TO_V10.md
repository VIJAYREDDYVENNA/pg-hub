# Migration review: V5 - V10 (main database `hostel_management_db`)

**Status: NOT applied to `hostel_management_db`.** It is at Flyway version 4. Nothing here changes until you approve.
(`hostel_management_test`, the throw-away test schema, already has V5-V10.)

Files: `backend/src/main/resources/db/migration/V5__online_payments.sql`, `V6__notification_retention_index.sql`,
`V7__subscriptions.sql`, `V8__payment_idempotency.sql`, `V9__rent_reminders.sql`, `V10__payment_methods.sql` (V9 and V10 were added by Phase 4;
their review is in section 8).
Sections 1-7 below were written for V5-V8 and still hold; section 8 covers V9-V10 and the combined rehearsal.

## 1. Verdict

* **Order:** strictly V5, V6, V7, V8. They are independent of each other: V5 references only V1 tables (and its own new
  `payment_links`), V6 only `notifications`, V7 only `owners`, and V8 only `payments`. Flyway runs them in version order;
  `baseline-on-migrate=false` and `ddl-auto=validate` stay as they are.
* **Safe for the existing V4 database:** every statement is additive (new tables, one new nullable column, two new indexes,
  seed rows). Nothing drops, renames, rewrites or deletes existing data. The largest table touched is `payments` (730 rows)
  and `notifications` (2,963 rows); both operations are near-instant.
* **Rehearsed on a real copy** (section 7): a backup of the main database was restored to a scratch schema and the
  application was started against it. All four migrations applied in about 3 seconds, schema validation passed, row counts were
  identical, and every owner was grandfathered. The scratch schema was then dropped.
* **Not reversible by Flyway** (Community Flyway has no "undo", and MySQL commits DDL immediately). The rollback path is
  restoring the backup (section 5); a manual SQL rollback is given as a last resort (section 4).

## 2. Every schema and data change

### V5 - online payments (gateway side of rent collection)

| Object | Change | Notes |
|---|---|---|
| `payment_attempts` | NEW table | One row per gateway checkout. FKs to owners, hostels, invoices, residents, payments, users, payment_links. Unique `(provider, provider_order_id)`, unique `(provider, provider_payment_id)`, unique `payment_id`. Indexes `(invoice_id, status)`, `(status, created_at)`. |
| `payment_links` | NEW table | Pay-link tokens (only the SHA-256 hash is stored). Unique `token_hash`. |
| `payment_attempts.fk_attempt_link` | NEW FK (added after `payment_links` exists) | |
| `payment_webhook_events` | NEW table | Webhook idempotency ledger, unique `(provider, event_id)`. |

No existing table is altered. No data is inserted.

### V6 - notification retention index

| Object | Change |
|---|---|
| `notifications` | NEW index `idx_notifications_created (created_at)` |

Read-only to data; builds in well under a second for 2,963 rows. The index serves the daily notification cleanup, which is
application code, not part of the migration. Checked against the live database (read-only query on 2026-10-27): the oldest
notification was created 2026-10-01 18:07 UTC and **0 of 2,963 are 30 days old**, so the first start after migration deletes
nothing. The first notifications become eligible on 2026-10-31, and the cleanup then removes them at its 03:30 run. If you want a
longer history, set `NOTIFICATION_RETENTION_DAYS` before starting.

### V7 - subscriptions

| Object | Change | Notes |
|---|---|---|
| `subscription_plans` | NEW table + CHECK constraints | Plan catalogue. |
| `plan_entitlements` | NEW table | Feature flags per plan; FK to plans. |
| `owner_subscriptions` | NEW table | One row per owner (unique `owner_id`), FKs to owners and plans, CHECK constraints on overrides. |
| `subscription_events` | NEW table | Append-only history. |
| `subscription_payments` | NEW table | Manually recorded subscription fees. |
| **Seed rows** | INSERT 4 plans + 28 entitlement rows | STARTER 199 / 1 hostel / 50 beds / 1 manager; GROWTH 399 / 3 / 100 / 5; BUSINESS 799 / 10 / 200 / 15; ENTERPRISE custom price, 50 / 1000 / 100. Trial 14 days, grace 7 days. All editable in Super Admin. |

No existing table is altered. The seeded values are the only data the migration writes.

### V8 - payment idempotency key

| Object | Change | Notes |
|---|---|---|
| `payments.idempotency_key` | NEW nullable `VARCHAR(64)` column | Existing 730 payments get `NULL`. |
| `payments` | NEW unique index `uk_payments_idempotency (owner_id, idempotency_key)` | MySQL allows any number of NULLs in a unique index, so existing rows cannot conflict. |

## 3. How existing owners receive Enterprise subscriptions (not in SQL)

The migrations do **not** create subscriptions for existing owners. On the first start after migration the application runs
`SubscriptionBackfill` (an `ApplicationRunner`, idempotent). For every owner without a subscription it creates, in one
transaction per owner:

* plan `ENTERPRISE`, status `ACTIVE`, start date = the day of that first start, **no expiry** (`period_ends_on` NULL),
  grace days from the plan, billing status `NOT_REQUIRED`;
* a **custom limit only where the owner's current usage already exceeds the Enterprise value**
  (`custom_max_hostels` if hostels > 50; `custom_max_beds_per_hostel` if the largest hostel > 1000;
  `custom_max_managers` if managers > 100). Otherwise the owner simply follows the plan;
* a `GRANDFATHERED` row in `subscription_events` recording the usage seen, and a `SUBSCRIPTION_CREATED` audit entry.

How usage is calculated (the same queries enforcement uses):

* hostels: `hostels` rows of the owner with `deleted_at IS NULL`;
* beds per hostel: `beds` joined to `rooms` for that hostel where `beds.deleted_at IS NULL` and `rooms.deleted_at IS NULL`
  (inactive and maintenance beds count); the owner's figure is the **largest single hostel**;
* managers: `users` of the owner with `enabled = TRUE` that do **not** hold the OWNER role (managers, wardens, staff).

Applied to your current data (from the rehearsal): owner 1 = 3 hostels, 3 managers, largest hostel 107 beds; owner 2 = 1 hostel,
12 beds; owners 3-5 = no hostels. All are within Enterprise, so **no custom limits are created and nobody is restricted**.
Starter would have been exceeded by owner 1 (3 hostels, 107 beds), which is why Enterprise is the grandfathering plan; move owners to
a real plan later from Super Admin > Owners (changing a plan never deletes data).

If the `ENTERPRISE` plan row were missing at that moment the backfill logs an error per owner and those owners would be blocked from
creating hostels, beds and managers (fail-closed, `SUBSCRIPTION_MISSING`) while everything else keeps working. V7 seeds that row, so
this only happens if the plan table was edited by hand.

## 4. Rollback limitations and data-loss risk

* **No automatic rollback.** Flyway Community cannot undo; MySQL DDL is not transactional, so a script that fails midway leaves the
  objects created so far and Flyway records the migration as failed (`success = 0`). You would then restore the backup (below) or
  repair by hand. In the rehearsal nothing failed.
* **Data-loss risk of applying: none.** No `DROP`, `DELETE`, `TRUNCATE`, type change or rewrite of existing data exists in V5-V8.
* **Data-loss risk of rolling back:** a rollback discards anything written to the new objects after the migration (online payment
  attempts, subscriptions, subscription payments/history, idempotency keys). If you roll back after real use, restore the backup
  and re-enter anything that happened since, or export those tables first.
* **Compatibility with the previous application version:** the old code ignores the new tables and the extra nullable column
  (Hibernate `validate` only checks columns it maps), so running the old build against a migrated database also works.
* **Do not edit V5-V8 after they are applied.** Flyway stores checksums; editing an applied file makes start-up fail. (V5 was
  corrected once before it was ever applied anywhere except the recreated test schema.)

Last-resort manual rollback (only if you cannot restore; run in this order, then restart the OLD build):

```sql
ALTER TABLE payments DROP INDEX uk_payments_idempotency, DROP COLUMN idempotency_key;                 -- V8
DROP TABLE subscription_payments, subscription_events, owner_subscriptions, plan_entitlements, subscription_plans;  -- V7
DROP INDEX idx_notifications_created ON notifications;                                                 -- V6
ALTER TABLE payment_attempts DROP FOREIGN KEY fk_attempt_link;                                          -- V5
DROP TABLE payment_webhook_events, payment_attempts, payment_links;
DELETE FROM flyway_schema_history WHERE version IN ('5','6','7','8');
```

## 5. Backup and restore (local MySQL 8.0 on Windows)

The MySQL client tools are installed but not on `PATH`: `C:\Program Files\MySQL\MySQL Server 8.0\bin`. Read the password from
`backend\.env` into the environment for the session instead of typing it on the command line.

**PowerShell**

```powershell
$bin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$env:MYSQL_PWD = '<DB_PASSWORD from backend\.env>'          # session only; never saved to a file
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
& "$bin\mysqldump.exe" -h localhost -P 3306 -u root --single-transaction --routines --triggers --events `
    --set-gtid-purged=OFF --default-character-set=utf8mb4 `
    --result-file="D:\New folder\HotelHub\HostelManagementSystem\backups\hostel_management_db_$stamp.sql" hostel_management_db
```

`--single-transaction` takes a consistent snapshot without locking the tables, so the app may stay running while you dump.
Back up the uploaded files too (resident documents live on disk, not in MySQL): copy `backend\data\uploads` next to the dump.

**Verify the backup before relying on it (restores into a scratch schema, never into the live one)**

```powershell
& "$bin\mysql.exe" -h localhost -u root -e "DROP DATABASE IF EXISTS hostel_restore_check; CREATE DATABASE hostel_restore_check CHARACTER SET utf8mb4;"
cmd /c "`"$bin\mysql.exe`" -h localhost -u root hostel_restore_check < `"<path to the .sql file>`""
# compare, for each table, CHECKSUM TABLE hostel_management_db.<t> against hostel_restore_check.<t>
& "$bin\mysql.exe" -h localhost -u root -e "DROP DATABASE hostel_restore_check;"
```

**Restore into the live database (only if a migration has to be undone; stop the backend first)**

```powershell
& "$bin\mysql.exe" -h localhost -u root -e "DROP DATABASE hostel_management_db; CREATE DATABASE hostel_management_db CHARACTER SET utf8mb4;"
cmd /c "`"$bin\mysql.exe`" -h localhost -u root hostel_management_db < `"<path to the .sql file>`""
```

(`createDatabaseIfNotExist` is intentionally off outside the dev profile, so create the database as shown before restoring.)

Backups made on 2026-10-27: `backups\hostel_management_db_V4_20261027_202214.sql` (1.3 MB, 41 tables, V4 state). It was verified
by restoring it into a scratch schema and comparing `CHECKSUM TABLE` for all 41 tables: **41 identical, 0 different**. The `backups`
folder is listed in `.gitignore` (it contains real data and password hashes).

## 6. Procedure once you approve

1. Stop the backend. Make a fresh backup (section 5) and copy `backend\data\uploads`.
2. Start the backend (`scripts\start-backend.bat`). Flyway applies V5-V8, then the backfill grandfathers the owners
   (log line: `Grandfathered 5 existing owner(s) onto open-ended subscriptions`).
3. Check: `SELECT version, success FROM flyway_schema_history;` shows 5-8 with `success = 1`; Super Admin > Owners shows a Plan column;
   `SELECT COUNT(*) FROM owner_subscriptions;` equals the number of owners.
4. If anything is wrong: stop the backend and restore the backup (section 5).

## 7. Rehearsal results (2026-10-27, against a restored COPY; the live database was not touched)

| Check | Result |
|---|---|
| Backup restored to scratch schema; `CHECKSUM TABLE` over all tables | 41 identical, 0 mismatched |
| Flyway on the copy | `Current version 4` -> V5, V6, V7, V8, "Successfully applied 4 migrations" (about 3 s) |
| Application start-up on the copy (Hibernate schema validation) | started, `/actuator/health` 200 |
| Backfill | 5 owners grandfathered, all `ENTERPRISE`, `ACTIVE`, no expiry, no custom limits |
| Row counts before/after | owners 5, users 12, hostels 4, rooms 67, beds 190, residents 134, invoices 872, payments 730, notifications 2,963: identical. `audit_logs` 1,271 -> 1,276 (the 5 new `SUBSCRIPTION_CREATED` entries). |
| Live `hostel_management_db` afterwards | still at Flyway version 4; scratch schema dropped |

Not covered by the rehearsal: the application's behaviour for each owner after migration in a browser, and rollback by restore (the
restore command itself was exercised only by the verification above, into the scratch schema).

## 8. V9 and V10 (added by Phase 4) and the V5-V10 rehearsal

### V9 - rent reminders

| Object | Change | Notes |
|---|---|---|
| `owner_settings.auto_reminders_enabled` | NEW column `BOOLEAN NOT NULL DEFAULT FALSE` | **Off for every existing owner**, so migrating never starts messaging anyone. |
| `rent_reminders` | NEW table | Unique `(invoice_id, stage)` is the duplicate-send guard. FKs to owners, invoices, whatsapp_messages. |

### V10 - payment methods

| Object | Change | Notes |
|---|---|---|
| `payment_method_settings` | NEW table | Per owner default (`hostel_id` NULL, `hostel_key` = 0) plus optional per-hostel rows; unique `(owner_id, hostel_key)`. Bank account number is stored only as AES-GCM ciphertext (`VARBINARY`) plus last 4 digits. FKs to owners, hostels, file_attachments, users. |

Both are additive and need no backfill: an owner with no `payment_method_settings` row behaves exactly as before (cash and online
accepted, nothing else), and no owner has reminders switched on. Existing rows are untouched. Rollback (last resort, after the V8
statements above, in this order; restoring the backup is still preferred):

```sql
DROP TABLE payment_method_settings;                                          -- V10
DROP TABLE rent_reminders;                                                   -- V9
ALTER TABLE owner_settings DROP COLUMN auto_reminders_enabled;               -- V9
DELETE FROM flyway_schema_history WHERE version IN ('9','10');
```

Rolling back after use discards owners' saved payment details and reminder history. Uploaded UPI QR images live in `data/uploads`
(rows in `file_attachments`); restoring an older dump leaves orphaned files there, which is harmless.

### Rehearsal of V5-V10 (2026-10-27, restored COPY of `backups\hostel_management_db_V4_20261027_202214.sql`; live DB untouched)

Run with `DEV_SEED_ENABLED=false` and `SCHEDULING_ENABLED=false` on port 18091 so no seed data or scheduled job touched the copy.

| Check | Result |
|---|---|
| Flyway | `Successfully applied 6 migrations ... now at version v10` (about 2.3 s); V5-V10 all `success = 1` |
| Start-up with Hibernate `ddl-auto: validate` | OK, `/actuator/health` UP |
| Backfill | `Grandfathered 5 existing owner(s)`; 5 `owner_subscriptions` rows for 5 owners |
| Reminders | owners with `auto_reminders_enabled` = 0 of 5; `rent_reminders` 0 rows |
| `payment_method_settings` | 0 rows (defaults apply) |
| Row counts | owners 5, residents 134, invoices 872, payments 730, notifications 2,963: identical to V4 |
| Live `hostel_management_db` afterwards | still Flyway version 4; scratch schema dropped |

### Procedure once you approve (replaces section 6 step 2-3 numbers)

1. Stop the backend; fresh backup (section 5) and copy `backend\data\uploads`.
2. Set `DATA_ENCRYPTION_KEY` in `backend\.env` if owners should store bank details (`docs/PAYMENT_METHODS.md`); optionally the `MAIL_*` variables (`docs/EMAIL.md`).
3. Start the backend: Flyway applies V5-V10, then the backfill grandfathers owners.
4. Check `SELECT version, success FROM flyway_schema_history;` shows 5-10 with `success = 1`, and `SELECT COUNT(*) FROM owner_subscriptions;` equals the number of owners.
5. If anything is wrong: stop the backend and restore the backup (section 5).

**Still not applied. This needs your explicit approval.**
