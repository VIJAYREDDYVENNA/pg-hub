import { useState } from 'react';
import { Link } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { dashboardApi } from '../../services/endpoints';
import { AsyncContent } from '../common/States';
import { date } from '../../utils/format';
import Icon from '../common/Icon';

export default function VacancyPanel({ hostelId, limit = 8 }) {
  const [days, setDays] = useState(7);
  const { data, loading, error, reload } = useApi(() => dashboardApi.vacancies({ hostelId, days }), [hostelId, days]);
  return (
    <div className="hh-card h-100">
      <div className="hh-card-header">
        <h2 className="hh-card-title"><Icon className="bi bi-calendar2-week me-2 text-primary-hh" />Upcoming vacancies</h2>
        <div className="nav nav-pills gap-1" role="tablist">
          {[7, 15, 30].map((d) => (
            <button key={d} type="button" role="tab" aria-selected={days === d} className={`nav-link py-1 px-3 ${days === d ? 'active' : ''}`} onClick={() => setDays(d)}>{d} days</button>
          ))}
        </div>
      </div>
      <AsyncContent loading={loading} error={error} onRetry={reload} compact empty={data && data.items.length === 0}
        emptyProps={{ icon: 'bi-calendar-check', title: `No checkouts in the next ${days} days` }}>
        {data && (
          <>
            <div className="d-flex gap-3 small text-muted-hh mb-2 flex-wrap">
              <span><strong className="text-success">{data.summary.currentlyAvailableBeds}</strong> free now</span>
              <span><strong className="text-warning">{data.items.length}</strong> becoming free</span>
              <span><strong>{data.summary.reservedBeds}</strong> reserved</span>
            </div>
            {data.items.slice(0, limit).map((v) => (
              <div className="hh-feed-item" key={v.admissionId}>
                <div className="hh-feed-icon" style={{ background: v.overdue ? '#FEE2E2' : '#FEF3C7', color: v.overdue ? '#B91C1C' : '#B45309' }}>
                  <span className="fw-bold small">{v.overdue ? '!' : `${v.daysRemaining}d`}</span>
                </div>
                <div className="min-w-0 flex-grow-1">
                  <div className="fw-semibold text-truncate">Room {v.roomNumber} · Bed {v.bedLabel} <span className="text-muted-hh fw-normal">· {v.hostelName}</span></div>
                  <div className="small text-muted-hh text-truncate">
                    <Link to={`/residents/${v.residentId}`}>{v.residentName}</Link> · {date(v.expectedCheckoutDate)} ·{' '}
                    {v.confirmed ? <span className="text-success">confirmed</span> : <span>estimated</span>}
                    {v.bookingId && <span className="ms-1 badge hh-badge hh-badge-reserved">Reserved for {v.reservedFor}</span>}
                  </div>
                </div>
              </div>
            ))}
            <Link to="/vacancies" className="btn btn-sm btn-light w-100 mt-2">See full forecast<Icon className="bi bi-arrow-right ms-1" /></Link>
          </>
        )}
      </AsyncContent>
    </div>
  );
}
