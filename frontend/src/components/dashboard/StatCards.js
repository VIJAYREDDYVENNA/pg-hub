import { Link } from 'react-router-dom';
import { Ring } from '../common/Donut';
import { money, number, percent } from '../../utils/format';
import Icon from '../common/Icon';

function Stat({ to, icon, cls, label, value, sub, money: isMoney, valueClass = '' }) {
  return (
    <Link to={to} className="hh-stat" aria-label={`${label}: ${value}`}>
      <span className={`hh-stat-icon ${cls}`}><Icon className={`bi ${icon}`} /></span>
      <span className="min-w-0">
        <span className="hh-stat-label d-block">{label}</span>
        <span className={`hh-stat-value d-block ${isMoney ? 'money' : ''} ${valueClass}`}>{value}</span>
        {sub && <span className="hh-stat-sub d-block">{sub}</span>}
      </span>
    </Link>
  );
}

/** The eight headline metrics. All values come from /api/dashboard/summary. */
export default function StatCards({ s, canPay }) {
  const v = (x) => (s ? x : '…');
  const vacantPct = s && s.totalBeds ? percent((s.vacantBeds * 100) / s.totalBeds) : '';
  return (
    <div className="hh-stats">
      <Stat to="/hostels" icon="bi-buildings-fill" cls="ic-orange" label="Total Hostels" value={v(number(s?.totalHostels))} />
      <Stat to="/rooms" icon="bi-door-closed-fill" cls="ic-blue" label="Total Rooms" value={v(number(s?.totalRooms))} />
      <Stat to="/residents?status=ACTIVE" icon="bi-people-fill" cls="ic-green" label="Total Residents" value={v(number(s?.occupiedBeds))} />
      <div className="hh-stat" aria-label={`Overall occupancy ${s ? percent(s.occupancyPercent) : ''}`}>
        <span className="hh-stat-occ">
          <span className="min-w-0">
            <span className="hh-stat-label d-block">Overall Occupancy</span>
            <span className="hh-stat-value d-block">{v(number(s?.occupiedBeds))}</span>
            <span className="hh-stat-sub d-block">/ {v(number(s?.totalBeds))} Beds</span>
          </span>
          <Ring percent={s?.occupancyPercent || 0} size={58} thickness={8} />
        </span>
      </div>
      <Stat to="/rooms?status=HAS_AVAILABLE" icon="bi-person-plus-fill" cls="ic-green" label="Vacant Beds" value={v(number(s?.vacantBeds))}
        sub={s && `(${vacantPct}) · ${s.reservedBeds} reserved`} />
      <Stat to="/vacancies?days=7" icon="bi-box-seam-fill" cls="ic-yellow" label="Vacating Soon (next 7 days)" value={v(number(s?.vacating7Days))}
        sub={s && `${s.vacating30Days} in next 30 days`} />
      {canPay && (
        <Stat to="/payments?tab=invoices&status=DUE_TODAY" icon="bi-receipt-cutoff" cls="ic-red" label="Today's Pending Payments" money
          value={v(money(s?.dueToday?.amount))} valueClass="text-danger" sub={s?.dueToday && `(${s.dueToday.count} invoice${s.dueToday.count === 1 ? '' : 's'})`} />
      )}
      {canPay && (
        <Stat to="/payments?tab=invoices&status=OVERDUE" icon="bi-exclamation-diamond-fill" cls="ic-solid-red" label="Overdue Payments" money
          value={v(money(s?.overdue?.amount))} valueClass="text-danger" sub={s?.overdue && `(${s.overdue.count} invoice${s.overdue.count === 1 ? '' : 's'})`} />
      )}
    </div>
  );
}
