# HostelHub — Product Feature Audit

Audit date: 2026-10-26. Scope: the repository at `HostelManagementSystem/` (Spring Boot backend, React frontend, Flyway migrations V1–V5). **No application code was changed for this audit.**

## How to read this report (evidence levels)

| Label | Meaning |
|---|---|
| **Verified (tests ran)** | An automated test that I executed this session passed. Only the online-payment module qualifies: `mvn test` ran 64 tests, 0 failures (27 unit, 37 integration against a throw-away MySQL schema). |
| **Static** | Read from source/config/migrations only. Nothing was executed. Treat as strong evidence of what the code does, not proof of runtime behaviour. |
| **Spot-checked** | A static finding I re-read myself in the source after a sub-review reported it. |

Nothing outside online payments has automated test coverage, so **no other feature can be labelled "implemented and verified"**. Everything else is at best "implemented, not fully verified". The app was also started and served during this session (backend health 200, frontend 200), but no end-to-end workflow was exercised by me beyond the payment tests.

Stack (read from files): Spring Boot 3.5.16, Java 21 target (running on JDK 25), Maven, MySQL with Flyway (V1–V5), `ddl-auto: validate`; React 18 + Webpack 5 + react-router 6, Yarn (`yarn.lock`), axios, react-hook-form, recharts, react-icons. The folder is **not a git repository**.

---

## A. Executive summary

**Maturity.** A broad, coherent single-product operations app: hostels → buildings → floors → rooms → beds, residents and admissions, invoicing/payments with receipts, expenses, maintenance, notices, staff directory, in-app notifications, a Super Admin portal, and (new this session) online payments with pay links. Code quality in the core money and occupancy paths is good (pessimistic locking, DB-level overbooking constraints, BigDecimal, hashed refresh tokens, per-request permission reload). It is **not yet a SaaS product**: there are no plans, limits, trials or billing, owner onboarding of payment methods is absent, and automation (reminders, exports, import, i18n, backups) is thin.

**Strongest existing capabilities.** Overbooking prevention (DB unique keys + row locks); consistent hostel-scoped tenant checks on all but a handful of by-id paths; invoice/payment ledger with locking, reversal and receipts; token revocation on user disable / owner suspend; online payment module with verified webhooks and tests; floor/bed visualisation and vacancy forecast.

**Biggest weaknesses.** (1) No subscription/plan/limit system at all. (2) Manager-to-manager privilege problems in user management. (3) PII/balance exposure through `HOSTEL_VIEW`-only endpoints and in the Super Admin activity feed. (4) Production defaults fail open (profile defaults to `dev`, known super-admin password, Swagger public, password reset cannot send email). (5) Tests exist only for payments. (6) No resident reminders automation, no import/export, no backups, no i18n.

**Critical security/financial risks.** See section D. Top three: peer account takeover via `resetPassword`; `dev` profile as default with a known super-admin password; resident PII/balances readable with only `HOSTEL_VIEW`.

**Ready for a pilot?** Conditionally yes for a *supervised, small, invited pilot* (a few trusted owners, you operating the platform, owners onboarded by Super Admin, `SPRING_PROFILES_ACTIVE=prod`) **after fixing the P0 items**. Not ready for self-serve public sale: no plans/limits/billing, no working password-reset email, no backups.

---

## B. Feature matrix

Status key: **IV** implemented & verified (tests ran) · **INV** implemented, not fully verified (static) · **P** partial · **M** missing · **B** broken · **S** insecure. `BE` = `backend/src/main/java/com/hostelmanagement`, `FE` = `frontend/src`.

### B1. Platform / tenancy / roles

| Feature | Status | Frontend evidence | Backend evidence | Database | Test evidence | Gap | Priority |
|---|---|---|---|---|---|---|---|
| Owner (tenant) accounts, Super Admin separation | INV | `pages/superadmin/*`, `routes/ProtectedRoute.js` | `admin/AdminController` (`hasRole('SUPER_ADMIN')`), `AdminService`, `config/SuperAdminSeeder` | `owners`, `users.owner_id` nullable (V4), role `SUPER_ADMIN` | none | Same `/api/auth/login` for both; portal split is frontend-only (backend still rejects tenant APIs for SA because it has no permissions) | P1 |
| Data isolation across owners | INV | – | `HostelAccessService`, owner check in each `getEntity`/`getAccessible` | Only 16 tables carry `owner_id`; rooms, beds, buildings, floors, admissions, bookings, expenses, maintenance, deposits isolate via `hostel_id` only (app-level, no composite FKs) | Only payments (`OnlinePaymentSecurityTest`) | No cross-tenant read/write leak found by static review; no tests outside payments | P0 (tests) |
| Manager hostel scoping | INV | `HostelAssignPicker`, `AssignHostelsModal` | `user_hostel_access`, `HostelAccessService.scope/checkEntity` | `user_hostel_access` | payments only | Cross-hostel gaps in booking/maintenance/staff (D) | P0/P1 |
| Manager create / reassign / deactivate | INV | `pages/managers/*` | `auth/UserManagementService` | `users`, `user_permissions`, `user_hostel_access` | none | Peer takeover & access stripping (D1); deactivation revokes tokens immediately (good, static) | P0 |
| Permission enforcement on backend | INV | `ProtectedRoute perm=` | `@PreAuthorize` on all controllers (see D) | `permissions`, `role_permissions` | payments only | `FINANCE_REPORTS` never enforced; PII endpoints under-gated | P1 |
| Super Admin: owner create/edit/suspend/reactivate/reset pw | INV | `SaOwnersPage`, `SaOwnerFormPage`, `OwnerStatusDialog` | `AdminController` `/api/admin/owners*` | `owners.status` | none | No delete, no plan, no manager control | P1 |
| Super Admin: global stats, activity, settings | INV | `SaDashboardPage`, `SaReportsPage`, `SaActivityPage`, `SaSettingsPage` | `AdminService` | `platform_settings`, `audit_logs` | none | Activity feed returns raw audit `details` containing resident names/amounts; `platform_settings` affect nothing | P1 |
| **Subscription plans / limits / trials / entitlements** | **M** | none | none | none | none | Entirely absent (E) | **P1** |
| Audit log | INV/P | Super Admin activity only; `endpoints.js auditLogs` unused | `common/audit/AuditService`, `GET /api/audit-logs` (OWNER only) | `audit_logs` | none | No owner UI; many sensitive reads/sends unlogged; PII in `details`; no retention | P1 |

