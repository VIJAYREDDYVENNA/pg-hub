import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { invoiceApi, whatsappApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import Modal from '../../components/common/Modal';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import ContactButtons from '../../components/common/WhatsAppButtons';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { isOpenInvoice, methodLabel, toPaise } from '../../components/payments/financeUtils';
import OnlinePaymentPanel from '../../components/payments/OnlinePaymentPanel';
import { date, dateTime, label, money } from '../../utils/format';
import { INVOICE_STATUS, MESSAGE_STATUS } from '../../utils/status';
import Icon from '../../components/common/Icon';

const ADJUST_TYPES = [
  { value: 'DISCOUNT', label: 'Discount', icon: 'bi-tag', help: 'Reduces what the resident owes.' },
  { value: 'LATE_FEE', label: 'Late fee', icon: 'bi-alarm', help: 'Adds a late payment fee.' },
  { value: 'CHARGE', label: 'Extra charge', icon: 'bi-plus-circle', help: 'Adds another charge to this invoice.' },
];

function AdjustModal({ show, type, invoice, onClose, onDone }) {
  const { notify } = useToast();
  const [error, setError] = useState(null);
  const meta = ADJUST_TYPES.find((t) => t.value === type) || ADJUST_TYPES[0];
  const { register, handleSubmit, reset, setError: setFieldError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { amount: '', description: '' },
  });
  useEffect(() => { if (show) { reset({ amount: '', description: meta.label }); setError(null); } }, [show, meta.label, reset]);

  const submit = async (v) => {
    setError(null);
    try {
      const updated = await invoiceApi.adjust(invoice.id, { type, amount: Number(v.amount), description: v.description.trim() });
      notify(`${meta.label} of ${money(v.amount)} applied. New balance ${money(updated.balance)}.`);
      onDone(updated);
    } catch (e) {
      if (!applyServerErrors(e, setFieldError)) setError(errorMessage(e));
    }
  };

  return (
    <Modal show={show} title={`Add ${meta.label.toLowerCase()} to ${invoice?.invoiceNumber || ''}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="adjust-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Apply {meta.label.toLowerCase()}
        </button>
      </>}>
      <form id="adjust-form" onSubmit={handleSubmit(submit)} noValidate>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        <p className="small text-muted-hh">{meta.help} Current balance: <strong>{money(invoice?.balance)}</strong>.</p>
        <FormField label="Amount (₹)" name="amount" type="number" inputMode="decimal" step="0.01" min="0.01" register={register} errors={errors}
          rules={{
            required: 'Enter an amount',
            validate: (x) => {
              if (!(toPaise(x) >= 1)) return 'Amount must be more than zero';
              if (type === 'DISCOUNT' && toPaise(x) > toPaise(invoice?.balance)) return `Discount cannot exceed the balance of ${money(invoice?.balance)}`;
              return true;
            },
          }} />
        <FormField label="Description" name="description" register={register} errors={errors}
          rules={{ required: 'Enter a description', maxLength: { value: 255, message: 'Keep it under 255 characters' } }} />
      </form>
    </Modal>
  );
}

function ReminderResult({ msg }) {
  if (!msg) return null;
  if (msg.status === 'SKIPPED_NO_CONSENT') {
    return (
      <div className="alert alert-warning py-2 d-flex gap-2 align-items-center" role="status">
        <Icon className="bi bi-shield-exclamation" aria-hidden="true" />
        Reminder not sent: the resident has not opted in to WhatsApp messages.
      </div>
    );
  }
  return (
    <div className={`alert ${msg.status === 'FAILED' ? 'alert-danger' : 'alert-light border'} py-2 d-flex gap-2 align-items-center flex-wrap`} role="status">
      <span>WhatsApp reminder:</span>
      <StatusBadge map={MESSAGE_STATUS} value={msg.status} />
      {msg.simulated && <span className="badge hh-badge hh-badge-simulated"><Icon className="bi bi-cone-striped me-1" aria-hidden="true" />Simulated</span>}
      {msg.simulated && <span className="small text-muted-hh">WhatsApp is in test mode – no real message was delivered.</span>}
      {msg.errorMessage && <span className="small text-danger">{msg.errorMessage}</span>}
      <span className="small text-muted-hh ms-auto">{dateTime(msg.createdAt)}</span>
    </div>
  );
}

export default function InvoiceDetailPage() {
  const { id } = useParams();
  const { can } = useAuth();
  const { notify } = useToast();
  const { data: inv, setData, loading, error, reload } = useApi(() => invoiceApi.get(id), [id]);
  const [adjustType, setAdjustType] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [reminder, setReminder] = useState(null);
  const [reminding, setReminding] = useState(false);

  useEffect(() => { document.title = inv ? `Invoice ${inv.invoiceNumber} · HostelHub` : 'Invoice · HostelHub'; }, [inv]);
  useEffect(() => { setReminder(null); }, [id]);

  if (loading && !inv) return <LoadingState label="Loading invoice…" />;
  if (error) {
    return error.response?.status === 404
      ? <div className="hh-card"><EmptyState icon="bi-receipt" title="Invoice not found" action={<Link to="/payments?tab=invoices" className="btn btn-light">Back to invoices</Link>} /></div>
      : <ErrorState error={error} onRetry={reload} />;
  }
  if (!inv) return null;

  const open = isOpenInvoice(inv);
  const canAdjust = can('PAYMENT_ADJUST') && open;

  const sendReminder = async () => {
    setReminding(true);
    try {
      const msg = await whatsappApi.invoiceReminder(inv.id);
      setReminder(msg);
    } catch (e) {
      notify(errorMessage(e), 'danger');
    } finally {
      setReminding(false);
    }
  };

  const runConfirm = async (reason) => {
    try {
      const updated = confirm === 'waive' ? await invoiceApi.waive(inv.id, reason.trim()) : await invoiceApi.cancel(inv.id, reason.trim());
      setData(updated);
      notify(`Invoice ${inv.invoiceNumber} ${confirm === 'waive' ? 'waived' : 'cancelled'}.`, 'warning');
      setConfirm(null);
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };

  const totals = [
    { k: 'Subtotal', v: money(inv.subtotal) },
    Number(inv.discountAmount) > 0 && { k: 'Discount', v: `− ${money(inv.discountAmount)}`, cls: 'text-success' },
    Number(inv.lateFeeAmount) > 0 && { k: 'Late fee', v: `+ ${money(inv.lateFeeAmount)}` },
    { k: 'Total', v: money(inv.totalAmount), cls: 'fw-bold' },
    { k: 'Paid', v: money(inv.amountPaid) },
    { k: 'Balance due', v: money(inv.balance), cls: `fw-bold fs-5 ${Number(inv.balance) > 0 ? 'text-danger' : 'text-success'}` },
  ].filter(Boolean);

  return (
    <>
      <div className="no-print">
        <PageHeader title={`Invoice ${inv.invoiceNumber}`} icon="bi-receipt"
          subtitle={<>{label(inv.invoiceType)} · {inv.residentName} · <StatusBadge map={INVOICE_STATUS} value={inv.status} /></>}
          actions={<>
            <Link to="/payments?tab=invoices" className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />Invoices</Link>
            <button type="button" className="btn btn-light" onClick={() => window.print()}><Icon className="bi bi-printer me-1" />Print</button>
            {open && can('NOTIFICATION_SEND') && (
              <button type="button" className="btn btn-light" onClick={sendReminder} disabled={reminding}
                title="Sends a WhatsApp reminder only if the resident opted in">
                {reminding ? <span className="spinner-border spinner-border-sm me-1" /> : <Icon className="bi bi-whatsapp me-1" />}Send reminder
              </button>
            )}
            {open && can('PAYMENT_COLLECT') && (
              <Link to={`/payments/collect?residentId=${inv.residentId}`} className="btn btn-primary"><Icon className="bi bi-cash-coin me-1" />Collect</Link>
            )}
          </>} />
        <ReminderResult msg={reminder} />
      </div>

      <div className="row g-3">
        <div className="col-lg-8">
          <article className="hh-card" aria-label="Invoice">
            <header className="d-flex justify-content-between flex-wrap gap-3 pb-3 mb-3 border-bottom">
              <div>
                <h2 className="h5 fw-bold mb-1">{inv.hostelName}</h2>
                <div className="small text-muted-hh">Invoice {inv.invoiceNumber} · issued {date(inv.issueDate)}</div>
              </div>
              <div className="text-sm-end">
                <StatusBadge map={INVOICE_STATUS} value={inv.status} />
                {inv.daysOverdue > 0 && <div className="small text-danger fw-semibold mt-1"><Icon className="bi bi-exclamation-circle me-1" />{inv.daysOverdue} day(s) overdue</div>}
              </div>
            </header>

            <dl className="hh-kv mb-3">
              <dt>Billed to</dt>
              <dd>
                <Link to={`/residents/${inv.residentId}`} className="fw-semibold">{inv.residentName}</Link>
                {inv.residentMobile && <span className="ms-2 no-print"><ContactButtons mobile={inv.residentMobile} /></span>}
                {inv.residentMobile && <div className="small text-muted-hh">{inv.residentMobile}</div>}
              </dd>
              {inv.roomNumber && (<><dt>Room / bed</dt><dd>Room {inv.roomNumber}{inv.bedLabel && ` / Bed ${inv.bedLabel}`}</dd></>)}
              {inv.periodStart && (<><dt>Period</dt><dd>{date(inv.periodStart)} – {date(inv.periodEnd)}</dd></>)}
              <dt>Due date</dt><dd className={inv.daysOverdue > 0 ? 'text-danger fw-semibold' : ''}>{date(inv.dueDate)}</dd>
              {inv.notes && (<><dt>Notes</dt><dd style={{ whiteSpace: 'pre-line' }}>{inv.notes}</dd></>)}
            </dl>

            <div className="table-responsive">
              <table className="table hh-table align-middle mb-3">
                <thead><tr><th>Item</th><th>Type</th><th className="text-end">Amount</th></tr></thead>
                <tbody>
                  {(inv.items || []).length === 0 && <tr><td colSpan={3} className="text-muted-hh">No line items.</td></tr>}
                  {(inv.items || []).map((it) => (
                    <tr key={it.id}>
                      <td>{it.description}</td>
                      <td className="small text-muted-hh">{label(it.itemType)}</td>
                      <td className={`text-end ${Number(it.amount) < 0 ? 'text-success' : ''}`}>{money(it.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="ms-auto" style={{ maxWidth: 340 }}>
              {totals.map((t) => (
                <div key={t.k} className="d-flex justify-content-between py-1 border-bottom">
                  <span className="text-muted-hh">{t.k}</span><span className={t.cls || ''}>{t.v}</span>
                </div>
              ))}
            </div>
          </article>
        </div>

        <div className="col-lg-4">
          <section className="hh-card mb-3" aria-labelledby="applied-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="applied-title"><Icon className="bi bi-cash-coin me-2 text-primary-hh" />Payments applied</h2></div>
            {(inv.payments || []).length === 0 ? (
              <p className="small text-muted-hh mb-0">No payments applied yet.</p>
            ) : inv.payments.map((pa) => (
              <Link key={`${pa.paymentId}`} to={`/payments/${pa.paymentId}`} className="hh-feed-item text-reset">
                <div className="min-w-0 flex-grow-1">
                  <div className="fw-semibold">{pa.receiptNumber}</div>
                  <div className="small text-muted-hh">{date(pa.paymentDate)} · {methodLabel(pa.method)}</div>
                </div>
                <div className="text-end">
                  <div className={`fw-bold ${pa.paymentStatus === 'REVERSED' ? 'text-decoration-line-through text-muted-hh' : ''}`}>{money(pa.amount)}</div>
                  {pa.paymentStatus === 'REVERSED' && <StatusBadge map={INVOICE_STATUS} value="REVERSED" />}
                </div>
              </Link>
            ))}
          </section>

          {can('PAYMENT_VIEW') && <OnlinePaymentPanel invoice={inv} onInvoiceChanged={reload} />}

          {canAdjust && (
            <section className="hh-card no-print" aria-labelledby="adjust-title">
              <div className="hh-card-header"><h2 className="hh-card-title" id="adjust-title"><Icon className="bi bi-sliders me-2 text-primary-hh" />Adjust</h2></div>
              <div className="d-grid gap-2">
                {ADJUST_TYPES.map((t) => (
                  <button key={t.value} type="button" className="btn btn-light text-start py-2" onClick={() => setAdjustType(t.value)}>
                    <Icon className={`bi ${t.icon} me-2`} />Add {t.label.toLowerCase()}
                  </button>
                ))}
                <button type="button" className="btn btn-light text-start py-2" onClick={() => setConfirm('waive')}>
                  <Icon className="bi bi-slash-circle me-2" />Waive balance
                </button>
                <button type="button" className="btn btn-outline-danger text-start py-2" onClick={() => setConfirm('cancel')}>
                  <Icon className="bi bi-x-circle me-2" />Cancel invoice
                </button>
              </div>
              <p className="small text-muted-hh mt-2 mb-0">All changes are kept in the audit history.</p>
            </section>
          )}
        </div>
      </div>

      <AdjustModal show={!!adjustType} type={adjustType} invoice={inv} onClose={() => setAdjustType(null)}
        onDone={(updated) => { setData(updated); setAdjustType(null); }} />
      <ConfirmDialog show={!!confirm} requireReason variant={confirm === 'cancel' ? 'danger' : 'primary'}
        title={confirm === 'waive' ? `Waive ${inv.invoiceNumber}?` : `Cancel ${inv.invoiceNumber}?`}
        confirmLabel={confirm === 'waive' ? 'Waive balance' : 'Cancel invoice'}
        message={confirm === 'waive'
          ? `The remaining balance of ${money(inv.balance)} will be written off and no longer counted as due.`
          : 'The invoice will be cancelled because it was raised by mistake. It will no longer count as due.'}
        onConfirm={runConfirm} onClose={() => setConfirm(null)} />
    </>
  );
}
