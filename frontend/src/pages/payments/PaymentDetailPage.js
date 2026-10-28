import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { paymentApi } from '../../services/endpoints';
import { errorMessage, openFile } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { methodLabel } from '../../components/payments/financeUtils';
import { date, dateTime, label, money, shortDate, whatsappLink } from '../../utils/format';
import { INVOICE_STATUS } from '../../utils/status';
import Icon from '../../components/common/Icon';

function receiptText(p, hostelName) {
  const lines = [
    `Payment receipt ${p.receiptNumber}`,
    hostelName,
    `Received ${money(p.amount)} from ${p.residentName} on ${date(p.paymentDate)} by ${methodLabel(p.method)}.`,
  ];
  if (p.transactionReference) lines.push(`Reference: ${p.transactionReference}`);
  if (p.allocations?.length) {
    lines.push('Applied to:');
    p.allocations.forEach((a) => lines.push(`• ${a.invoiceNumber}${a.periodStart ? ` (${shortDate(a.periodStart)} – ${shortDate(a.periodEnd)})` : ''}: ${money(a.amount)}`));
  }
  if (Number(p.unallocatedAmount) > 0) lines.push(`Advance credit: ${money(p.unallocatedAmount)}`);
  lines.push('Thank you!');
  return lines.filter(Boolean).join('\n');
}