### B2. Hostel structure and occupancy

| Feature | Status | Frontend | Backend | Database | Tests | Gap | Priority |
|---|---|---|---|---|---|---|---|
| Multiple hostels / buildings / floors / rooms / beds | INV | `HostelSetupPage`, `StructureModals`, `RoomBedsModal`, `RoomsPage` | `HostelService`, `BuildingService`, `FloorService`, `RoomService`, `BedService` | `hostels`, `buildings`, `floors`, `rooms`, `beds` (soft delete `deleted_at`) | none | Name/label uniqueness is app-level (race-prone); `BedService.create` bypasses the 30-bed room cap (spot-checked: cap only in `RoomService`) | P1 |
| Bed states (occupied/available/reserved/maintenance) | INV | `components/building/*`, `RoomStatusLegend` | `occupancy/RoomStatusCalculator`, `OccupancyQueries` (derived, not stored) | `beds.status`, `bookings`, `maintenance_requests` | none | – | – |
| Overbooking prevention | INV | – | `AdmissionService.lockAvailableBed` (`BedRepository.lockById`) | `uk_alloc_active_bed` generated column, `uk_alloc_active_admission`, `uk_admissions_active_resident`, `uk_bookings_reserved_bed` | none | Strong design; unverified at runtime | P2 (tests) |
| Move-in / move-out / transfer | INV | `AdmissionDialogs`, `CheckoutDialog`, `StayTab` | `AdmissionService.admit/checkout/transfer` | `admissions`, `bed_allocations` | none | Checkout does not settle deposit/dues; cross-hostel transfer leaves old `hostel_id` on invoices/payments/deposits | P1 |
| Reservations / bookings | P | **no UI** | `BookingController/Service`, expiry job 00:30 | `bookings` | none | Backend only; advance not linked to payments | P2 |
| Enquiries / waitlist | P | **no UI** (notification links to `/enquiries` 404) | `EnquiryController/Service` | `enquiries` | none | No UI, no waitlist | P2 |
| Upcoming vacancies | INV | `VacanciesPage`, `VacancyPanel` | `VacancyForecastService` | – | none | Returns resident name/mobile under `HOSTEL_VIEW` only | P1 |
| Floor / bed visualisation, responsive | INV | `BuildingView`, `FloorPlan`, `FloorDesigner`, `responsive.css`, `BottomNav` | `BuildingViewService` | V3 layout columns | none | `GET /rooms` loads all bed states then pages in memory — will not scale to large hostels | P2 |
| Bulk import (residents/rooms/beds) | **M** | none | none | – | – | None anywhere | P2 |

### B3. Residents

| Feature | Status | Frontend | Backend | Database | Tests | Gap | Priority |
|---|---|---|---|---|---|---|---|
| Create/edit/deactivate | INV | `ResidentFormPage`, `ResidentsPage`, `ResidentProfilePage` | `ResidentController/Service` | `residents` (no `deleted_at`) | none | No hard delete/anonymise/export/retention | P2 |
| Duplicate detection | **M** | – | none | only non-unique `idx_residents_mobile` | – | Same person can be created repeatedly | P1 |
| Documents/photo upload | INV | `DocumentsTab` | `common/files/FileStorageService` (magic-byte whitelist JPEG/PNG/WEBP/PDF, 5 MB, UUID names, traversal guard), `FileController` | `file_attachments` | none | **No permission check on `GET /api/files/{id}`** (any tenant user with hostel access); no delete; orphans; no quota; no encryption at rest; local disk | P1 |
| Sensitive data | S/P | profile page | `ResidentService.profile` returns `idDocumentNumber` unmasked | plaintext columns | none | No masking/encryption | P2 |
| Emergency contact, history, deposits | INV | `StayTab`, `PaymentsTab` | `Admission*`, `Deposit` | `deposits` | none | single emergency contact | P3 |

### B4. Rent, invoices, payments (staff-recorded)

| Feature | Status | Frontend | Backend | Database | Tests | Gap | Priority |
|---|---|---|---|---|---|---|---|
| Monthly rent invoices | INV | `PaymentsPage`, `InvoiceDetailPage` | `InvoiceService.generateRentInvoices`, `BillingScheduler` (01:15) | `invoices`, `uk_invoices_period` | none | **No proration** (mid-month move-in/out); one big transaction for the nightly batch; no distributed lock for schedulers | P1 |
| Due / overdue | INV | `StatusBadge` | `BillingScheduler`, `PaymentQueries.EFFECTIVE_STATUS` | – | none | Single flat late fee only | P2 |
| Partial / multiple payments, allocation | INV | `CollectPaymentPage` | `PaymentService.record/collect` (locks) | `payments`, `payment_allocations` | **online path IV**; manual path none | No idempotency key (double submit = two receipts) | P1 |
| Cash / UPI / bank / card recording | INV | `CollectPaymentPage` | `PaymentService.METHODS` | `payments.method` | none | UPI/bank are *recording* only; no "pending bank transfer" state | P2 |
| Receipts (PDF) | INV | `PaymentDetailPage` | `ReceiptPdfService`, `GET /api/payments/{id}/receipt.pdf` | – | none | – | – |
| Reversals / refunds | INV | `PaymentDetailPage` | `PaymentService.reverse/refund` | `refunds` | online reversal ran in `OnlinePaymentFlowTest` | **Reversing a `DEPOSIT_ADJUSTMENT` does not restore `deposits.amount_adjusted`; advance-refund vs credit accounting is lossy** (static, balance-correctness bug) | P0/P1 |
| Deposits & settlement | P | `FinanceModals` | `Deposit.refreshStatus`, `adjustDeposit`, `refund` | `deposits` | none | No forfeiture/damage deduction, no checkout settlement transaction | P1 |
| Decimal-safe maths | INV | – | `common/Money` (BigDecimal scale 2 HALF_UP), `DECIMAL(12,2)` | yes | online path | No `@Digits` cap on manual amounts | P2 |
| Expenses, P&L | P | `ExpensesPage`, `ExpenseAnalytics` | `ExpenseService` | `expenses` | none | Summary shows income/expense net; "collections" include deposits and ignore refunds (not net revenue) | P2 |
| Financial reports / export | P | tiles on `PaymentsPage`; expenses CSV | `PaymentQueries.summary/trend` | – | none | No server-side export; CSV has no formula-injection guard; `FINANCE_REPORTS` unused | P2 |
| Audit of manual financial changes | INV | – | `AuditService` calls in `PaymentService/InvoiceService` | `audit_logs` | none | OK for money; PII in details | P2 |

