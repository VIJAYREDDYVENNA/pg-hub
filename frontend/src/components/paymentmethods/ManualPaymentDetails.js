import Icon from '../common/Icon';

/**
 * How to pay without the online button. `qrSrc` is optional; the public page passes the token-scoped QR URL.
 * `note` comes from the server so the wording is the same everywhere: a QR or UPI ID is not automatic confirmation.
 */
export default function ManualPaymentDetails({ info, qrSrc, invoiceNumber, heading = 'Other ways to pay' }) {
  if (!info || (!info.cash && !info.upi && !info.bank)) return null;
  return (
    <section className="border rounded p-3 mt-3" aria-label="Manual payment details" data-testid="manual-payment">
      <h2 className="h6 mb-2">{heading}</h2>
      {info.upi && (
        <div className="mb-3">
          <div className="fw-semibold"><Icon className="bi bi-qr-code me-2" />UPI</div>
          <div>UPI ID: <code data-testid="upi-id">{info.upi.upiId}</code></div>
          {info.upi.payeeName && <div className="small text-muted-hh">Payee: {info.upi.payeeName}</div>}
          {info.upi.hasQr && qrSrc && <img src={qrSrc} alt="UPI QR code" className="mt-2 border rounded d-block" style={{ maxWidth: 200 }} />}
        </div>
      )}
      {info.bank && (
        <div className="mb-3">
          <div className="fw-semibold"><Icon className="bi bi-bank me-2" />Bank transfer</div>
          <dl className="hh-kv mb-0">
            <dt>Account holder</dt><dd>{info.bank.accountHolder}</dd>
            {info.bank.bankName && (<><dt>Bank</dt><dd>{info.bank.bankName}{info.bank.branch ? `, ${info.bank.branch}` : ''}</dd></>)}
            <dt>Account number</dt><dd data-testid="bank-account">{info.bank.accountNumber}</dd>
            <dt>IFSC</dt><dd>{info.bank.ifsc}</dd>
          </dl>
        </div>
      )}
      {info.cash && <div className="mb-2"><Icon className="bi bi-cash-coin me-2" />Cash is accepted at the hostel office.</div>}
      {invoiceNumber && (info.upi || info.bank) && (
        <div className="small mb-2">Mention invoice <strong>{invoiceNumber}</strong> in the payment note and keep your transaction reference.</div>
      )}
      {info.instructions && <div className="small mb-2" style={{ whiteSpace: 'pre-wrap' }}>{info.instructions}</div>}
      {info.note && (
        <div className="alert alert-info py-2 small mb-0 d-flex gap-2" role="note">
          <Icon className="bi bi-info-circle mt-1" /><div>{info.note}</div>
        </div>
      )}
    </section>
  );
}