export default function PaymentDetailPage() {
  const { id } = useParams();
  const { can } = useAuth();
  const { notify } = useToast();
  const { data: receipt, setData, loading, error, reload } = useApi(() => paymentApi.receipt(id), [id]);
  const [confirmReverse, setConfirmReverse] = useState(false);
  const [pdfBusy, setPdfBusy] = useState(false);
  const p = receipt?.payment;

  useEffect(() => { document.title = p ? `Receipt ${p.receiptNumber} · HostelHub` : 'Payment · HostelHub'; }, [p]);

  if (loading && !receipt) return <LoadingState label="Loading receipt…" />;
  if (error) {
    return error.response?.status === 404
      ? <div className="hh-card"><EmptyState icon="bi-receipt" title="Payment not found" action={<Link to="/payments?tab=payments" className="btn btn-light">Back to payments</Link>} /></div>
      : <ErrorState error={error} onRetry={reload} />;
  }
  if (!p) return null;

  const reversed = p.status === 'REVERSED';
  const isAdjustment = p.method === 'DEPOSIT_ADJUSTMENT';

  const downloadPdf = async () => {
    setPdfBusy(true);
    try { await openFile(`/payments/${p.id}/receipt.pdf`); } catch (e) { notify(errorMessage(e, 'Could not open the PDF receipt.'), 'danger'); } finally { setPdfBusy(false); }
  };

  const reverse = async (reason) => {
    try {
      const updated = await paymentApi.reverse(p.id, reason.trim());
      setData((r) => ({ ...r, payment: updated }));
      setConfirmReverse(false);
      notify(`Payment ${p.receiptNumber} reversed. The invoices it paid are open again.`, 'warning');
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };

  return (
    <>
      <div className="no-print">
        <PageHeader title={`Receipt ${p.receiptNumber}`} icon="bi-receipt"
          subtitle={<>{p.residentName} · {date(p.paymentDate)} · <StatusBadge map={INVOICE_STATUS} value={p.status} /></>}
          actions={<>
            <Link to="/payments?tab=payments" className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />Payments</Link>
            <button type="button" className="btn btn-light" onClick={() => window.print()}><Icon className="bi bi-printer me-1" />Print</button>
            <button type="button" className="btn btn-light" onClick={downloadPdf} disabled={pdfBusy}>
              {pdfBusy ? <span className="spinner-border spinner-border-sm me-1" /> : <Icon className="bi bi-file-earmark-pdf me-1" />}Download PDF
            </button>
            {!reversed && p.residentMobile && (
              <a className="btn btn-whatsapp" href={whatsappLink(p.residentMobile, receiptText(p, p.hostelName))} target="_blank" rel="noreferrer"
                title="Opens WhatsApp on this device with the receipt text; you send it yourself">
                <Icon className="bi bi-whatsapp me-1" />Share on WhatsApp
              </a>
            )}
            {!reversed && can('PAYMENT_ADJUST') && (
              <button type="button" className="btn btn-outline-danger" onClick={() => setConfirmReverse(true)}>
                <Icon className="bi bi-arrow-counterclockwise me-1" />Reverse
              </button>
            )}
          </>} />
      </div>

      {reversed && (
        <div className="alert alert-danger d-flex gap-2 align-items-start" role="alert">
          <Icon className="bi bi-x-octagon-fill fs-5" aria-hidden="true" />
          <div><strong>This payment was reversed and is no longer counted.</strong>
            {p.reversalReason && <div>Reason: {p.reversalReason}</div>}</div>
        </div>
      )}

      <article className="hh-card mx-auto" style={{ maxWidth: 820 }} aria-label="Payment receipt">
        <header className="d-flex justify-content-between flex-wrap gap-3 pb-3 mb-3 border-bottom">
          <div className="min-w-0">
            <h2 className="h5 fw-bold mb-1">{p.hostelName}</h2>
            {receipt.hostelAddress && <div className="small text-muted-hh">{receipt.hostelAddress}</div>}
            {receipt.hostelContact && <div className="small text-muted-hh">{receipt.hostelContact}</div>}
            {receipt.ownerName && <div className="small text-muted-hh">{receipt.ownerName}</div>}
          </div>
          <div className="text-sm-end">
            <div className="text-uppercase small fw-bold text-muted-hh">{isAdjustment ? 'Deposit adjustment' : 'Payment receipt'}</div>
            <div className="fw-bold">{p.receiptNumber}</div>
            <div className="small">{date(p.paymentDate)}</div>
            <div className="mt-1"><StatusBadge map={INVOICE_STATUS} value={p.status} /></div>
          </div>
        </header>

        <div className="row g-3 mb-3">
          <div className="col-sm-7">
            <div className="small text-muted-hh">Received from</div>
            <div className="fw-semibold"><Link to={`/residents/${p.residentId}`} className="text-reset">{p.residentName}</Link></div>
            <div className="small">{[p.residentMobile, p.roomNumber && `Room ${p.roomNumber}${p.bedLabel ? ` / Bed ${p.bedLabel}` : ''}`].filter(Boolean).join(' · ')}</div>
          </div>
          <div className="col-sm-5 text-sm-end">
            <div className="small text-muted-hh">Amount</div>
            <div className={`fs-3 fw-bold ${reversed ? 'text-decoration-line-through text-muted-hh' : ''}`}>{money(p.amount)}</div>
            <div className="small">{methodLabel(p.method)}{p.transactionReference && ` · Ref ${p.transactionReference}`}</div>
          </div>
        </div>

        <h3 className="h6 fw-bold">Applied to</h3>
        {p.allocations?.length ? (
          <div className="table-responsive">
            <table className="table hh-table align-middle mb-2">
              <thead><tr><th>Invoice</th><th>Type</th><th>Period</th><th className="text-end">Amount</th></tr></thead>
              <tbody>
                {p.allocations.map((a) => (
                  <tr key={a.invoiceId}>
                    <td><Link to={`/invoices/${a.invoiceId}`}>{a.invoiceNumber}</Link></td>
                    <td>{label(a.invoiceType)}</td>
                    <td>{a.periodStart ? `${date(a.periodStart)} – ${date(a.periodEnd)}` : '—'}</td>
                    <td className="text-end">{money(a.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <p className="small text-muted-hh">Not applied to any invoice.</p>}

        <dl className="hh-kv mt-2">
          {Number(p.unallocatedAmount) > 0 && (<><dt>Advance credit</dt><dd className="fw-semibold">{money(p.unallocatedAmount)} <span className="small text-muted-hh">(kept for future dues)</span></dd></>)}
          {p.notes && (<><dt>Notes</dt><dd>{p.notes}</dd></>)}
          <dt>Recorded by</dt><dd>{p.recordedByName || '—'}{p.createdAt && <span className="text-muted-hh"> · {dateTime(p.createdAt)}</span>}</dd>
        </dl>

        <footer className="small text-muted-hh border-top pt-2 mt-3">
          {isAdjustment
            ? 'Settled from the security deposit held; no new money was received.'
            : 'This receipt confirms money actually received by the hostel.'}
        </footer>
      </article>

      <ConfirmDialog show={confirmReverse} title={`Reverse ${p.receiptNumber}?`} variant="danger" confirmLabel="Reverse payment"
        requireReason reasonLabel="Why is this payment being reversed?"
        message={`Use this only if the ${money(p.amount)} was not actually received (e.g. a bounced transfer or a mistake). The invoices it paid will become unpaid again. This is kept in the audit history.`}
        onConfirm={reverse} onClose={() => setConfirmReverse(false)} />
    </>
  );
}