### B5. Owner payment onboarding

| Feature | Status | Evidence | Gap | Priority |
|---|---|---|---|---|
| Accepted methods, UPI ID, static UPI QR, bank instructions, per-hostel config | **M** | `owner_settings` only holds vacancy/grace/late-fee/reminder/currency; `SettingsPage` has business rules + WhatsApp card; no migration/entity/DTO for UPI/QR/bank | Nothing to mask or protect because nothing is stored | P1 |
| Gateway credentials | P | Global env vars `app.payment.razorpay.*` (never sent to React — verified in `RazorpayPaymentGatewayServiceTest`) | Not per owner; single merchant account; split settlement needs provider approval | P2 |
| Onboarding wizard | **M** | `HostelSetupPage` is a structure editor, not a wizard | – | P2 |

### B6. Online payments (built this session)

| Feature | Status | Evidence | Gap | Priority |
|---|---|---|---|---|
| Provider abstraction + mock + Razorpay adapters, config-driven | **IV** | `payments/gateway/*`; `PaymentGatewayConfigTest` (9), `RazorpayPaymentGatewayServiceTest` (18) | Razorpay Test Mode **never run against the real API** (no credentials) | P1 |
| Server-side verification, never trusting browser | **IV** | `verifyCheckout` signature + provider fetch; flow tests | – | – |
| Webhook signature (raw bytes), idempotency, concurrency | **IV** | `OnlinePaymentFlowTest` duplicate/concurrent/invalid-signature tests | Webhook handling against real Razorpay payloads untested | P1 |
| Pending/failed never reduce balance; async reconcile | **IV** | flow tests + `PaymentReconciliationScheduler` test | – | – |
| Tokenized pay links, partial ≥ ₹100, full balance when < ₹100 | **IV** | `PaymentLinkService`, `PublicPayController`, `OnlinePaymentSecurityTest` | Rate limiter is in-memory and keyed on `getRemoteAddr()` (wrong behind a proxy) | P1 |
| Gateway refunds | M | not implemented; reversing an `ONLINE` payment in-app does not refund at the provider | – | P2 |
| Add another provider | P | New adapter + one `case` + one React branch (`docs/PAYMENTS.md`); checkout/webhook/settlement differences remain real work | Medium effort per provider | – |
| UI (full/partial, states, history, test-mode notice) | INV | `PayPanel`, `OnlinePaymentPanel`, `PublicPayPage`; frontend **bundle compiled**, UI **not exercised in a browser** | needs manual test | P1 |

### B7. WhatsApp and reminders

| Feature | Status | Evidence | Gap | Priority |
|---|---|---|---|---|
| Staff-triggered messages, bulk overdue reminders, announcements | INV | `WhatsAppService`, `WhatsAppController`, `InvoiceDetailPage`, `PaymentsPage` | **No dedupe** — double bulk-click sends twice | P1 |
| **Automated resident reminders (before/on/after due)** | **M** | `ReminderScheduler` notifies *staff* in-app only (its Javadoc says so); `owner_settings.reminder_days_before` is stored, editable, **and never used** | Setting is misleading | P1 |
| Partial balance in message | INV | amount = `total − paid` | – | – |
| Stop after paid | INV | status re-checked (409) | stateless | – |
| Retry/backoff, delivery status | INV | `retryFailed` every 2 min, 3 attempts, exponential; webhook `X-Hub-Signature-256` constant-time | No replay protection/event idempotency; no tests | P2 |
| Real provider (Meta Cloud API) | P | `MetaCloudWhatsAppProvider`: template + free text | Body params only, one global credential set, no per-owner config, no opt-out/inbound, no template-approval check, **untested against Meta** | P2 |
| Consent | INV | `residents.whatsapp_opt_in`, `SKIPPED_NO_CONSENT` | No STOP handling | P2 |
| Template editor | P | API exists; **no UI** | – | P3 |
| Usage/cost tracking, quotas | **M** | `whatsapp_messages` rows only | needed for plan limits | P2 |
| Default mode | INV | `WHATSAPP_MODE=MOCK`; UI badges "Simulated" | Do not claim live WhatsApp | – |

### B8. Operations and competitive features

| Feature | Status | Notes |
|---|---|---|
| Maintenance requests / complaints | INV (staff-raised only; no resident complaints) | `MaintenancePage`, `MaintenanceService` |
| Notices / announcements | INV | `AnnouncementsPage`, optional WhatsApp broadcast |
| Staff directory | P | salary is a field; **no attendance/payroll** |
| Visitor records | M | – |
| Digital agreements / e-sign | M | only a document category |
| Resident self-service | P | only the `/pay/:token` page (no login) |
| AI-assisted reports | M | – |
| Multilingual (English + Telugu) | M | hard-coded English, no i18n library |
| Bulk import/export | M/P | two client-side CSVs only |
| Backups / recovery | M | no scripts or docs |
| Mobile & accessibility | P | `responsive.css`, `BottomNav`, ~357 aria/role uses; thin keyboard support; no automated a11y checks; manifest lacks PNG icons |
| Global search | INV | `SearchController` (capped) |
| In-app notifications | INV | `NotificationService`; only channel implemented |

---

## C. Missing and incomplete features (practical impact)

1. **No subscription system** — you cannot sell tiers, cap usage, trial, or suspend by non-payment; every owner has unlimited everything.
2. **No owner payment setup** — cash-only owners are fine, but owners cannot show a UPI ID/QR/bank details to residents; pay links work only through your single gateway account.
3. **No automated reminders** — the most-requested PG feature (rent chasing) is manual; `reminder_days_before` setting does nothing.
4. **No proration** — mid-month move-ins/outs bill a full cycle from the admission date; owners will correct by hand-adding discounts.
5. **No import** — onboarding an existing PG with 100+ residents is entirely manual.
6. **No password-reset email in production** — `AuthService.forgotPassword` only logs the link in dev/test; locked-out users need a Super Admin reset (owners) or the owner (managers).
7. **Enquiries and bookings have no UI** though the backend exists, and notification links to `/enquiries` land on a 404.
8. **No backups or recovery procedure**, uploads on local disk, no purge of tokens/audit/orphan files.
9. **No duplicate-resident detection**, no document delete/quota, no data export/erasure.
10. **No i18n/Telugu, no visitor log, agreements, payroll.**

