# Online payments

Online collection is layered on the existing invoice/payment ledger. A verified gateway payment becomes an ordinary
`payments` row (method `ONLINE`) plus `payment_allocations` through `PaymentService.record()`, so receipts, reversal,
resident credit, reports and audit all keep working unchanged.

## Architecture

```
React (PayPanel)  ->  OnlinePaymentController / PublicPayController / PaymentWebhookController
                                  |
                          OnlinePaymentService      (authorise, validate, call provider, no DB lock held)
                                  |
                      PaymentAttemptProcessor       (@Transactional; the ONLY place balances change)
                                  |                         |
                      PaymentService.record()      PaymentGatewayService  <- provider adapters
```

| Piece | Where |
|---|---|
| Provider contract | `payments/gateway/PaymentGatewayService.java` |
| Adapters | `MockPaymentGatewayService`, `RazorpayPaymentGatewayService` |
| Provider selection | `PaymentGatewayConfig` (single switch on `app.payment.provider`) |
| Business logic | `payments/online/*` (never imports a provider class) |
| Tables | `payment_attempts`, `payment_links`, `payment_webhook_events` (migration `V5__online_payments.sql`) |

Rules the code enforces:

* The browser's "success" is never trusted. `verifyCheckout` checks the signature **and** fetches the payment from the
  provider; only `captured` counts as money received.
* An attempt is settled under a row lock (attempt, then invoice). Webhook, checkout confirmation and the reconciler all
  go through `PaymentAttemptProcessor.apply`, so any mix of duplicates or races applies a payment once.
* Webhooks: signature over the raw bytes, then an `INSERT IGNORE` into `payment_webhook_events (provider, event_id)`.
  A redelivery is acknowledged (HTTP 200, `DUPLICATE_EVENT`) and not re-applied.
* Amount/currency/owner/hostel/resident must match the attempt; otherwise the attempt becomes `MISMATCH`, nothing is
  applied and an audit entry is written for manual review.
* Pending and failed attempts never change an invoice. `FAILED` is not terminal (a later capture on the same order is
  still applied); `SUCCEEDED` and `MISMATCH` are.
* If an invoice was settled in the meantime (e.g. cash), a captured payment is still recorded and the surplus is held as
  the resident's credit, exactly like a staff-recorded overpayment.
* Only one `PENDING` attempt per invoice at a time (24 h); abandoned `CREATED` attempts expire after 24 h.
* Partial payments: minimum `PAYMENT_MIN_PARTIAL_AMOUNT` (default 100), or the whole balance when it is lower.
* Resident pay links: 256-bit random token, only its SHA-256 is stored, default 7-day expiry, a new link revokes the
  previous one, 30 requests/min per client IP. A link can only create/confirm/refresh attempts that it created itself.

## Run in mock mode (default)

```
cd backend && mvn spring-boot:run            # PAYMENT_PROVIDER defaults to mock
cd frontend && yarn start
```

Open any open invoice > **Online payment** > *Collect online*. A simulated checkout lets you pick success, failure, or
"bank is slow" (pending; resolve it with *Simulate bank success/failure* or *Check status*). *Create payment link* gives
a `/pay/<token>` URL that works without signing in. The mock refuses to start under the `prod` profile.

## Run with Razorpay Test Mode

1. Create a Razorpay account and stay in **Test Mode** (toggle in the dashboard).
2. Dashboard > Account & Settings > API Keys > generate a *test* key pair.
3. Put these in `backend/.env` (git-ignored; never paste them into chat or commit them):
   ```
   PAYMENT_PROVIDER=razorpay
   RAZORPAY_KEY_ID=rzp_test_xxxxxxxx
   RAZORPAY_KEY_SECRET=xxxxxxxx
   ```
4. Restart the backend. Startup fails with a clear message if a key is missing, malformed, or a live key is used
   outside the `prod` profile. It never falls back to mock.
5. Pay with Razorpay's published test cards / test UPI IDs (https://razorpay.com/docs/payments/payments/test-card-details/).
6. Webhooks (optional but recommended; checkout verification and *Check status* work without them):
   * Expose the backend (for example `ngrok http 8080`).
   * Dashboard > Webhooks > URL `https://<public-host>/api/payments/webhook/razorpay`, choose a secret, enable
     `payment.captured`, `payment.failed`, `order.paid`.
   * Add `RAZORPAY_WEBHOOK_SECRET=<that secret>` and restart. Until it is set, webhooks get HTTP 503.

## Adding another provider (PhonePe, Cashfree, PayU, ...)

1. Implement `PaymentGatewayService` in `payments/gateway/` (for its REST API, signature scheme and webhook format).
   Map the provider's statuses to `SUCCEEDED / FAILED / PENDING`; only a confirmed capture may be `SUCCEEDED`.
   Return browser checkout parameters from `createOrder` (`checkout` map, no secrets, `provider` = your code).
2. Add settings to `AppProperties.Payment`, entries in `application.yml` / `.env.example`, and a `case` in
   `PaymentGatewayConfig` that validates them (fail fast).
3. Frontend: handle the new `checkout.provider` in `PayPanel.start` next to `openRazorpay` (load the provider's checkout script and call
   `api.confirm(orderId, fieldsTheProviderReturns)`). This is the only React code that is provider specific.
4. Write adapter tests like `RazorpayPaymentGatewayServiceTest` (mocked HTTP; signature maths; status mapping).
5. Set `PAYMENT_PROVIDER`, configure the provider's webhook to `/api/payments/webhook/<code>`, run the suite.

What does **not** change: invoices, `PaymentService`, `PaymentAttemptProcessor`, tables, controllers, pay links.
What can differ per provider and needs real work: checkout UX (redirect vs. overlay), webhook payloads/retries, partial
payment support (`Capabilities.partialPayments`), refunds and settlement/split payouts. Attempts keep the provider code
they were created with; after switching, wait for open attempts to settle (the reconciler only handles the active
provider).

## Tests

`mvn test` in `backend/` (needs MySQL as in `.env`; integration tests use the throw-away schema
`TEST_DB_NAME`, default `hostel_management_test`, which they **drop and recreate**. The runner refuses any schema not
ending in `_test`).

* `RazorpayPaymentGatewayServiceTest`, `PaymentGatewayConfigTest`: unit tests with mocked HTTP.
* `OnlinePaymentFlowTest`, `OnlinePaymentSecurityTest`: end-to-end through controllers, security chain and MySQL with the
  mock gateway (success, failure, pending, partial, duplicate and concurrent webhooks, invalid signature, amount and
  currency mismatch, unknown order, reconciliation, tenant/manager isolation, pay-link scoping).

Not covered by automation: real Razorpay Test Mode calls (need your keys), the browser checkout UI, WhatsApp delivery.

## Known limitations

* Online refunds through the gateway are not implemented; existing manual refund/reversal workflows are unchanged.
  Reversing an `ONLINE` payment in the app does not refund the customer at the provider.
* Settlement: a single merchant account settles to one bank account. Per-owner payouts need provider marketplace/route
  features that require explicit provider approval; keep that separate from this module.
* WhatsApp: pay links can be created and shared manually; automatic reminders containing links are not wired up.
* Pay-link rate limiting is in-memory (single instance).
