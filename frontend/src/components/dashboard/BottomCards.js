import { useState } from 'react';
import { Link } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { dashboardApi, whatsappApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { AsyncContent, EmptyState } from '../common/States';
import Avatar, { AvatarStack } from '../common/Avatar';
import { date, fromNow, money } from '../../utils/format';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import Icon from '../common/Icon';

export function UpcomingVacanciesCard({ hostelId }) {
  const { data, loading, error, reload } = useApi(() => dashboardApi.vacancies({ hostelId, days: 30 }), [hostelId]);
  const buckets = data ? [
    { label: 'Within 7 Days', items: data.items.filter((i) => i.daysRemaining <= 7), days: 7 },
    { label: 'Within 15 Days', items: data.items.filter((i) => i.daysRemaining <= 15), days: 15 },
    { label: 'Within 30 Days', items: data.items, days: 30 },
  ] : [];
  return (
    <div className="hh-card h-100">
      <div className="hh-card-header">
        <h3 className="hh-card-title">Upcoming Vacancies</h3>
        <Link to="/vacancies" className="hh-link">View All <Icon className="bi bi-arrow-right" /></Link>
      </div>
      <AsyncContent loading={loading} error={error} onRetry={reload} compact>
        {data && (
          <>
            <div className="hh-vac-buckets">
              {buckets.map((b) => (
                <Link key={b.days} to={`/vacancies?days=${b.days}`} className="hh-vac-bucket text-reset">
                  <div className="hh-vac-bucket-head">
                    <span className="ic"><Icon className="bi bi-calendar2-week" /></span>
                    <span><small>{b.label}</small><strong>{b.items.length} Bed{b.items.length === 1 ? '' : 's'}</strong></span>
                  </div>
                  <AvatarStack names={b.items.map((i) => i.residentName)} max={3} />
                </Link>
              ))}
            </div>
            {data.summary.overdueCheckouts > 0 && (
              <div className="small text-danger mt-3"><Icon className="bi bi-exclamation-circle me-1" />{data.summary.overdueCheckouts} resident(s) past their expected checkout date</div>
            )}
            <div className="small text-muted-hh mt-2">{data.summary.currentlyAvailableBeds} beds free right now · {data.summary.reservedBeds} reserved</div>
          </>
        )}
      </AsyncContent>
    </div>
  );
}

export function PendingPaymentsCard({ activity, loading, error, onRetry }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const [busy, setBusy] = useState(null);
  const rows = activity ? (activity.dueToday.length ? activity.dueToday : []) : [];
  const remind = async (inv) => {
    setBusy(inv.id);
    try {
      const m = await whatsappApi.invoiceReminder(inv.id);
      if (m.status === 'SKIPPED_NO_CONSENT') notify(`${inv.residentName} has not opted in to WhatsApp — reminder not sent.`, 'warning');
      else notify(`Reminder ${m.simulated ? 'SIMULATED (mock mode, not actually sent)' : 'sent'} to ${inv.residentName}.`, m.simulated ? 'warning' : 'success');
    } catch (e) { notify(errorMessage(e), 'danger'); } finally { setBusy(null); }
  };
  return (
    <div className="hh-card h-100">
      <div className="hh-card-header">
        <h3 className="hh-card-title">Today's Pending Payments</h3>
        <Link to="/payments?tab=invoices&status=DUE_TODAY" className="hh-link">View All <Icon className="bi bi-arrow-right" /></Link>
      </div>
      <AsyncContent loading={loading} error={error} onRetry={onRetry} compact empty={activity && rows.length === 0}
        emptyProps={{ icon: 'bi-check2-circle', title: 'Nothing due today', message: activity?.overdue.length ? `${activity.overdue.length}+ overdue invoices need attention` : undefined }}>
        {rows.slice(0, 4).map((i) => (
          <div className="hh-pay-row" key={i.id}>
            <Avatar name={i.residentName} size="sm" />
            <Link to={`/residents/${i.residentId}`} className="min-w-0 flex-grow-1 text-reset small text-truncate">
              {i.residentName}{i.roomNumber && <span className="text-muted-hh"> - Room {i.roomNumber}</span>}
            </Link>
            <strong className="small">{money(i.balance)}</strong>
            <span className="hh-due-today d-none d-sm-inline">Due Today</span>
            {can('NOTIFICATION_SEND') && (
              <button type="button" className="btn-reminder" disabled={busy === i.id} onClick={() => remind(i)}>
                <span className="d-none d-xl-inline">Send Reminder</span><span className="d-xl-none">Remind</span>
                <span className="wa">{busy === i.id ? <span className="spinner-border spinner-border-sm" style={{ width: 12, height: 12 }} /> : <Icon className="bi bi-whatsapp" />}</span>
              </button>
            )}
          </div>
        ))}
      </AsyncContent>
    </div>
  );
}

export function RecentMovesCard({ activity, loading, error, onRetry }) {
  const moves = activity ? [
    ...activity.recentAdmissions.map((a) => ({ key: `in${a.id}`, type: 'in', name: a.residentName, residentId: a.residentId,
      room: a.currentBed?.roomNumber, when: a.admissionDate })),
    ...activity.recentCheckouts.map((a) => ({ key: `out${a.id}`, type: 'out', name: a.residentName, residentId: a.residentId,
      room: a.currentBed?.roomNumber, when: a.actualCheckoutDate })),
  ].sort((x, y) => String(y.when).localeCompare(String(x.when))).slice(0, 4) : [];
  return (
    <div className="hh-card h-100">
      <div className="hh-card-header">
        <h3 className="hh-card-title">Recent Check-ins / Check-outs</h3>
        <Link to="/residents" className="hh-link">View All <Icon className="bi bi-arrow-right" /></Link>
      </div>
      <AsyncContent loading={loading} error={error} onRetry={onRetry} compact>
        {moves.length === 0 ? <EmptyState icon="bi-arrow-left-right" title="No recent movements" /> : moves.map((m) => (
          <div className="hh-pay-row" key={m.key}>
            <span className={`hh-move-badge ${m.type}`}><Icon className={`bi ${m.type === 'in' ? 'bi-box-arrow-in-right' : 'bi-box-arrow-right'}`} />{m.type === 'in' ? 'Check-in' : 'Check-out'}</span>
            <Link to={`/residents/${m.residentId}`} className="min-w-0 flex-grow-1 text-reset small text-truncate">
              {m.name}{m.room && <span className="text-muted-hh"> - Room {m.room}</span>}
            </Link>
            <span className="small text-muted-hh text-nowrap" title={date(m.when)}>{fromNow(m.when)}</span>
          </div>
        ))}
      </AsyncContent>
    </div>
  );
}