---

## D. Security and financial correctness

Priority order. "Spot-checked" = I re-read the cited code.

### P0 — act before any pilot

1. **Peer account takeover and access stripping in user management** *(spot-checked, `auth/UserManagementService.resetPassword` / `editable`)*. A non-owner with `STAFF_MANAGE` can reset the password of any user who shares **one** hostel with them — including a manager who has **more** hostels or permissions — then log in as them. `update` also replaces the target's entire permission/hostel set (`deleteByUserId`) so a manager can strip a peer's access to hostels the caller cannot even see. `checkGrantable` stops granting what you lack, but not taking over someone with more. *Fix direction:* a non-owner may only modify users whose permissions and hostels are a subset of their own; password reset by owner only (or the same subset rule).
2. **Fail-open production defaults.** `spring.profiles.active` defaults to `dev` (`application.yml`), which seeds demo data and a **known** Super Admin (`superadmin@hostelhub.dev` / `Admin@12345`, `application-dev.yml`), with `refresh-cookie-secure=false` and DEBUG logging. `application-prod.yml` is a stub. Swagger/OpenAPI is public in all profiles; no HSTS/CSP; JDBC URL has `createDatabaseIfNotExist=true`. *Mitigated already:* the mock payment gateway refuses to start under `prod` (`PaymentGatewayConfig`, `PaymentGatewayConfigTest.mock_isForbiddenInProd`), so the dev-only `MockSettleController` cannot be reached in a correctly configured production.
3. **Resident PII and balances readable with only `HOSTEL_VIEW`**: `GET /api/rooms`, `/api/rooms/{id}`, `/api/hostels/{id}/building-view` (`BuildingViewService.roomDetails`: names, mobile, rent, outstanding/overdue, payment status) and `GET /api/dashboard/vacancies` (name, mobile). A "view hostels" staff member can read what `RESIDENT_VIEW`/`PAYMENT_VIEW` are supposed to protect. `GET /api/files/{id}` has **no** permission check at all, so a user with only `MAINTENANCE_MANAGE` can fetch a resident's ID document if they guess a sequential file id.
4. **Super Admin sees resident/payment details** through `/api/admin/activity`, `/api/admin/owners/{id}` and the dashboard, which return raw `audit_logs.details` (e.g. `PAYMENT_RECORDED … from <resident name>`), contradicting the stated rule that the platform admin never sees resident or payment data. Redact for the platform view.

### P1

5. **Balance-correctness bugs (static):** reversing a `DEPOSIT_ADJUSTMENT` payment restores the invoice but not `deposits.amount_adjusted` (deposit shows less held; `held()` can go negative); reversal zeroes `unallocated_amount` even if part was already refunded as advance; manual payments have no idempotency key; dashboard "collections" include deposit receipts and ignore refunds.
6. **Cross-hostel (not cross-tenant) leaks:** `BookingService.create` loads the enquiry by owner only *(spot-checked)* so a manager can attach/flip another hostel's enquiry; `MaintenanceService.validStaff` and `StaffService.update` (owner-wide `hostel_id IS NULL` staff incl. salary editable/readable by any `STAFF_MANAGE` manager); unvalidated `maintenanceRequestId` on expenses; `HostelService` lets `HOSTEL_MANAGE` users grant hostel access to any owner user.
7. **Cross-tenant email enumeration:** `UserManagementService.create` returns a distinct "already exists" for emails registered on any tenant (global unique key).
8. **Rate limiting and audit IPs are proxy-unaware** (`LoginRateLimiter`, `AuditService` use `getRemoteAddr()`; no `server.forward-headers-strategy`). Also in-memory/single-instance. Password policy is 8 chars letter+digit only.
9. **Scheduler safety:** `BillingScheduler.runBilling` is one transaction for all tenants (one failure rolls back everything); no ShedLock, so a second instance double-runs billing/reminders/reconciliation.
10. **File handling:** no document delete, orphans, no quota, plaintext at rest, PDFs accepted on magic bytes only, stored on local disk.
11. **Client-side CSV exports** lack formula-injection escaping (`expenseUtils.downloadCsv`, `SaReportsPage`).
12. **Source maps** are emitted in production builds (`frontend/build` currently holds 34 `.map` files) — don't serve them publicly.

### Checked and found sound (static unless stated)
SQL: parameter binding throughout (no user-input concatenation found); tokens: access token in memory only, refresh cookie HttpOnly/SameSite=Strict, refresh rotation with reuse detection, permissions reloaded each request, disable/suspend/password-change invalidate sessions; money: BigDecimal/`DECIMAL(12,2)`; locking on payment/invoice/deposit paths; no `dangerouslySetInnerHTML`; service worker never caches `/api`; online-payment webhook/idempotency/amount-matching (**tested**).

---

## E. Subscription-limit audit (exact current state)

**There is no plan, subscription, trial, grace, entitlement, quota or billing concept anywhere** — backend, migrations V1–V5, or frontend (grep of `plan|subscription|trial|entitle|quota|limit` yields only unrelated hits: SQL `LIMIT`, pagination, rate limiters, the 5 MB upload cap, "floor plan"). `owners` has only `status` (ACTIVE/SUSPENDED/INACTIVE, enforced identically for SUSPENDED/INACTIVE); `owner_settings` holds only operational settings; `platform_settings` (two keys) affects nothing. `SaOwnersPage` has no plan column; `SaSettingsPage` has no pricing.

