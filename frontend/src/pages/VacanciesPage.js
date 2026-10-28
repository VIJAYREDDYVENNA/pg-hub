import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/common/PageHeader';
import HostelFilter from '../components/common/HostelFilter';
import SummaryCard from '../components/common/SummaryCard';
import { RowAction, RowActions } from '../components/common/DataTable';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import Pagination from '../components/common/Pagination';
import useApi from '../hooks/useApi';
import { useHostels } from '../context/HostelContext';
import { dashboardApi } from '../services/endpoints';
import { addDays, date, number, telLink, today, whatsappLink } from '../utils/format';
import Icon from '../components/common/Icon';

const PRESETS = [7, 15, 30];

function SourceBadge({ confirmed }) {
  return confirmed
    ? <span className="badge hh-badge hh-badge-success"><Icon className="bi bi-check2-circle me-1" />Confirmed</span>
    : <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-question-circle me-1" />Estimated</span>;
}

function Remaining({ v }) {
  if (v.overdue) {
    return <span className="badge hh-badge hh-badge-danger"><Icon className="bi bi-exclamation-circle me-1" />Overdue {Math.abs(v.daysRemaining)}d</span>;
  }
  if (v.daysRemaining === 0) return <span className="badge hh-badge hh-badge-warning"><Icon className="bi bi-alarm me-1" />Today</span>;
  return <span className={`badge hh-badge ${v.daysRemaining <= 7 ? 'hh-badge-soon' : 'hh-badge-info'}`}><Icon className="bi bi-hourglass-split me-1" />{v.daysRemaining} day{v.daysRemaining === 1 ? '' : 's'}</span>;
}

function Booking({ v }) {
  if (!v.bookingId) return <span className="text-muted-hh">Not reserved</span>;
  return (
    <span className="badge hh-badge hh-badge-reserved text-wrap text-start">
      <Icon className="bi bi-bookmark-fill me-1" />Reserved for {v.reservedFor}{v.reservedFrom ? ` from ${date(v.reservedFrom)}` : ''}
    </span>
  );
}

