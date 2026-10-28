# E-mail (password reset)

HostelHub sends e-mail through a provider-independent layer (`com.hostelmanagement.mail`). Nothing is tied to one vendor:
any SMTP server works. Credentials come from environment variables only and are never committed.

## Modes (`MAIL_MODE`)

| Mode | Behaviour |
|------|-----------|
| `LOG` (default) | The message is written to the server log; nothing is sent. Safe for development. **Refused under the `prod` profile.** |
| `SMTP` | Sent through `MAIL_HOST:MAIL_PORT`. Start-up fails with a clear message if host/from/credentials are missing. |
| `DISABLED` | Nothing is sent. |

## Variables

`MAIL_MODE`, `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME`,
`MAIL_TLS` (`NONE` | `STARTTLS` | `SSL`), `MAIL_AUTH`, `MAIL_TIMEOUT_MS`. Put them in `backend/.env` (git-ignored).
`FRONTEND_BASE_URL` must be the public address of the app because it is used to build the reset link.

Example (any provider; use that provider's SMTP host and an app password / SMTP key):

```
MAIL_MODE=SMTP
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_TLS=STARTTLS
MAIL_USERNAME=<smtp user>
MAIL_PASSWORD=<smtp password or key>
MAIL_FROM=no-reply@your-domain.example
```

## Password-reset behaviour

* `POST /api/auth/forgot-password` always answers the same generic message and takes the same time whether or not the
  account exists (the e-mail is sent asynchronously, after the database transaction commits), so it cannot be used to find
  out which e-mail addresses are registered.
* The e-mail contains a single-use link with a random token that expires after the configured time; only a hash of the token is stored.
* A delivery failure is logged (without the token or password) and never shown to the caller.
* In `LOG` mode the full message, including the link, appears in the server log so you can test locally.

## Testing

Unit and integration tests use GreenMail (an in-process SMTP server) - no real mail is ever sent. To try a real provider
manually, set the variables above in `.env`, start the backend and use *Forgot password* with an address you control.
