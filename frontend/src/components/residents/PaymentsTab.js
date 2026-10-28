import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import DataTable, { RowAction, RowActions } from '../common/DataTable';
import Pagination from '../common/Pagination';
import StatusBadge from '../common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../common/States';
import useApi from '../../hooks/useApi';
import { invoiceApi, paymentApi } from '../../services/endpoints';
import { useAuth } from '../../context/AuthContext';
import { INVOICE_STATUS, PAYMENT_METHODS } from '../../utils/status';
import { date, label, money } from '../../utils/format';
import { isOpenInvoice } from '../payments/financeUtils';
import Icon from '../common/Icon';

const methodLabel = (m) => PAYMENT_METHODS.find((x) => x.value === m)?.label || label(m);

/** Invoices and payments of one resident. */
export default function PaymentsTab({ residentId }) {
  const { can } = useAuth();
  const navigate = useNavigate();
  const [invPage, setInvPage] = useState(0);
  const [invSize, setInvSize] = useState(10);
  const [payPage, setPayPage] = useState(0);
  const [paySize, setPaySize] = useState(10);
  const inv = useApi(() => invoiceApi.list({ residentId, page: invPage, size: invSize }), [residentId, invPage, invSize]);
  const pay = useApi(() => paymentApi.list({ residentId, page: payPage, size: paySize }), [residentId, payPage, paySize]);
  const canCollect = can('PAYMENT_COLLECT');

  const invColumns = [
    { key: 'invoiceNumber', label: 'Invoice', required: true, sortValue: (i) => i.invoiceNumber, render: (i) => (
      <div>
        <Link to={`/invoices/${i.id}`} className="hh-cell-primary" onClick={(e) => e.stopPropagation()}>{i.invoiceNumber}</Link>
        <span className="hh-cell-sub">{label(i.invoiceType)}{i.periodStart ? ` · ${date(i.periodStart)} – ${date(i.periodEnd)}` : ''}</span>
      </div>
    ) },
    { key: 'dueDate', label: 'Due', sortValue: (i) => i.dueDate || '', render: (i) => (
      <div>{date(i.dueDate)}{i.daysOverdue > 0 && <span className="hh-cell-sub text-danger">{i.daysOverdue} day{i.daysOverdue === 1 ? '' : 's'} overdue</span>}</div>
    ) },
    { key: 'totalAmount', label: 'Amount', align: 'end', hideOnMobile: true, sortValue: (i) => Number(i.totalAmount), render: (i) => money(i.totalAmount) },
    { key: 'balance', label: 'Balance', align: 'end', sortValue: (i) => Number(i.balance), render: (i) => (
      <span className={Number(i.balance) > 0 ? (i.status === 'OVERDUE' ? 'text-danger fw-semibold' : 'fw-semibold') : 'text-muted-hh'}>{money(i.balance)}</span>
    ) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (i) => i.status, render: (i) => <StatusBadge map={INVOICE_STATUS} value={i.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (i) => (
      <RowActions>
        <RowAction variant="view" label={`View invoice ${i.invoiceNumber}`} onClick={() => navigate(`/invoices/${i.id}`)} />
        {canCollect && isOpenInvoice(i) && (
          <RowAction variant="pay" label="Collect payment" onClick={() => navigate(`/payments/collect?residentId=${residentId}`)} />
        )}
      </RowActions>
    ) },
  ];

  const payColumns = [
    { key: 'receiptNumber', label: 'Receipt', required: true, sortValue: (p) => p.receiptNumber, render: (p) => (
      <Link to={`/payments/${p.id}`} className="hh-cell-primary" onClick={(e) => e.stopPropagation()}>{p.receiptNumber}</Link>
    ) },
    { key: 'paymentDate', label: 'Date', sortValue: (p) => p.paymentDate || '', render: (p) => date(p.paymentDate) },
    { key: 'method', label: 'Method', sortValue: (p) => methodLabel(p.method), render: (p) => (
      <div>{methodLabel(p.method)}{p.transactionReference && <span className="hh-cell-sub">{p.transactionReference}</span>}</div>
    ) },
    { key: 'amount', label: 'Amount', align: 'end', sortValue: (p) => Number(p.amount), render: (p) => (
      <span className={p.status === 'REVERSED' ? 'text-decoration-line-through text-muted-hh' : 'fw-semibold'}>{money(p.amount)}</span>
    ) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (p) => p.status, render: (p) => <StatusBadge map={INVOICE_STATUS} value={p.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (p) => (
      <RowActions>
        <RowAction variant="view" label={`View receipt ${p.receiptNumber}`} onClick={() => navigate(`/payments/${p.id}`)} />
      </RowActions>
    ) },
  ];

  return (
    <>
      <section className="mb-3" aria-label="Invoices">
        {inv.error ? <ErrorState compact error={inv.error} onRetry={inv.reload} /> : (
          <DataTable tableId="resident-invoices" columns={invColumns} rows={inv.data?.content || []} serial
            serialStart={(inv.data?.page || 0) * (inv.data?.size || invSize)} onRowClick={(i) => navigate(`/invoices/${i.id}`)}
            toolbar={<>
              <span className="fw-semibold"><Icon className="bi bi-receipt me-2 text-primary-hh" aria-hidden="true" />Invoices</span>
              {canCollect && (
                <Link to={`/payments/collect?residentId=${residentId}`} className="btn btn-sm btn-primary no-print"><Icon className="bi bi-cash-coin me-1" />Collect payment</Link>
              )}
            </>}
            empty={inv.loading && !inv.data ? <LoadingState compact /> : <EmptyState icon="bi-receipt" title="No invoices yet" />}
            footer={inv.data && <Pagination page={inv.data.page} totalPages={inv.data.totalPages} totalElements={inv.data.totalElements} size={inv.data.size}
              noun="invoices" sizes={[10, 20, 50]} onChange={setInvPage} onSizeChange={(s) => { setInvSize(s); setInvPage(0); }} />} />
        )}
      </section>

      <section className="mb-3" aria-label="Payments">
        {pay.error ? <ErrorState compact error={pay.error} onRetry={pay.reload} /> : (
          <DataTable tableId="resident-payments" columns={payColumns} rows={pay.data?.content || []} serial
            serialStart={(pay.data?.page || 0) * (pay.data?.size || paySize)} onRowClick={(p) => navigate(`/payments/${p.id}`)}
            toolbar={<span className="fw-semibold"><Icon className="bi bi-cash-stack me-2 text-primary-hh" aria-hidden="true" />Payments</span>}
            empty={pay.loading && !pay.data ? <LoadingState compact /> : <EmptyState icon="bi-cash-stack" title="No payments recorded" />}
            footer={pay.data && <Pagination page={pay.data.page} totalPages={pay.data.totalPages} totalElements={pay.data.totalElements} size={pay.data.size}
              noun="payments" sizes={[10, 20, 50]} onChange={setPayPage} onSizeChange={(s) => { setPaySize(s); setPayPage(0); }} />} />
        )}
      </section>
    </>
  );
}
