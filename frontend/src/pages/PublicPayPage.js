import { useEffect, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import useApi from '../hooks/useApi';
import { publicPayApi } from '../services/endpoints';
import Icon from '../components/common/Icon';
import StatusBadge from '../components/common/StatusBadge';
import { LoadingState } from '../components/common/States';
import PayPanel from '../components/payments/PayPanel';
import ManualPaymentDetails from '../components/paymentmethods/ManualPaymentDetails';
import { date, dateTime, label, money } from '../utils/format';
import { INVOICE_STATUS } from '../utils/status';

/** Resident-facing payment page opened from a pay link. No sign-in: the link itself selects the one invoice. */
export default function PublicPayPage() {
  const { token } = useParams();
  const { data: inv, loading, error, reload } = useApi(() => publicPayApi.invoice(token), [token]);
  const api = useMemo(() => publicPayApi.forLink(token), [token]);

  useEffect(() => { document.title = 'Pay your invoice · HostelHub'; }, []);

  return (
    <main className="container py-4" style={{ maxWidth: 520 }}>
      <div className="d-flex align-items-center gap-2 mb-3">
        <Icon className="bi bi-shield-check text-primary-hh fs-4" />
        <span className="fw-bold fs-5">HostelHub</span>
      </div>

      {loading && !inv && <LoadingState label="Loading your invoice…" />}

      {error && (
        <div className="hh-card text-center py-4" role="alert">
          <Icon className="bi bi-x-circle fs-1 text-muted-hh" />
          <h1 className="h5 mt-2">This payment link is not valid</h1>
          <p className="text-muted-hh mb-0">
            {error.response?.status === 429
              ? 'Too many requests. Please wait a minute and try again.'
              : 'It may have expired or been replaced. Please ask your hostel for a new link.'}
          </p>
        </div>
      )}

      {inv && (
        <div className="hh-card">
          <h1 className="h5 mb-1">{inv.hostelName}</h1>
          <div className="text-muted-hh mb-3">
            {inv.residentFirstName ? `Hi ${inv.residentFirstName}, ` : ''}invoice {inv.invoiceNumber} · {label(inv.invoiceType)}
          </div>
          <dl className="hh-kv mb-3">
            <dt>Status</dt><dd><StatusBadge map={INVOICE_STATUS} value={inv.status} /></dd>
            <dt>Due date</dt><dd>{date(inv.dueDate)}</dd>
            <dt>Invoice total</dt><dd>{money(inv.totalAmount)}</dd>
            <dt>Already paid</dt><dd>{money(inv.amountPaid)}</dd>
            <dt>Balance</dt><dd className="fw-bold">{money(inv.balance)}</dd>
          </dl>

          {inv.payable ? (
            <>
              {inv.manualPayment?.online !== false ? (
                <PayPanel api={api} balance={inv.balance} gateway={inv.gateway} onChanged={reload} onPaid={reload} />
              ) : (
                <div className="alert alert-secondary py-2" role="status">Online payment is not available for this hostel. Use one of the options below.</div>
              )}
              <ManualPaymentDetails info={inv.manualPayment} qrSrc={publicPayApi.qrUrl(token)} invoiceNumber={inv.invoiceNumber} />
            </>
          ) : (
            <div className="alert alert-success mb-0" role="status">
              <Icon className="bi bi-check-circle-fill me-2" />Nothing is due on this invoice. Thank you!
            </div>
          )}
          <div className="small text-muted-hh mt-3">Link valid until {dateTime(inv.linkExpiresAt)}.</div>
        </div>
      )}
    </main>
  );
}
