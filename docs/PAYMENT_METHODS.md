# Owner payment methods (cash, UPI, QR, bank transfer, online)

Settings -> **Payment methods** lets an owner decide how residents may pay. One default applies to all hostels; a hostel can
have its own override (and can go back to following the default). Needs the `SETTINGS_MANAGE` permission.

| Method | What it is | Confirmed how |
|--------|-----------|---------------|
| Online | Gateway checkout / pay link (mock or Razorpay) | Automatically, after the provider confirms |
| Cash | Paid at the office | Staff records the payment |
| UPI (manual) | UPI ID and optional **static** QR picture | Staff records the payment after seeing the money |
| Bank transfer (manual) | Holder, bank, IFSC, account number | Staff records the payment after seeing the money |

## Important: a static QR or UPI ID is not auto-reconciled

A UPI ID, QR picture or bank account only tells a resident where to send money. It carries no invoice reference, and
HostelHub cannot see the owner's bank. Payments made this way are **not detected**; staff must record them. This is shown
to owners in Settings, to residents on the pay page, and in every API response that carries payment instructions. Only the
online gateway flow is verified and applied automatically.

## Protecting sensitive data

* Bank account numbers are encrypted with AES-256-GCM and bound to the owner, so the ciphertext cannot be moved to another owner's row. Only the last four digits are stored in clear text for display.
* The settings endpoints never return the number (write-only; leave it blank to keep, send an empty value to remove).
* The full number is returned only to (a) a resident holding a valid pay link for an unpaid invoice and (b) staff with `PAYMENT_VIEW` for that hostel. Once the invoice is paid it is no longer exposed.
* The audit log records which fields changed, never their values.
* The QR image is stored outside the web root under a random name; only JPG/PNG/WEBP verified by file signature, max 5 MB. Replacing or removing it deletes the old file.
* Turning **Online** off blocks gateway checkout and new pay links for that scope (HTTP 403 `ONLINE_PAYMENTS_OFF`); manual details are still shown.

## Server key

Set `DATA_ENCRYPTION_KEY` (Base64 of 32 random bytes):

```
openssl rand -base64 32
```

* Development without a key: a key derived from `JWT_SECRET` is used so the feature works, with a warning in the log.
* Production without a key: bank account numbers **cannot be saved** (the API answers 503 `ENCRYPTION_UNAVAILABLE`); UPI/QR/cash still work.
* Keep the key in your secret store and back it up separately from database backups. If it is lost or changed, saved account numbers can no longer be decrypted and the owner must re-enter them (key rotation is not automated).

## API

`GET/PUT/DELETE /api/payment-settings[?hostelId=]`, `POST/DELETE /api/payment-settings/qr`, `GET /api/payment-settings/instructions`
(staff, full details), public: `GET /api/public/pay/{token}` (field `manualPayment`) and `GET /api/public/pay/{token}/qr`.

## Database

`V10__payment_methods.sql` adds table `payment_method_settings` only (additive). See `docs/MIGRATION_V5_TO_V10.md`.
