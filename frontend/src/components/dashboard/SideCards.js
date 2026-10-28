import { Link } from 'react-router-dom';
import Donut from '../common/Donut';
import { CATEGORY_META, countCategories } from '../building/roomDisplay';
import useApi from '../../hooks/useApi';
import { notificationApi } from '../../services/endpoints';
import { AsyncContent } from '../common/States';
import { fromNow } from '../../utils/format';
import { useAuth } from '../../context/AuthContext';
import Icon from '../common/Icon';

const ORDER = ['occupied', 'partial', 'vacant', 'soon', 'maintenance'];

export function FloorSummaryCard({ rooms, title }) {
  const c = countCategories(rooms);
  const total = rooms?.length || 0;
  return (
    <div className="hh-card">
      <h3 className="hh-card-title mb-3">{title ? `${title} Summary` : 'Floor Summary'}</h3>
      <div className="hh-donut-wrap">
        <Donut size={112} thickness={16} label={`${total} rooms`}
          segments={ORDER.map((k) => ({ label: k, value: c[k], color: CATEGORY_META[k].color }))}>
          <div><div className="fw-bold fs-3 lh-1">{total}</div><div className="small text-muted-hh">Rooms</div></div>
        </Donut>
        <div className="hh-donut-legend">
          {ORDER.map((k) => (
            <div key={k}><span><i style={{ background: CATEGORY_META[k].color }} />{CATEGORY_META[k].label}</span><b>{c[k]}</b></div>
          ))}
        </div>
      </div>
    </div>
  );
}

export function QuickActionsCard() {
  const { can } = useAuth();
  const items = [
    can('RESIDENT_MANAGE') && { to: '/residents/new', label: 'Add Resident', icon: 'bi-person-plus-fill', cls: 'ic-orange' },
    can('PAYMENT_COLLECT') && { to: '/payments/collect', label: 'Collect Payment', icon: 'bi-currency-rupee', cls: 'ic-blue' },
    { to: '/rooms', label: 'Room Details', icon: 'bi-file-earmark-text-fill', cls: 'ic-purple' },
    { to: '/vacancies', label: 'Vacancies', icon: 'bi-calendar2-week-fill', cls: 'ic-red' },
  ].filter(Boolean).slice(0, 4);
  return (
    <div className="hh-card">
      <h3 className="hh-card-title mb-3">Quick Actions</h3>
      <div className="hh-qa">
        {items.map((i) => (
          <Link key={i.to} to={i.to}><span className={`hh-qa-ic ${i.cls}`}><Icon className={`bi ${i.icon}`} /></span>{i.label}</Link>
        ))}
      </div>
    </div>
  );
}

const ALERT_META = {
  UPCOMING_CHECKOUT: ['bi-bell-fill', 'ic-yellow'], PAYMENT_OVERDUE: ['bi-currency-rupee', 'ic-solid-red'], PAYMENT_DUE: ['bi-calendar-event', 'ic-orange'],
  MAINTENANCE: ['bi-tools', 'ic-red'], NEW_ENQUIRY: ['bi-person', 'ic-blue'], ENQUIRY_FOLLOW_UP: ['bi-telephone', 'ic-blue'],
  NEW_ADMISSION: ['bi-person-plus', 'ic-green'], CHECKOUT: ['bi-door-open', 'ic-gray'], PAYMENT_RECEIVED: ['bi-cash-coin', 'ic-green'],
  ANNOUNCEMENT: ['bi-megaphone', 'ic-purple'],
};

export function AlertsCard({ limit = 4 }) {
  const { data, loading, error, reload } = useApi(() => notificationApi.list({ size: limit }), [limit]);
  return (
    <div className="hh-card">
      <div className="hh-card-header mb-1">
        <h3 className="hh-card-title">Alerts &amp; Notifications</h3>
        <Link to="/notifications" className="hh-link">View All <Icon className="bi bi-arrow-right" /></Link>
      </div>
      <AsyncContent loading={loading} error={error} onRetry={reload} compact empty={data && data.content.length === 0}
        emptyProps={{ icon: 'bi-bell-slash', title: 'No alerts' }}>
        {data?.content.map((n) => {
          const [icon, cls] = ALERT_META[n.type] || ['bi-bell', 'ic-orange'];
          const body = (
            <>
              <span className={`hh-alert-ic ${cls}`}><Icon className={`bi ${icon}`} /></span>
              <span className="min-w-0">
                <span className={`d-block small fw-medium ${n.type === 'PAYMENT_OVERDUE' ? 'text-danger' : ''}`} style={{ fontWeight: 500 }}>{n.title}</span>
                <span className="d-block text-muted-hh text-truncate" style={{ fontSize: '.75rem', maxWidth: 190 }}>{n.message}</span>
              </span>
              <span className="hh-alert-time">{fromNow(n.createdAt)}</span>
            </>
          );
          return n.link ? <Link key={n.id} to={n.link} className="hh-alert">{body}</Link> : <div key={n.id} className="hh-alert">{body}</div>;
        })}
      </AsyncContent>
    </div>
  );
}