| Limit | Enforced today? | Entry points that would need a check | Edge cases found |
|---|---|---|---|
| Hostels per owner | No | `HostelService.create` (`@Transactional`) | soft delete via `deleted_at`: deleted hostels already excluded from counts; blocked while active admissions exist |
| Beds per hostel | No (only `RoomService.MAX_BEDS_PER_ROOM = 30` and `@Max(30)`) | `RoomService.create` (delta = `totalBeds`), `RoomService.update → resizeBeds` (grow), `BedService.create` (**bypasses even the 30 cap**) | Beds soft-deleted (`deleted_at`) free quota immediately; delete-recreate is net-neutral for a "live beds" cap. Beds/rooms with `status='INACTIVE'` are *not* deleted — a cap must count `deleted_at IS NULL` (not the occupancy stats) or INACTIVE parking evades it. A "created in period" metric would be bypassable (no creation counter). |
| Manager accounts | No | `UserManagementService.create`, and `update` when re-enabling | `uk_users_email` makes duplicates race-safe, but count-then-insert is not; unique accounts, not hostel assignments, would be counted (reassignment edits `user_hostel_access` rows only) |
| Concurrency | None | – | No locks on parent rows; `NumberSequenceService` and `*Repository.lockById` show the project's lock pattern. **Natural lock:** `SELECT … FOR UPDATE` on the owner's subscription (or `owners`) row, taken inside the existing service transactions. |
| Suspension hook | Exists | `PrincipalLoader.load` + `AuthService.requireOwnerActive` already block all tokens of a non-ACTIVE owner and `AdminService.setStatus` revokes refresh tokens | Reusable for "expired" — but a full lock-out is *not* the safe expiry policy (below) |

**Can the proposed limits (Starter 1/50/1, Growth 3/100/5, Business 10/200/15, Enterprise custom) be enforced safely?** Yes, architecturally: all creation paths funnel through a small number of `@Transactional` services; a central `PlanLimitService.assertCanAdd(ownerId, resource, delta)` that locks the subscription row and counts with the same `deleted_at IS NULL` SQL as `OccupancyQueries` fits cleanly. Not yet — nothing exists, and the `BedService.create` bypass must be closed in the same change. Per-hostel bed cap vs. existing data: some existing owners may already exceed the proposed numbers → "grandfather" rule needed (below).

**Recommended expiry/downgrade policy (to confirm with you):** never delete or lock *read* access to residents, invoices, payments or receipts. Trial/expiry → status `TRIAL → ACTIVE → GRACE (e.g. 7 days, full access, banner) → RESTRICTED (read-only for new hostels/beds/managers/admissions; can still record payments, view history, export) → SUSPENDED (by Super Admin only)`. Downgrade below current usage → keep everything, block only *new* hostels/beds/managers, show an over-limit banner and upgrade path. Failed subscription payment ≠ renewal.

---

## F. Competitor comparison

I did **not** research or test competitors; I will not invent their features or prices. The table lists only what *this code* provably does, versus the capabilities you named. Use it as a checklist to compare against each vendor's own current documentation.

| Capability (as you listed them) | HostelHub today | Evidence level |
|---|---|---|
| Multi-property, floors/rooms/beds, occupancy views | Yes | static |
| Overbooking protection | Yes (DB + locks) | static |
| Resident records, documents, deposits | Yes (no duplicate check, no import) | static |
| Rent invoices, partial payments, receipts | Yes (no proration) | static; online path tested |
| Online rent collection / pay links | Yes, mock + Razorpay adapter | **tested (mock + mocked HTTP)**; real Razorpay not run |
| Automated WhatsApp rent reminders | **No** (manual only; mock by default) | static |
| Expenses & P&L | Basic | static |
| Complaints/maintenance | Staff-raised only | static |
| Multi-language (Telugu) | No | static |
| Subscription plans / usage limits | **No** | static |
| Bulk import, server exports, backups | **No** | static |
| Resident app/portal | No (pay-link page only) | static |

Planned / unverified-by-me: whatever each competitor advertises (StayOne, ManagR, SatraM, PayGuest, HostelNPG) — **requires your own validation**. Likely differentiators you can already claim honestly: per-bed visual floor plans, DB-enforced overbooking safety, provider-independent verified online payments with tokenized links and no resident login, affordable multi-tenant structure.

---

## G. Prioritized remediation roadmap (effort S/M/L)

