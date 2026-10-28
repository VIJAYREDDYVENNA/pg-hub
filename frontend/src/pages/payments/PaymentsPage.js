import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import useApi from '../../hooks/useApi';
import useDebounce from '../../hooks/useDebounce';
import { invoiceApi, paymentApi, whatsappApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import SummaryCard from '../../components/common/SummaryCard';
import HostelFilter from '../../components/common/HostelFilter';
import DataTable, { RowAction, RowActions } from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { AsyncContent, EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { ChargeModal, DepositAdjustModal, RefundModal } from '../../components/payments/FinanceModals';
import {
  CHART_COLORS, INVOICE_FILTERS, INVOICE_TYPES, METHOD_ICONS, compactMoney, isOpenInvoice, methodLabel, moneyTooltip,
  reminderToast,
} from '../../components/payments/financeUtils';
import RevenueDefinitions from '../../components/payments/RevenueDefinitions';
import { date, label, money, number, shortDate } from '../../utils/format';
import { INVOICE_STATUS, PAYMENT_METHODS } from '../../utils/status';
import Icon from '../../components/common/Icon';

const TABS = [
  { key: 'overview', label: 'Overview', icon: 'bi-speedometer2' },
  { key: 'invoices', label: 'Invoices', icon: 'bi-receipt' },
  { key: 'payments', label: 'Payments', icon: 'bi-cash-coin' },
  { key: 'refunds', label: 'Refunds & deposits', icon: 'bi-arrow-counterclockwise' },
];

const hasValue = (v) => Number(v) !== 0 && v !== null && v !== undefined;

/* ------------------------------------------------------------------ Overview */

function TrendChart({ trend }) {
  const data = useMemo(() => (trend || []).map((p) => ({
    month: dayjs(`${p.month}-01`).format('MMM YY'),
    collected: Number(p.collected),
    invoiced: Number(p.invoiced),
    expenses: Number(p.expenses),
  })), [trend]);
  const empty = !trend || trend.every((p) => !hasValue(p.collected) && !hasValue(p.invoiced) && !hasValue(p.expenses));
  if (empty) {
    return <EmptyState icon="bi-bar-chart" title="No billing activity yet" message="Invoices, collections and expenses from the last six months will appear here." />;
  }
  return (
    <div style={{ width: '100%', height: 300 }} role="img"
      aria-label="Monthly amount collected and invoiced as bars, and expenses as a line, for the last six months">
      <ResponsiveContainer>
        <ComposedChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={CHART_COLORS.grid} vertical={false} />
          <XAxis dataKey="month" tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} axisLine={false} tickLine={false} />
          <YAxis tickFormatter={compactMoney} tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} axisLine={false} tickLine={false} width={64} />
          <Tooltip formatter={moneyTooltip} cursor={{ fill: 'rgba(255,90,10,.06)' }} />
          <Legend wrapperStyle={{ fontSize: 13 }} />
          <Bar dataKey="invoiced" name="Invoiced" fill={CHART_COLORS.gray} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="collected" name="Collected" fill={CHART_COLORS.green} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Line dataKey="expenses" name="Expenses" stroke={CHART_COLORS.red} strokeWidth={2} dot={{ r: 3 }} type="monotone" />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

function OverviewTab({ hostelId, refreshKey }) {
  const { data: s, loading, error, reload } = useApi(() => paymentApi.summary(hostelId), [hostelId, refreshKey]);
  if (error) return <ErrorState error={error} onRetry={reload} />;
  const v = (x) => (s ? x : '…');
  const cards = [
    { label: "Today's collections", value: v(money(s?.todayCollections)), icon: 'bi-cash-coin', tone: 'green', to: '/payments?tab=payments' },
    { label: 'Due today', value: v(money(s?.dueToday?.amount)), icon: 'bi-calendar-event', tone: 'orange',
      hint: s && `${number(s.dueToday?.count)} invoice(s)`, to: '/payments?tab=invoices&status=DUE_TODAY' },
    { label: 'Overdue', value: v(money(s?.overdue?.amount)), icon: 'bi-exclamation-octagon', tone: 'red',
      hint: s && `${number(s.overdue?.count)} invoice(s)`, to: '/payments?tab=invoices&status=OVERDUE' },
    { label: 'Total outstanding', value: v(money(s?.totalOutstanding)), icon: 'bi-hourglass-split', tone: 'yellow', to: '/payments?tab=invoices&status=OPEN' },
    { label: 'Collections this month', value: v(money(s?.monthCollections)), icon: 'bi-graph-up-arrow', tone: 'green',
      hint: s && `Gross receipts · Invoiced ${money(s.monthInvoiced)}` },
    { label: 'Net revenue this month', value: v(money(s?.monthNetRevenue)), icon: 'bi-piggy-bank', tone: 'blue',
      hint: s && `Excl. deposits ${money(s.monthDepositReceipts)} · Advances refunded ${money(s.monthAdvanceRefunds)}` },
    { label: 'Deposits held', value: v(money(s?.depositsHeld)), icon: 'bi-safe', tone: 'blue', to: '/payments?tab=refunds' },
  ];
  const revenueCols = [
    { key: 'hostelName', label: 'Hostel', sortValue: (r) => r.hostelName, render: (r) => <span className="hh-cell-primary">{r.hostelName}</span> },
    { key: 'collected', label: 'Collected this month', align: 'end', sortValue: (r) => Number(r.collected), render: (r) => money(r.collected) },
    { key: 'outstanding', label: 'Outstanding', align: 'end', sortValue: (r) => Number(r.outstanding),
      render: (r) => <span className={Number(r.outstanding) > 0 ? 'text-danger fw-semibold' : ''}>{money(r.outstanding)}</span> },
  ];
  return (
    <>
      <div className="row g-3 mb-4">
        {cards.map((c) => (
          <div className="col-6 col-md-4" key={c.label} style={{ minWidth: 0 }}>
            <SummaryCard {...c} loading={loading && !s} />
          </div>
        ))}
      </div>
      <RevenueDefinitions summary={s} />
      <div className="row g-3">
        <div className="col-xl-8">
          <section className="hh-card h-100" aria-labelledby="trend-title">
            <div className="hh-card-header">
              <h2 className="hh-card-title" id="trend-title"><Icon className="bi bi-bar-chart me-2 text-primary-hh" />Collections vs invoiced vs expenses</h2>
              <span className="small text-muted-hh">Last 6 months</span>
            </div>
            <AsyncContent loading={loading && !s} compact>
              <TrendChart trend={s?.trend} />
            </AsyncContent>
          </section>
        </div>
        <div className="col-xl-4">
          <section className="hh-card h-100" aria-labelledby="hostel-rev-title">
            <div className="hh-card-header">
              <h2 className="hh-card-title" id="hostel-rev-title"><Icon className="bi bi-houses me-2 text-primary-hh" />Hostel-wise revenue</h2>
            </div>
            <AsyncContent loading={loading && !s} compact empty={s && !s.hostelRevenue?.length}
              emptyProps={{ icon: 'bi-houses', title: 'No hostels in this view' }}>
              <DataTable columns={revenueCols} rows={s?.hostelRevenue || []} rowKey="hostelId" serial />
            </AsyncContent>
          </section>
        </div>
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ Invoices */

function InvoicesTab({ hostelId, refreshKey, initialStatus }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [, setParams] = useSearchParams();
  const [f, setF] = useState({ status: initialStatus || 'OPEN', type: 'ALL', q: '', dueFrom: '', dueTo: '' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [reminding, setReminding] = useState(null);
  const q = useDebounce(f.q.trim(), 350);
  const { data, loading, error, reload } = useApi(
    () => invoiceApi.list({ hostelId, status: f.status, type: f.type, q, dueFrom: f.dueFrom, dueTo: f.dueTo, page, size }),
    [hostelId, f.status, f.type, q, f.dueFrom, f.dueTo, page, size, refreshKey]
  );
  useEffect(() => { if (initialStatus) setF((x) => ({ ...x, status: initialStatus })); }, [initialStatus]);
  const set = (k) => (e) => {
    const value = e.target.value;
    setF((x) => ({ ...x, [k]: value }));
    setPage(0);
    if (k === 'status') setParams((p) => { const n = new URLSearchParams(p); n.set('status', value); return n; }, { replace: true });
  };

  const remind = async (inv) => {
    setReminding(inv.id);
    try {
      reminderToast(await whatsappApi.invoiceReminder(inv.id), notify, inv.residentName);
    } catch (e) {
      notify(errorMessage(e), 'danger');
    } finally {
      setReminding(null);
    }
  };

  const stop = (e) => e.stopPropagation();
  const columns = [
    { key: 'invoiceNumber', label: 'Invoice', required: true, sortValue: (r) => r.invoiceNumber, render: (r) => (
      <div><Link to={`/invoices/${r.id}`} onClick={stop} className="hh-cell-primary">{r.invoiceNumber}</Link>
        <span className="hh-cell-sub">{label(r.invoiceType)}</span></div>
    ) },
    { key: 'resident', label: 'Resident', sortValue: (r) => r.residentName, render: (r) => (
      <div className="min-w-0"><span className="hh-cell-primary">{r.residentName}</span>
        <span className="hh-cell-sub">{[r.hostelName, r.roomNumber && `Room ${r.roomNumber}`].filter(Boolean).join(' · ')}</span></div>
    ) },
    { key: 'period', label: 'Period', hideOnMobile: true, sortValue: (r) => r.periodStart || '',
      render: (r) => (r.periodStart ? `${shortDate(r.periodStart)} – ${shortDate(r.periodEnd)}` : <span className="hh-dash">—</span>) },
    { key: 'dueDate', label: 'Due', sortValue: (r) => r.dueDate || '', render: (r) => (
      <div>{date(r.dueDate)}{r.daysOverdue > 0 && <span className="hh-cell-sub text-danger">{r.daysOverdue} day(s) late</span>}</div>
    ) },
    { key: 'totalAmount', label: 'Total', align: 'end', hideOnMobile: true, sortValue: (r) => Number(r.totalAmount), render: (r) => money(r.totalAmount) },
    { key: 'balance', label: 'Balance', align: 'end', sortValue: (r) => Number(r.balance),
      render: (r) => <span className={Number(r.balance) > 0 ? 'fw-bold' : 'text-muted-hh'}>{money(r.balance)}</span> },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => <StatusBadge map={INVOICE_STATUS} value={r.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (r) => (
      <RowActions>
        <RowAction variant="view" label={`View invoice ${r.invoiceNumber}`} onClick={() => navigate(`/invoices/${r.id}`)} />
        {isOpenInvoice(r) && can('NOTIFICATION_SEND') && (
          <RowAction variant="remind" label={reminding === r.id ? 'Sending reminder…' : 'Send a WhatsApp payment reminder (only if the resident opted in)'}
            disabled={reminding === r.id} onClick={() => remind(r)} />
        )}
        {isOpenInvoice(r) && can('PAYMENT_COLLECT') && (
          <RowAction variant="pay" label="Collect payment" onClick={() => navigate(`/payments/collect?residentId=${r.residentId}`)} />
        )}
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Resident, mobile, invoice no." aria-label="Search invoices"
          value={f.q} onChange={set('q')} />
      </div>
      <select className="form-select w-auto" aria-label="Status" value={f.status} onChange={set('status')}>
        {INVOICE_FILTERS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <select className="form-select w-auto" aria-label="Invoice type" value={f.type} onChange={set('type')}>
        {INVOICE_TYPES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      <label className="d-flex align-items-center gap-1 small text-muted-hh mb-0">Due from
        <input type="date" className="form-control w-auto" value={f.dueFrom} onChange={set('dueFrom')} max={f.dueTo || undefined} />
      </label>
      <label className="d-flex align-items-center gap-1 small text-muted-hh mb-0">to
        <input type="date" className="form-control w-auto" value={f.dueTo} onChange={set('dueTo')} min={f.dueFrom || undefined} />
      </label>
    </>
  );

  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
      <DataTable tableId="invoices" columns={columns} rows={data?.content || []} serial serialStart={(data?.page || 0) * (data?.size || size)}
        onRowClick={(r) => navigate(`/invoices/${r.id}`)} toolbar={toolbar}
        empty={loading && !data ? <LoadingState /> : (
          <EmptyState icon="bi-receipt" title="No invoices found" message="Try a different status, type or date range." />
        )}
        footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
          noun="invoices" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
    </div>
  );
}

/* ------------------------------------------------------------------ Payments */

function PaymentsTab({ hostelId, refreshKey }) {
  const navigate = useNavigate();
  const [f, setF] = useState({ method: 'ALL', from: '', to: '', q: '' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const q = useDebounce(f.q.trim(), 350);
  const { data, loading, error, reload } = useApi(
    () => paymentApi.list({ hostelId, method: f.method, from: f.from, to: f.to, q, page, size }),
    [hostelId, f.method, f.from, f.to, q, page, size, refreshKey]
  );
  const set = (k) => (e) => { const value = e.target.value; setF((x) => ({ ...x, [k]: value })); setPage(0); };
  const columns = [
    { key: 'receiptNumber', label: 'Receipt', required: true, sortValue: (r) => r.receiptNumber, render: (r) => (
      <div><Link to={`/payments/${r.id}`} onClick={(e) => e.stopPropagation()} className="hh-cell-primary">{r.receiptNumber}</Link>
        <span className="hh-cell-sub">{date(r.paymentDate)}</span></div>
    ) },
    { key: 'resident', label: 'Resident', sortValue: (r) => r.residentName, render: (r) => (
      <div><span className="hh-cell-primary">{r.residentName}</span>
        <span className="hh-cell-sub">{[r.hostelName, r.roomNumber && `Room ${r.roomNumber}`].filter(Boolean).join(' · ')}</span></div>
    ) },
    { key: 'method', label: 'Method', sortValue: (r) => methodLabel(r.method), render: (r) => (
      <span><Icon className={`bi ${METHOD_ICONS[r.method] || 'bi-cash'} me-1 text-muted-hh`} aria-hidden="true" />{methodLabel(r.method)}</span>
    ) },
    { key: 'transactionReference', label: 'Reference', hideOnMobile: true, sortValue: (r) => r.transactionReference || '',
      render: (r) => r.transactionReference || <span className="hh-dash">—</span> },
    { key: 'amount', label: 'Amount', align: 'end', sortValue: (r) => Number(r.amount), render: (r) => (
      <span className={`fw-bold ${r.status === 'REVERSED' ? 'text-decoration-line-through text-muted-hh' : ''}`}>{money(r.amount)}</span>
    ) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => <StatusBadge map={INVOICE_STATUS} value={r.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (r) => (
      <RowActions>
        <RowAction variant="view" label={`View receipt ${r.receiptNumber}`} onClick={() => navigate(`/payments/${r.id}`)} />
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Resident, mobile, receipt, reference" aria-label="Search payments"
          value={f.q} onChange={set('q')} />
      </div>
      <select className="form-select w-auto" aria-label="Payment method" value={f.method} onChange={set('method')}>
        <option value="ALL">All methods</option>
        {PAYMENT_METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
        <option value="DEPOSIT_ADJUSTMENT">Deposit adjustment</option>
      </select>
      <label className="d-flex align-items-center gap-1 small text-muted-hh mb-0">From
        <input type="date" className="form-control w-auto" value={f.from} onChange={set('from')} max={f.to || undefined} />
      </label>
      <label className="d-flex align-items-center gap-1 small text-muted-hh mb-0">to
        <input type="date" className="form-control w-auto" value={f.to} onChange={set('to')} min={f.from || undefined} />
      </label>
    </>
  );

  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
      <DataTable tableId="payments" columns={columns} rows={data?.content || []} serial serialStart={(data?.page || 0) * (data?.size || size)}
        onRowClick={(r) => navigate(`/payments/${r.id}`)} toolbar={toolbar}
        empty={loading && !data ? <LoadingState /> : (
          <EmptyState icon="bi-cash-coin" title="No payments found" message="Payments you record will appear here with their receipts." />
        )}
        footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
          noun="payments" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
    </div>
  );
}

/* ------------------------------------------------------------------ Refunds */

function RefundsTab({ hostelId, refreshKey, onChanged }) {
  const { can } = useAuth();
  const navigate = useNavigate();
  const [modal, setModal] = useState(null);
  const { data, loading, error, reload } = useApi(() => paymentApi.refunds({ hostelId }), [hostelId, refreshKey]);
  const columns = [
    { key: 'residentName', label: 'Resident', required: true, sortValue: (r) => r.residentName, render: (r) => (
      <div><Link to={`/residents/${r.residentId}`} className="hh-cell-primary" onClick={(e) => e.stopPropagation()}>{r.residentName}</Link>
        <span className="hh-cell-sub">{date(r.refundDate)}</span></div>
    ) },
    { key: 'refundType', label: 'Type', align: 'center', sortValue: (r) => r.refundType, render: (r) => (
      <span className="badge hh-badge hh-badge-info">
        <Icon className={`bi ${r.refundType === 'DEPOSIT' ? 'bi-safe' : 'bi-wallet2'} me-1`} aria-hidden="true" />{r.refundType === 'DEPOSIT' ? 'Deposit' : 'Advance'}
      </span>
    ) },
    { key: 'amount', label: 'Amount', align: 'end', sortValue: (r) => Number(r.amount), render: (r) => <span className="fw-bold">{money(r.amount)}</span> },
    { key: 'method', label: 'Paid by', sortValue: (r) => methodLabel(r.method), render: (r) => (
      <div>{methodLabel(r.method)}{r.transactionReference && <span className="hh-cell-sub">{r.transactionReference}</span>}</div>
    ) },
    { key: 'reason', label: 'Reason', hideOnMobile: true, sortValue: (r) => r.reason || '', render: (r) => r.reason || <span className="hh-dash">—</span> },
    { key: 'recordedByName', label: 'Recorded by', hideOnMobile: true },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (r) => (
      <RowActions>
        <RowAction variant="view" label={`Open ${r.residentName}`} onClick={() => navigate(`/residents/${r.residentId}`)} />
      </RowActions>
    ) },
  ];
  const done = () => { setModal(null); reload(); onChanged?.(); };
  const toolbar = (
    <>
      <span className="fw-semibold"><Icon className="bi bi-arrow-counterclockwise me-2 text-primary-hh" />Refunds</span>
      {can('PAYMENT_ADJUST') && (
        <div className="d-flex gap-2 flex-wrap">
          <button type="button" className="btn btn-sm btn-light" onClick={() => setModal('deposit')}><Icon className="bi bi-safe me-1" />Adjust deposit against dues</button>
          <button type="button" className="btn btn-sm btn-primary" onClick={() => setModal('refund')}><Icon className="bi bi-plus-lg me-1" />Record refund</button>
        </div>
      )}
    </>
  );
  return (
    <>
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <DataTable tableId="refunds" columns={columns} rows={data || []} serial toolbar={toolbar}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-arrow-counterclockwise" title="No refunds recorded" message="Deposit and advance refunds will be listed here." />
          )}
          footer={data && data.length > 0 && (
            <div className="hh-pagination"><div className="hh-pagination-info"><span>Showing the latest {data.length} refund(s)</span></div></div>
          )} />
      )}
      <RefundModal show={modal === 'refund'} onClose={() => setModal(null)} onDone={done} hostelId={hostelId} />
      <DepositAdjustModal show={modal === 'deposit'} onClose={() => setModal(null)} hostelId={hostelId}
        onDone={(p) => { done(); if (p?.id) navigate(`/payments/${p.id}`); }} />
    </>
  );
}

/* ------------------------------------------------------------------ Page */

export default function PaymentsPage() {
  const { can } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const { selectedHostelId, selectedHostel } = useHostels();
  const [params, setParams] = useSearchParams();
  const tab = TABS.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'overview';
  const statusParam = params.get('status');
  const initialStatus = INVOICE_FILTERS.some((o) => o.value === statusParam) ? statusParam : null;
  const [refreshKey, setRefreshKey] = useState(0);
  const [busy, setBusy] = useState(null);
  const [showCharge, setShowCharge] = useState(false);
  const [confirmBulk, setConfirmBulk] = useState(false);

  useEffect(() => { document.title = 'Payments · HostelHub'; }, []);

  const setTab = (key) => setParams((p) => {
    const n = new URLSearchParams(p);
    n.set('tab', key);
    if (key !== 'invoices') n.delete('status');
    return n;
  });

  const generateRent = async () => {
    setBusy('rent');
    try {
      const r = await invoiceApi.generateRent(selectedHostelId);
      notify(r?.created ? `${r.created} rent invoice(s) created.` : 'All rent invoices are already up to date.');
      setRefreshKey((k) => k + 1);
    } catch (e) {
      notify(errorMessage(e), 'danger');
    } finally {
      setBusy(null);
    }
  };

  const sendBulk = async () => {
    try {
      const r = await whatsappApi.bulkReminders(selectedHostelId);
      const parts = [`${number(r.attempted)} overdue reminder(s) queued`];
      if (r.skippedNoConsent) parts.push(`${number(r.skippedNoConsent)} skipped (no WhatsApp consent)`);
      notify(`${parts.join(', ')}${r.simulated ? ' (simulated – no real WhatsApp messages were sent)' : ''}.`, r.attempted ? 'success' : 'warning');
      setConfirmBulk(false);
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };

  return (
    <>
      <PageHeader title="Payments" icon="bi-wallet2"
        subtitle={`${selectedHostel ? selectedHostel.name : 'All hostels'} · rent, charges, collections and refunds`}
        actions={<>
          <div style={{ minWidth: 180 }}><HostelFilter id="payments-hostel" /></div>
          {can('PAYMENT_COLLECT') && (
            <button type="button" className="btn btn-light" onClick={() => setShowCharge(true)}><Icon className="bi bi-plus-square me-1" />Add charge</button>
          )}
          {can('PAYMENT_COLLECT') && (
            <button type="button" className="btn btn-light" onClick={generateRent} disabled={busy === 'rent'}
              title="Creates any rent invoices that are due now (this also runs automatically every night)">
              {busy === 'rent' ? <span className="spinner-border spinner-border-sm me-1" /> : <Icon className="bi bi-calendar-plus me-1" />}Generate due rent
            </button>
          )}
          {can('NOTIFICATION_SEND') && (
            <button type="button" className="btn btn-light" onClick={() => setConfirmBulk(true)}><Icon className="bi bi-whatsapp me-1" />Send overdue reminders</button>
          )}
          {can('PAYMENT_COLLECT') && (
            <Link to="/payments/collect" className="btn btn-primary"><Icon className="bi bi-cash-coin me-1" />Collect payment</Link>
          )}
        </>} />

      <ul className="nav nav-pills gap-1 mb-3 flex-nowrap overflow-auto no-print" role="tablist">
        {TABS.map((t) => (
          <li className="nav-item" key={t.key} role="presentation">
            <button type="button" role="tab" aria-selected={tab === t.key} className={`nav-link text-nowrap ${tab === t.key ? 'active' : ''}`}
              onClick={() => setTab(t.key)}>
              <Icon className={`bi ${t.icon} me-1`} aria-hidden="true" />{t.label}
            </button>
          </li>
        ))}
      </ul>

      <div role="tabpanel">
        {tab === 'overview' && <OverviewTab hostelId={selectedHostelId} refreshKey={refreshKey} />}
        {tab === 'invoices' && <InvoicesTab hostelId={selectedHostelId} refreshKey={refreshKey} initialStatus={initialStatus} />}
        {tab === 'payments' && <PaymentsTab hostelId={selectedHostelId} refreshKey={refreshKey} />}
        {tab === 'refunds' && <RefundsTab hostelId={selectedHostelId} refreshKey={refreshKey} onChanged={() => setRefreshKey((k) => k + 1)} />}
      </div>

      <ChargeModal show={showCharge} onClose={() => setShowCharge(false)} hostelId={selectedHostelId}
        onDone={(inv) => { setShowCharge(false); setRefreshKey((k) => k + 1); if (inv?.id) navigate(`/invoices/${inv.id}`); }} />
      <ConfirmDialog show={confirmBulk} title="Send overdue reminders" confirmLabel="Send reminders"
        message={`WhatsApp reminders will be sent for overdue invoices in ${selectedHostel ? selectedHostel.name : 'all your hostels'}. Residents who have not opted in to WhatsApp are skipped. Reminders do not mark anything as paid.`}
        onConfirm={sendBulk} onClose={() => setConfirmBulk(false)} />
    </>
  );
}