export default function VacanciesPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { selectedHostelId } = useHostels();
  const initialDays = Number(params.get('days'));
  const [days, setDays] = useState(PRESETS.includes(initialDays) ? initialDays : (params.get('from') ? null : 30));
  const [range, setRange] = useState({ from: params.get('from') || today(), to: params.get('to') || addDays(60) });
  const [applied, setApplied] = useState(params.get('from') ? { from: params.get('from'), to: params.get('to') || addDays(60) } : null);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  useEffect(() => { document.title = 'Vacancy forecast · HostelHub'; }, []);
  useEffect(() => { setPage(0); }, [selectedHostelId, days, applied?.from, applied?.to]);

  const query = days ? { hostelId: selectedHostelId, days } : { hostelId: selectedHostelId, ...applied };
  const { data, loading, error, reload } = useApi(() => dashboardApi.vacancies(query),
    [selectedHostelId, days, applied?.from, applied?.to], { enabled: !!days || !!applied });

  const pickDays = (d) => {
    setDays(d);
    setParams({ days: String(d) }, { replace: true });
  };
  const applyRange = (e) => {
    e.preventDefault();
    if (!range.from || !range.to || range.to < range.from) return;
    setDays(null);
    setApplied({ ...range });
    setParams({ from: range.from, to: range.to }, { replace: true });
  };

  // Group items by expected checkout date (overdue first, then chronological).
  const sorted = useMemo(() => [...(data?.items || [])].sort((a, b) => (a.expectedCheckoutDate < b.expectedCheckoutDate ? -1 : a.expectedCheckoutDate > b.expectedCheckoutDate ? 1 : 0)), [data]);
  const totalPages = Math.max(1, Math.ceil(sorted.length / size));
  const curPage = Math.min(page, totalPages - 1);
  const pageStart = curPage * size;
  const groups = useMemo(() => {
    const items = sorted.slice(pageStart, pageStart + size);
    const out = [];
    const overdue = items.filter((v) => v.overdue);
    if (overdue.length) out.push({ key: 'overdue', title: 'Overdue checkouts', overdue: true, items: overdue });
    items.filter((v) => !v.overdue).forEach((v) => {
      const last = out[out.length - 1];
      if (last && !last.overdue && last.key === v.expectedCheckoutDate) last.items.push(v);
      else out.push({ key: v.expectedCheckoutDate, title: date(v.expectedCheckoutDate), items: [v] });
    });
    return out;
  }, [sorted, pageStart, size]);

  const s = data?.summary;
  const custom = !days;
  const windowLabel = data ? `${date(data.from)} – ${date(data.to)}` : '';

  return (
    <>
      <PageHeader title="Vacancy forecast" icon="bi-calendar2-week" subtitle="Beds that are free now and beds expected to become free"
        actions={<HostelFilter className="w-auto" id="vac-hostel" />} />

      <div className="hh-filters">
        <div className="nav nav-pills gap-1" role="tablist" aria-label="Forecast window">
          {PRESETS.map((d) => (
            <button key={d} type="button" role="tab" aria-selected={days === d} className={`nav-link ${days === d ? 'active' : ''}`} onClick={() => pickDays(d)}>
              Next {d} days
            </button>
          ))}
          <button type="button" role="tab" aria-selected={custom} className={`nav-link ${custom ? 'active' : ''}`} onClick={() => setDays(null)}>
            <Icon className="bi bi-calendar-range me-1" />Custom
          </button>
        </div>
        {custom && (
          <form className="d-flex flex-wrap gap-2 align-items-center" onSubmit={applyRange}>
            <label className="visually-hidden" htmlFor="vac-from">From</label>
            <input id="vac-from" type="date" className="form-control w-auto" value={range.from} onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} />
            <span className="text-muted-hh">to</span>
            <label className="visually-hidden" htmlFor="vac-to">To</label>
            <input id="vac-to" type="date" className="form-control w-auto" value={range.to} min={range.from} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} />
            <button type="submit" className="btn btn-primary" disabled={!range.from || !range.to || range.to < range.from}>Apply</button>
          </form>
        )}
      </div>

      {custom && !applied ? (
        <div className="hh-card"><div className="text-muted-hh text-center py-4">Choose a date range and press Apply.</div></div>
      ) : (
        <>
          <div className="row g-3 mb-3">
            {[
              { label: 'Free now', value: s && number(s.currentlyAvailableBeds), icon: 'bi-door-open', tone: 'red', to: '/rooms?status=HAS_AVAILABLE', hint: s && `of ${number(s.totalBeds)} beds` },
              { label: 'Becoming free', value: s && number(s.expectedToBecomeAvailable), icon: 'bi-hourglass-split', tone: 'yellow', hint: windowLabel },
              { label: 'Confirmed / estimated', value: s && `${number(s.confirmedCheckouts)} / ${number(s.estimatedCheckouts)}`, icon: 'bi-check2-circle', tone: 'green',
                hint: s && (s.expectedButReserved > 0 ? `${number(s.expectedButReserved)} already reserved` : 'checkout dates') },
              { label: 'Overdue checkouts', value: s && number(s.overdueCheckouts), icon: 'bi-exclamation-octagon', tone: s?.overdueCheckouts > 0 ? 'red' : 'gray' },
              { label: 'Reserved beds', value: s && number(s.reservedBeds), icon: 'bi-bookmark', tone: 'purple' },
              { label: 'In maintenance', value: s && number(s.maintenanceBeds), icon: 'bi-tools', tone: 'gray', to: '/rooms?status=UNDER_MAINTENANCE' },
            ].map((c) => (
              <div className="col-6 col-md-4 col-xl-2" key={c.label}><SummaryCard {...c} value={c.value ?? '…'} loading={loading && !data} /></div>
            ))}
          </div>

          {error ? <ErrorState error={error} onRetry={reload} /> : (
            <div className="hh-table-card">
              <div className="hh-table-toolbar">
                <span className="fw-semibold">Expected checkouts{windowLabel && <span className="text-muted-hh fw-normal small ms-2">{windowLabel}</span>}</span>
                {s && <div className="small text-muted-hh ms-md-auto">Next 7 days: <strong>{s.within7Days}</strong> · 15 days: <strong>{s.within15Days}</strong> · 30 days: <strong>{s.within30Days}</strong></div>}
              </div>
              {loading && !data ? <LoadingState /> : data && data.items.length === 0 ? (
                <EmptyState icon="bi-calendar-check" title="No checkouts expected in this period" message="Set expected checkout dates on admissions to build the forecast." />
              ) : (
                <div style={{ opacity: loading ? 0.6 : 1 }}>
                  {/* Desktop table (grouped by checkout date) */}
                  <div className="hh-table-wrapper d-none d-md-block">
                    <table className="hh-crm-table">
                      <thead>
                        <tr>
                          <th className="hh-sno">S.No</th><th>Bed</th><th>Resident</th><th>Expected checkout</th><th className="text-center">Remaining</th>
                          <th className="text-center">Source</th><th className="text-center">Freeing in room</th><th>Booking</th><th className="text-center">Actions</th>
                        </tr>
                      </thead>
                      {groups.map((g, gi) => (
                        <tbody key={g.key}>
                          <tr>
                            <td colSpan={9} className={`small fw-semibold ${g.overdue ? 'text-danger' : ''}`} style={{ background: 'var(--hh-card-alt, #F9FAFB)' }}>
                              <Icon className={`bi ${g.overdue ? 'bi-exclamation-triangle' : 'bi-calendar-event'} me-1`} aria-hidden="true" />{g.title}
                              <span className="text-muted-hh fw-normal ms-2">{g.items.length} bed{g.items.length === 1 ? '' : 's'}</span>
                            </td>
                          </tr>
                          {g.items.map((v, i) => (
                            <tr key={v.admissionId}>
                              <td className="hh-sno">{pageStart + groups.slice(0, gi).reduce((n, x) => n + x.items.length, 0) + i + 1}</td>
                              <td>
                                <Link to={`/rooms?roomId=${v.roomId}`} className="hh-cell-primary">Room {v.roomNumber} · Bed {v.bedLabel}</Link>
                                <span className="hh-cell-sub">{!selectedHostelId && `${v.hostelName} · `}{v.buildingName} · {v.floorName}</span>
                              </td>
                              <td>
                                <Link to={`/residents/${v.residentId}`} className="hh-cell-primary">{v.residentName}</Link>
                                {v.residentMobile && <span className="hh-contact-line"><Icon className="bi bi-telephone" />{v.residentMobile}</span>}
                              </td>
                              <td className="text-nowrap">{date(v.expectedCheckoutDate)}</td>
                              <td className="text-center"><Remaining v={v} /></td>
                              <td className="text-center"><SourceBadge confirmed={v.confirmed} /></td>
                              <td className="text-center">{v.roomBedsFreeingInWindow}</td>
                              <td><Booking v={v} /></td>
                              <td className="text-center">
                                <RowActions>
                                  <RowAction variant="view" label="Open resident profile" onClick={() => navigate(`/residents/${v.residentId}`)} />
                                  {v.residentMobile && <RowAction variant="call" label={`Call ${v.residentMobile}`} href={telLink(v.residentMobile)} />}
                                  {v.residentMobile && <RowAction variant="whatsapp" label="Open chat in WhatsApp" href={whatsappLink(v.residentMobile, `Hi ${v.residentName}, `)} />}
                                </RowActions>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      ))}
                    </table>
                  </div>

                  {/* Mobile cards */}
                  <div className="d-md-none hh-card-list">
                    {groups.map((g) => (
                      <div key={g.key} className="mb-3">
                        <div className={`small fw-bold mb-2 ${g.overdue ? 'text-danger' : 'text-muted-hh'}`}>
                          <Icon className={`bi ${g.overdue ? 'bi-exclamation-triangle' : 'bi-calendar-event'} me-1`} aria-hidden="true" />{g.title} · {g.items.length}
                        </div>
                        {g.items.map((v) => (
                          <div key={v.admissionId} className="hh-list-card">
                            <div className="d-flex justify-content-between align-items-start gap-2">
                              <div className="min-w-0">
                                <Link to={`/rooms?roomId=${v.roomId}`} className="hh-cell-primary">Room {v.roomNumber} · Bed {v.bedLabel}</Link>
                                <span className="hh-cell-sub">{v.hostelName} · {v.buildingName} · {v.floorName}</span>
                              </div>
                              <Remaining v={v} />
                            </div>
                            <div className="d-flex justify-content-between align-items-center gap-2 mt-2">
                              <div className="min-w-0">
                                <Link to={`/residents/${v.residentId}`}>{v.residentName}</Link>
                                <span className="hh-cell-sub">Leaves {date(v.expectedCheckoutDate)}</span>
                              </div>
                              {v.residentMobile && (
                                <RowActions>
                                  <RowAction variant="call" label={`Call ${v.residentMobile}`} href={telLink(v.residentMobile)} />
                                  <RowAction variant="whatsapp" label="Open chat in WhatsApp" href={whatsappLink(v.residentMobile, `Hi ${v.residentName}, `)} />
                                </RowActions>
                              )}
                            </div>
                            <div className="d-flex flex-wrap gap-2 mt-2 align-items-center">
                              <SourceBadge confirmed={v.confirmed} />
                              {v.roomBedsFreeingInWindow > 1 && <span className="small text-muted-hh">{v.roomBedsFreeingInWindow} beds freeing in this room</span>}
                              <Booking v={v} />
                            </div>
                          </div>
                        ))}
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {data && data.items.length > 0 && (
                <Pagination page={curPage} totalPages={totalPages} totalElements={sorted.length} size={size} noun="beds"
                  onChange={setPage} onSizeChange={(n) => { setSize(n); setPage(0); }} />
              )}
            </div>
          )}
        </>
      )}
    </>
  );
}