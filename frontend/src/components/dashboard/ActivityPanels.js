import { Link } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { dashboardApi } from '../../services/endpoints';
import { AsyncContent, EmptyState } from '../common/States';
import StatusBadge from '../common/StatusBadge';
import { date, fromNow, money } from '../../utils/format';
import { MAINTENANCE_STATUS, PRIORITY } from '../../utils/status';
import { useAuth } from '../../context/AuthContext';
import Icon from '../common/Icon';

function Panel({ title, icon, children, to }) {
  return (
    <div className="hh-card h-100">
      <div className="hh-card-header">
        <h2 className="hh-card-title"><Icon className={`bi ${icon} me-2 text-primary-hh`} />{title}</h2>
        {to && <Link to={to} className="small">View all</Link>}
      </div>
      {children}
    </div>
  );
}

export default function ActivityPanels({ hostelId }) {
  const { can } = useAuth();
  const { data, loading, error, reload } = useApi(() => dashboardApi.activity(hostelId), [hostelId]);
  const wrap = (content) => <AsyncContent loading={loading} error={error} onRetry={reload} compact>{content}</AsyncContent>;
  const emptyLine = (t) => <EmptyState icon="bi-check2-circle" title={t} />;

  return (
    <div className="row g-3">
      {can('PAYMENT_VIEW') && (
        <>
          <div className="col-lg-6">
            <Panel title="Payments due today" icon="bi-calendar-event" to="/payments?tab=invoices&status=DUE_TODAY">
              {wrap(data && (data.dueToday.length === 0 ? emptyLine('Nothing due today') : data.dueToday.map((i) => (
                <div className="hh-feed-item" key={i.id}>
                  <div className="min-w-0 flex-grow-1">
                    <Link to={`/residents/${i.residentId}`} className="fw-semibold">{i.residentName}</Link>
                    <div className="small text-muted-hh">{i.hostelName}{i.roomNumber && ` · Room ${i.roomNumber}`} · {i.invoiceNumber}</div>
                  </div>
                  <div className="text-end"><div className="fw-bold">{money(i.balance)}</div>
                    {can('PAYMENT_COLLECT') && <Link to={`/payments/collect?residentId=${i.residentId}`} className="small">Collect</Link>}</div>
                </div>
              ))))}
            </Panel>
          </div>
          <div className="col-lg-6">
            <Panel title="Overdue payments" icon="bi-exclamation-circle" to="/payments?tab=invoices&status=OVERDUE">
              {wrap(data && (data.overdue.length === 0 ? emptyLine('No overdue payments') : data.overdue.map((i) => (
                <div className="hh-feed-item" key={i.id}>
                  <div className="min-w-0 flex-grow-1">
                    <Link to={`/residents/${i.residentId}`} className="fw-semibold">{i.residentName}</Link>
                    <div className="small text-muted-hh">{i.hostelName}{i.roomNumber && ` · Room ${i.roomNumber}`} · due {date(i.dueDate)}</div>
                  </div>
                  <div className="text-end"><div className="fw-bold text-danger">{money(i.balance)}</div><div className="small text-danger">{i.daysOverdue} days late</div></div>
                </div>
              ))))}
            </Panel>
          </div>
          <div className="col-lg-6 col-xl-4">
            <Panel title="Recent payments" icon="bi-cash-coin" to="/payments?tab=payments">
              {wrap(data && (data.recentPayments.length === 0 ? emptyLine('No payments yet') : data.recentPayments.map((p) => (
                <Link to={`/payments/${p.id}`} className="hh-feed-item text-reset" key={p.id}>
                  <div className="hh-feed-icon" style={{ background: '#DCFCE7', color: '#15803D' }}><Icon className="bi bi-arrow-down-left" /></div>
                  <div className="min-w-0 flex-grow-1"><div className="fw-semibold text-truncate">{p.residentName}</div>
                    <div className="small text-muted-hh">{p.method.replace('_', ' ')} · {date(p.paymentDate)}</div></div>
                  <div className="fw-bold">{money(p.amount)}</div>
                </Link>
              ))))}
            </Panel>
          </div>
        </>
      )}
      {can('RESIDENT_VIEW') && (
        <>
          <div className="col-lg-6 col-xl-4">
            <Panel title="Recent admissions" icon="bi-person-plus" to="/residents?status=ACTIVE">
              {wrap(data && (data.recentAdmissions.length === 0 ? emptyLine('No recent admissions') : data.recentAdmissions.map((a) => (
                <Link to={`/residents/${a.residentId}`} className="hh-feed-item text-reset" key={a.id}>
                  <div className="hh-feed-icon" style={{ background: '#FFF0E5', color: '#E84900' }}><Icon className="bi bi-person-plus" /></div>
                  <div className="min-w-0 flex-grow-1"><div className="fw-semibold text-truncate">{a.residentName}</div>
                    <div className="small text-muted-hh">{a.hostelName}{a.currentBed && ` · ${a.currentBed.roomNumber}/${a.currentBed.bedLabel}`}</div></div>
                  <div className="small text-muted-hh">{date(a.admissionDate)}</div>
                </Link>
              ))))}
            </Panel>
          </div>
          <div className="col-lg-6 col-xl-4">
            <Panel title="Recent checkouts" icon="bi-box-arrow-right" to="/residents?status=CHECKED_OUT">
              {wrap(data && (data.recentCheckouts.length === 0 ? emptyLine('No recent checkouts') : data.recentCheckouts.map((a) => (
                <Link to={`/residents/${a.residentId}`} className="hh-feed-item text-reset" key={a.id}>
                  <div className="hh-feed-icon" style={{ background: '#F1F5F9', color: '#475569' }}><Icon className="bi bi-door-open" /></div>
                  <div className="min-w-0 flex-grow-1"><div className="fw-semibold text-truncate">{a.residentName}</div>
                    <div className="small text-muted-hh">{a.hostelName}</div></div>
                  <div className="small text-muted-hh">{date(a.actualCheckoutDate)}</div>
                </Link>
              ))))}
            </Panel>
          </div>
        </>
      )}
      {can('MAINTENANCE_MANAGE') && (
        <div className="col-lg-6 col-xl-4">
          <Panel title="Maintenance requests" icon="bi-tools" to="/maintenance">
            {wrap(data && (data.openMaintenance.length === 0 ? emptyLine('No open requests') : data.openMaintenance.map((m) => (
              <Link to={`/maintenance?id=${m.id}`} className="hh-feed-item text-reset" key={m.id}>
                <div className="min-w-0 flex-grow-1"><div className="fw-semibold text-truncate">{m.title}</div>
                  <div className="small text-muted-hh">{m.hostelName}{m.roomNumber && ` · Room ${m.roomNumber}`} · {fromNow(m.createdAt)}</div></div>
                <div className="d-flex flex-column gap-1 align-items-end"><StatusBadge map={MAINTENANCE_STATUS} value={m.status} /><StatusBadge map={PRIORITY} value={m.priority} /></div>
              </Link>
            ))))}
          </Panel>
        </div>
      )}
    </div>
  );
}