**P0 — Critical (before any external pilot)**
| # | Work | Effort | Depends on | Value |
|---|---|---|---|---|
| 1 | User-management escalation fix (subset rule; owner-only password reset) + tests | S | – | Removes account-takeover path |
| 2 | Fail-closed deployment: require explicit profile (no `dev` default), real `application-prod.yml` (springdoc off, Swagger blocked, HSTS/CSP via proxy, no `createDatabaseIfNotExist`, `forward-headers-strategy`), remove dev super-admin fallback from non-dev | S | – | Prevents known-credential exposure |
| 3 | Permission-gate PII: require `RESIDENT_VIEW` for occupant fields in rooms/building-view/vacancies; permission + entity-type check on `GET /api/files/{id}`; redact `audit_logs.details` for platform view | M | – | Real data-privacy boundary |
| 4 | Fix deposit-adjustment reversal and advance-credit accounting; add idempotency key to manual payments | M | tests (#H1) | Correct balances |

**P1 — Launch blockers**
| # | Work | Effort | Depends on | Value |
|---|---|---|---|---|
| 5 | Subscription system (plans, entitlements, owner subscription w/ trial/grace/status, overrides, history) + `PlanLimitService` + Super Admin Plans/Subscriptions UI + owner Usage page | L | P0 #2; decisions in §I | Core monetisation |
| 6 | Close `BedService.create` cap bypass; unique/lock strategy for structure creation | S | #5 | Limits actually hold |
| 7 | Production password reset (email provider) | M | account decision | Self-serve recovery |
| 8 | Automated resident reminders (use `reminder_days_before`, dedupe key, per-owner opt-in, still mock by default) | M | WhatsApp provider decision | Rent collection value |
| 9 | Cross-hostel fixes (booking enquiry, maintenance staff, owner-wide staff rows), email-enumeration message | S | – | Manager isolation |
| 10 | Owner payment onboarding (accepted methods, UPI ID, QR upload, masked bank instructions; per-hostel optional) | M | – | Cash/UPI owners self-serve |
| 11 | Backup/restore procedure + scripts, token/audit/orphan purge, scheduler locking (ShedLock) and per-admission billing transactions | M | – | Reliability |
| 12 | Tests: tenant isolation + permission matrix for every controller, auth/revocation, admissions/overbooking, billing, reversal math | L | – | Confidence for all of the above |
| 13 | Run payments against **real Razorpay Test Mode** (you supply keys) incl. public webhook | S | your credentials | Moves online payments from "mock-verified" to "provider-verified" |

**P2 — Competitive advantage**
Bulk CSV/Excel import (+ duplicate detection), proration for move-in/out, owner audit-log UI, server-side exports and richer reports (dues ageing, P&L), enquiries/bookings UI, SQL-paginated rooms listing, rate limiter behind proxy, template editor, WhatsApp usage tracking/quota, Telugu/i18n — **M each**, i18n **L**.

**P3 — Future**
Resident portal/complaints, visitors, digital agreements/e-sign, payroll/attendance, AI reports, gateway refunds, per-owner gateways/split settlement (needs provider approval) — **L each**; validate owner demand first.

---

## H. Recommended tests

Automated (none of these exist today except payments):
1. **Tenant isolation matrix:** for each controller/by-id endpoint, owner B and an unassigned manager get 404/403 (parameterised test generated from the endpoint list).
2. **Permission matrix:** each `PERM_*` allowed/denied per endpoint; PII endpoints with `HOSTEL_VIEW` only.
3. **User management escalation:** peer reset/update with more hostels/permissions must fail.
4. **Auth:** lockout, refresh rotation & reuse detection, token invalidation on disable / owner suspend / password change.
5. **Admissions:** concurrent admit to same bed (expect one winner), transfer, checkout dues/deposit.
6. **Billing:** idempotent rent generation, back-dated admission, late fee once, proration (when built), concurrent manual+nightly runs.
7. **Money:** reversal incl. deposit adjustment, refund/credit accounting, duplicate manual submit.
8. **Subscription (when built):** limits per resource, concurrent creation at the boundary (N threads), per-hostel bed cap incl. `BedService.create` and room resize, INACTIVE-parking, manager unique-account counting, downgrade-over-limit, trial/grace/restricted/suspended transitions, data preserved, SA-only plan administration, entitlement checks.
9. **Flyway:** migrate V1→latest on an empty schema (already exercised implicitly by the test schema).
10. **Frontend:** add Jest/RTL (the `"test": "jest"` script is broken — Jest is not installed); component tests for `PayPanel` states.
Manual: full pay-link flow in a browser (mock + Razorpay Test Mode cards/UPI), mobile layouts, keyboard navigation, PDF receipt, WhatsApp mock banner, owner suspension → forced logout.

---

## I. Questions for you (not decidable from code)

1. **Plans:** are Starter ₹199 / Growth ₹399 / Business ₹799 / Enterprise final? Annual pricing? Trial length? Grace-period length?
2. **Limit semantics:** should *inactive* managers count? Should beds with `status = INACTIVE`/`MAINTENANCE` count (I recommend yes: count all non-deleted)? Do archived hostels count?
3. **Existing owners over the new limits:** grandfather them at current usage, or require upgrade?
4. **Expiry policy:** OK with the GRACE → RESTRICTED (read-only for new resources, payments still recordable) → Super-Admin-only SUSPENDED model?
5. **Subscription billing:** in scope now (collect your SaaS fee via Razorpay) or manual/offline by Super Admin first?
6. **Onboarding model:** one platform Razorpay account for all owners (money lands with you; you pay owners out) or owners' own gateway accounts? This changes legal/settlement design.
7. **Email provider** for password reset (SMTP/SES/other)?
8. **WhatsApp:** which provider/account, and will owners use your number or their own? (Controls per-owner config and cost tracking.)
9. **Manager rights:** should only the owner manage users/passwords, or may senior managers manage junior ones (subset rule)?
10. **Enquiries/bookings UI:** intentionally removed or just not built yet? (I did not restore anything.)
11. **Hosting target** (single VM, Docker, cloud) and DB backup RPO/RTO?
12. **Git:** the project is not under version control — OK to initialise a repository before changes begin?

---

## J. Implementation log (remediation, updated 2026-10-27)

Approved order: 1 security, 2 financial correctness, 3 subscriptions, 4 password-reset email / reminders / owner payment setup, 5 backups + scheduler locking, 6 more tests + Razorpay Test Mode, 7 competitive features. Plus a requested notification-retention feature. **Phases 1-3 and the notification retention are done; Phases 4-7 are not started.**

### J1. Database state (read this first)

New Flyway migrations: **V5** online payments, **V6** `idx_notifications_created`, **V7** subscriptions (plans, entitlements, owner subscriptions, history, manual subscription payments, seeded plans), **V8** `payments.idempotency_key`.

* They have been applied **only to the throw-away test schema `hostel_management_test`** (recreated by the tests).
* They have **not** been applied to your main database `hostel_management_db`, which is at V4. Per your instruction I did not migrate it. Starting the backend against it applies V5-V8 automatically (Flyway). Take a backup first (`mysqldump hostel_management_db > backup.sql`).
* On that first start `SubscriptionBackfill` gives every existing owner an open-ended ENTERPRISE subscription whose limits are never below current usage, so nothing is blocked or removed. Move owners to real plans from Super Admin > Owners.
* `backend/.env` already sets `SPRING_PROFILES_ACTIVE=dev`, which the application now requires.

### J2. What changed, by phase

**Phase 1 - security (done, 16 + 3 regression tests)**

| Audit finding | Change |
|---|---|
| Manager takeover / access stripping (`UserManagementService`) | Password reset is owner-only. A non-owner may edit a user only if that user's hostels and permissions are within their own. |
| Fail-open production defaults | Profile no longer defaults to `dev` (`ProfileGuard` refuses to start without `dev`/`prod`/`test`). `application-prod.yml`: springdoc disabled, `forward-headers-strategy` configurable (default `none`), INFO logging. Swagger is denied under the prod profile. `createDatabaseIfNotExist` is opt-in (`app.db.create-database`, true only in dev). `.env.example` documents it. |
| Resident PII and balances under `HOSTEL_VIEW` | `ResidentPrivacy`: room details, building view, room list and vacancy forecast show "Resident" and no mobile without `RESIDENT_VIEW`, and no rent/balance/payment status without `PAYMENT_VIEW`. Searching rooms by resident name is limited to `RESIDENT_VIEW`. |
| File downloads without permission check | Files inherit the permission of what they belong to: resident files need `RESIDENT_VIEW`, hostel images `HOSTEL_VIEW`, maintenance images `MAINTENANCE_MANAGE`, unknown kinds are owner-only. |
| Super Admin feed shows resident/payment details | Audit `details` are shown to the platform only for account/structure/subscription events; operational events show that they happened, not the content. |
| Cross-hostel gaps | Booking enquiry link, maintenance staff assignment, owner-wide staff edits/salaries, hostel manager assignment (needs `STAFF_MANAGE`) and the expense-to-maintenance link are now checked. |

**Phase 2 - financial correctness (done, 10 tests)**

* Reversing a `DEPOSIT_ADJUSTMENT` payment now restores `deposits.amount_adjusted`.
* A reversal is refused (409, nothing changes) when it would leave a deposit with a negative held amount, or when part of the payment was already refunded to the resident as advance credit.
* Manual payments accept an optional `idempotencyKey` (unique per owner, `V8`). Retrying returns the original payment; reusing a key for a different payment is a 409; two identical requests racing return the same payment. The collect form sends a key.
* **Pre-existing deadlock fixed** in `NumberSequenceService` (receipt/invoice numbers): two concurrent first-use inserts for an owner/year could deadlock (HTTP 500). Found by the new concurrency test.

**Phase 3 - subscriptions (done, 31 backend + 15 frontend-logic tests)**

* Data model and defaults exactly as approved (Starter 199 / 1 hostel / 50 beds / 1 manager, Growth 399 / 3 / 100 / 5, Business 799 / 10 / 200 / 15, Enterprise custom; 14-day trial, 7-day grace) stored in `subscription_plans` and editable in Super Admin > Subscription plans (create, edit, activate/deactivate/archive, feature entitlements, subscriber counts).
* Create Owner now requires a subscription block: plan, start date, trial days, expiry, grace, optional custom limits and price. Plan selection pre-fills everything; values that differ from the plan are shown as "Custom" with a one-click reset. Owner and subscription are created in one transaction; there is no API path that creates an owner without a subscription.
* Owner detail page: subscription card with state, dates, limits against usage (over-limit highlighted), history (who/what/when), manual payments, edit dialog. A recorded payment is the only thing that renews; `extendMonths = 0` changes nothing.
* Enforcement (`SubscriptionLimitService`): hostels per owner, beds per hostel (all non-deleted beds including inactive), enabled non-owner logins counted once each. Every check takes a row lock on the owner's subscription and counts with locking reads, so concurrent requests cannot overshoot (tested with 8 racing threads). `BedService.create` no longer bypasses the 30-beds-per-room cap and now respects the per-hostel limit; growing a room is limited, shrinking never is.
* Lowering a limit or changing plan never deletes anything; usage above a limit only blocks growth. Expiry: TRIAL/ACTIVE -> GRACE (still allowed) -> RESTRICTED (no new hostels/beds/managers; reads, invoices, payments, receipts and the owner's login keep working). SUSPENDED/CANCELLED subscriptions are RESTRICTED. An owner with no subscription row is blocked (`SUBSCRIPTION_MISSING`) rather than treated as unlimited.
* Feature gate (`EntitlementService`): online payments (staff checkout and pay links) are gated by the plan's `ONLINE_PAYMENTS` entitlement, read from the database. Other features exist in the catalogue; their modules are not built yet.
* Owner UI: "Hostels: 2 / 3" on Hostels, "Beds in this hostel: 75 / 100" on Hostel setup, Add-hostel disabled with the reason at the limit, a Plan & usage card in Settings, and a banner for grace/restricted/ending-soon.

**Notification retention (done, 16 tests)**

* `NotificationRetentionService` deletes notifications whose `created_at` is at or before `now - app.notifications.retention-days` (default 30, env `NOTIFICATION_RETENTION_DAYS`), read or unread, every tenant, in batches (`delete-batch-size`, default 5000). Age is the creation timestamp only (re-reading or updating a row does not extend it).
* Runs daily at 03:30 in the business time zone (`NOTIFICATION_CLEANUP_CRON`). A MySQL named lock (`DbJobLock`, reusable for Phase 5) prevents overlapping runs across instances. Failures are logged (type and message only, never notification text) and retried next day; success logs count and duration.
* `V6` adds an index on `created_at`. `notifications` has no foreign keys pointing at it. No frontend change was needed: the list reloads when opened and the badge polls every 60 s.
* Side effect to know: staff reminders use dedupe keys; a reminder whose condition persists beyond 30 days (e.g. an overdue checkout) is created again after its old notification is purged, at most once per 30 days.

### J3. Test results (actually run, 2026-10-27)

* `mvn test` (backend): **140 run, 0 failures, 0 errors, 0 skipped** - ProfileGuardTest 3, NotificationRetentionTest 16, PaymentGatewayConfigTest 9, RazorpayPaymentGatewayServiceTest 18, OnlinePaymentFlowTest 25, OnlinePaymentSecurityTest 12, PaymentIntegrityTest 10, Phase1SecurityTest 16, SubscriptionTest 31. Integration tests use the real MySQL test schema, controllers and security chain; only the payment provider is simulated.
* `npm test` (frontend): **15 run, 15 passed** - plan-default/override/validation/usage logic (`src/utils/subscriptionLogic.test.mjs`) using Node's built-in runner. The old `"test": "jest"` script was broken (Jest was never installed); it now runs these.
* Frontend production bundle compiles (webpack, output to a scratch folder).
* **Not run / not verified:** any browser-level UI check (forms, modals, banners were compiled but never clicked through); React component tests (no test framework installed - adding Jest/Testing Library needs your approval); the app started against the main dev database (migrations not applied there); real Razorpay Test Mode (needs your keys); real WhatsApp/SMTP; a dependency vulnerability scan; the Phase 1 cross-checks for `forward-headers`/Swagger-in-prod beyond configuration (the prod profile was not booted).

### J4. Assumptions I made (change if wrong)

1. Enterprise is seeded with generous but finite limits (50 hostels, 1000 beds per hostel, 100 managers); "configurable" is per owner or by editing the plan.
2. "Manager accounts" = every enabled non-owner login (managers, wardens, staff). Disabled ones do not count.
3. Existing owners are grandfathered on an open-ended Enterprise subscription (see J1).
4. Plan edits apply to owners immediately (except fields they override). Archived plans are frozen.
5. The Super Admin dashboard "collections" figure still means gross cash received (it includes deposits and ignores refunds). I did not change its meaning; decide whether it should become net revenue.
6. After a cross-hostel transfer, old invoices and payments keep their original hostel (income stays with the hostel that earned it).
7. Trial/billing: billing status is tracked separately (`NOT_REQUIRED`, `UNPAID`, `PAID`); there is no subscription payment gateway, as agreed.

### J5. Remaining issues

* **Not started:** password-reset email (SMTP), automated rent reminders using `reminder_days_before` (still stored and ignored), owner payment-method setup (UPI/QR/bank), database backup/restore instructions and scheduler locking for billing/reminders (`DbJobLock` exists, billing is not yet wrapped), per-admission billing transactions, bulk import, proration, owner audit-log UI, Telugu/i18n, enquiries/bookings UI (backend exists, no UI; confirm it was never built rather than removed), gateway refunds.
* **Known gaps in finished work:** reservation guest names are still visible with only `HOSTEL_VIEW`; the "email already exists" message still reveals registered emails across tenants; `GET /api/owners/settings` returns an error for a Super Admin token (harmless); WhatsApp has no per-message dedupe and no opt-out handling; the pay-link rate limiter is in memory and keyed on `getRemoteAddr()` (set `FORWARD_HEADERS_STRATEGY=framework` only behind a trusted proxy); a subscription payment recorded with `extendMonths` always renews from today or the current end date, with no reversal workflow; file uploads still have no delete/quota/virus scan.
* **Process:** the project is still not under version control. Before initialising Git, confirm that is what you want; I have not touched the repository layout.

### J6. Open questions

1. Should "collections" on the dashboard become net revenue (excluding deposits, minus refunds)?
2. May I install Jest and React Testing Library (dev dependencies) to add component tests?
3. Apply migrations V5-V8 to `hostel_management_db` now? (Back up first; the backfill runs on that start.)
4. Should enquiries/bookings get a UI, or were they intentionally removed?
5. Which SMTP provider will you use for password-reset email?

---

## K. Implementation log, round 2 (2026-10-27)

**Database: unchanged.** `hostel_management_db` is still at Flyway V4. Migrations V5-V10 exist and were rehearsed on a restored copy only. Review, rollback limits, backup/restore commands and the approval procedure: `docs/MIGRATION_V5_TO_V10.md`. A verified backup is in `backups/` (git-ignored). **Waiting for your explicit approval before applying anything.**

### K1. Completed

| Item | Summary |
|---|---|
| Collections vs Net Revenue | "Collections" kept (all money received, defined on screen). New **Net revenue** = Collections - deposit receipts - advance refunds; deposit refunds are shown separately and not deducted. Monthly trend has both. 4 backend tests check it against the records. |
| Jest + React Testing Library | Installed with Yarn. Component tests: subscription cards/usage, owner creation with custom limits, hostel limit indicators, notification panel, permission-based routes/UI, payment panels, plans admin, enquiries, payment methods, settings. Node logic tests kept. |
| Enquiries / bookings | The backend existed; **no UI had ever been built**. Added the Enquiries page (enquiries, reservations, convert/cancel), sidebar entry and route guarded by `ENQUIRY_MANAGE`. |
| Password-reset e-mail | Provider-independent SMTP (`MAIL_MODE` LOG/SMTP/DISABLED), env-configured, no credentials in the repo, LOG mode for development, refused in prod. Sending is async after commit, so response and timing do not reveal whether an account exists. `docs/EMAIL.md`. |
| Automated rent reminders | Uses `reminder_days_before`; per-owner opt-in (off for everyone); only residents who consented to WhatsApp; unique `(invoice, stage)` key prevents duplicates across retries/instances; stops once the invoice is paid; WhatsApp remains **mock**. Settings checkbox added. |
| Owner payment methods | Online/cash/UPI/bank transfer, optional static QR, per-hostel override. Bank account number AES-256-GCM encrypted (bound to owner), masked in settings, audit stores field names only, full details only to a resident with a valid link for an unpaid invoice and to staff with `PAYMENT_VIEW`. Online can be switched off (403 `ONLINE_PAYMENTS_OFF`). The UI and API say a static QR/UPI ID/bank transfer is **not** auto-reconciled. `docs/PAYMENT_METHODS.md`. |

### K2. Test results (actually run)

* Backend `mvn test`: **211 run, 0 failures, 0 errors, 0 skipped** (new: NetRevenue 4, Mail 22, RentReminder 28, PaymentMethod 17).
* Frontend `yarn test`: **15 logic + 126 Jest (12 suites) pass**; `SettingsPage.test.js` (2 tests) was added afterwards and passes when run on its own (not re-run in the full suite).
* Production build (`yarn build`): compiled successfully.
* Migration rehearsal V5-V10 on a restored copy: succeeded (section 8 of the migration doc).
* **Not done:** no browser was available in this session, so nothing was clicked through in a real browser; UI verification is component-level (jsdom) only. No real e-mail, WhatsApp or payment was sent or initiated.

### K3. Assumptions

1. Net revenue does not deduct deposit refunds (money held for the resident, not income). Say if you want them deducted.
2. Without `DATA_ENCRYPTION_KEY`: dev derives a key from `JWT_SECRET`; prod refuses to store bank numbers. Losing the key makes saved numbers unreadable (re-enter).
3. Only `SETTINGS_MANAGE` users (and owners) edit payment methods; `PAYMENT_VIEW` users can read the full details to share them.

### K4. Remaining gaps

* No staff "copy payment details" button yet (API `GET /api/payment-settings/instructions` exists).
* Not started: Phase 5 (backup scripts, scheduler locking for billing, per-admission billing transactions), Phase 6 (Razorpay Test Mode needs your keys), Phase 7 (bulk import, proration, reports, i18n, gateway refunds).
* Earlier known gaps in J5 still apply (reservation guest names visible with `HOSTEL_VIEW`, email-exists message, WhatsApp opt-out handling, etc.).

### K5. Needs your decision

1. Approve applying V5-V10 to `hostel_management_db` (and set `DATA_ENCRYPTION_KEY` / `MAIL_*` first if wanted)?
2. Net revenue and deposit refunds (K3.1) as assumed?
3. Which SMTP provider, and your Razorpay Test keys when ready for Phase 6?
