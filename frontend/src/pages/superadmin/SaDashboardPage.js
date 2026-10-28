import { Link } from 'react-router-dom';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, Bar, BarChart } from 'recharts';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import PageHeader from '../../components/common/PageHeader';
import SummaryCard from '../../components/common/SummaryCard';
import StatusBadge from '../../components/common/StatusBadge';
import Donut from '../../components/common/Donut';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { date, number } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { ActivityItem, OWNER_STATUS, OwnerLink, saCrumbs } from './saCommon';

const STATUS_COLORS = { Active: '#198754', Suspended: '#F5A524', Inactive: '#98A2B3' };

export default function SaDashboardPage() {
  const { data: d, loading, error, reload } = useApi(() => adminApi.dashboard(), []);

  const actions = (
    <Link to="/super-admin/owners/new" className="btn btn-primary"><Icon className="bi bi-person-plus me-1" />Create owner</Link>
  );
  if (error) return <><PageHeader title="Platform dashboard" icon="bi-speedometer2" breadcrumb={saCrumbs({ label: 'Dashboard' })} actions={actions} /><ErrorState error={error} onRetry={reload} /></>;
  if (loading && !d) return <LoadingState />;

  const growth = (d.growth || []).map((g) => ({ ...g, month: g.month.slice(2) }));

  return (
    <>
      <PageHeader title="Platform dashboard" icon="bi-speedometer2" subtitle="Owner accounts and hostels across the whole platform"
        breadcrumb={saCrumbs({ label: 'Dashboard' })} actions={actions} />

      <div className="row g-3 mb-3">
        <div className="col-6 col-xl-4"><SummaryCard label="Total owners" value={number(d.totalOwners)} icon="bi-people-fill" tone="orange" to="/super-admin/owners" /></div>
        <div className="col-6 col-xl-4"><SummaryCard label="Active owners" value={number(d.activeOwners)} icon="bi-check-circle-fill" tone="green" /></div>
        <div className="col-6 col-xl-4"><SummaryCard label="Suspended owners" value={number(d.suspendedOwners)} icon="bi-pause-circle-fill" tone="yellow"
          hint={d.inactiveOwners ? `${d.inactiveOwners} inactive` : undefined} /></div>
        <div className="col-6 col-xl-4"><SummaryCard label="Hostels on platform" value={number(d.totalHostels)} icon="bi-houses" tone="blue" to="/super-admin/hostels" /></div>
        <div className="col-6 col-xl-4"><SummaryCard label="Managers" value={number(d.totalManagers)} icon="bi-person-gear" tone="purple" /></div>
        <div className="col-6 col-xl-4"><SummaryCard label="Residents" value={number(d.totalResidents)} icon="bi-person-fill" tone="orange" hint={`${number(d.totalBeds)} beds in total`} /></div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-xl-8">
          <section className="hh-card h-100" aria-labelledby="growth-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="growth-title"><Icon className="bi bi-graph-up-arrow me-2 text-primary-hh" />Platform growth</h2>
              <span className="small text-muted-hh">Cumulative, last 6 months</span></div>
            <div style={{ width: '100%', height: 260 }} className="p-2">
              <ResponsiveContainer>
                <LineChart data={growth} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--hh-border)" />
                  <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} width={32} />
                  <Tooltip /><Legend />
                  <Line type="monotone" dataKey="owners" name="Owners" stroke="#FF5A0A" strokeWidth={2.5} dot />
                  <Line type="monotone" dataKey="hostels" name="Hostels" stroke="#2F6FED" strokeWidth={2.5} dot />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </section>
        </div>
        <div className="col-xl-4">
          <section className="hh-card h-100" aria-labelledby="status-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="status-title"><Icon className="bi bi-pie-chart-fill me-2 text-primary-hh" />Owner account status</h2></div>
            <div className="d-flex align-items-center gap-4 p-3 flex-wrap">
              <Donut size={130} thickness={18} label="Owner accounts by status"
                segments={d.ownersByStatus.map((s) => ({ label: s.label, value: s.value, color: STATUS_COLORS[s.label] }))}>
                <div className="text-center"><div className="fs-4 fw-bold lh-1">{d.totalOwners}</div><div className="small text-muted-hh">owners</div></div>
              </Donut>
              <ul className="list-unstyled mb-0">
                {d.ownersByStatus.map((s) => (
                  <li key={s.label} className="d-flex align-items-center gap-2 mb-1">
                    <span style={{ width: 12, height: 12, borderRadius: 3, background: STATUS_COLORS[s.label] }} />{s.label}<strong className="ms-auto ps-3">{s.value}</strong>
                  </li>
                ))}
              </ul>
            </div>
          </section>
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-xl-6">
          <section className="hh-card h-100" aria-labelledby="byowner-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="byowner-title"><Icon className="bi bi-bar-chart me-2 text-primary-hh" />Hostels by owner</h2></div>
            {d.hostelsByOwner.length === 0 ? <EmptyState icon="bi-houses" title="No hostels yet" /> : (
              <div style={{ width: '100%', height: 260 }} className="p-2">
                <ResponsiveContainer>
                  <BarChart data={d.hostelsByOwner} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--hh-border)" horizontal={false} />
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
                    <YAxis type="category" dataKey="label" width={130} tick={{ fontSize: 12 }} />
                    <Tooltip />
                    <Bar dataKey="value" name="Hostels" fill="#FF5A0A" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
        </div>
        <div className="col-xl-6">
          <section className="hh-card h-100" aria-labelledby="activity-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="activity-title"><Icon className="bi bi-clock-history me-2 text-primary-hh" />Recent platform activity</h2>
              <Link to="/super-admin/activity" className="small">View all</Link></div>
            {d.recentActivity.length === 0 ? <EmptyState icon="bi-clock-history" title="No activity yet" /> : (
              <ul className="list-unstyled mb-0 px-3">{d.recentActivity.map((a) => <ActivityItem key={a.id} a={a} />)}</ul>
            )}
          </section>
        </div>
      </div>

      <div className="row g-3">
        <div className="col-xl-6">
          <section className="hh-card h-100" aria-labelledby="recent-owners-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="recent-owners-title"><Icon className="bi bi-person-plus me-2 text-primary-hh" />Recent owner registrations</h2>
              <Link to="/super-admin/owners" className="small">All owners</Link></div>
            <div className="table-responsive"><table className="table table-sm align-middle mb-0">
              <thead><tr><th>Owner</th><th>Registered</th><th>Status</th></tr></thead>
              <tbody>{d.recentOwners.map((o) => (
                <tr key={o.id}><td><OwnerLink id={o.id}>{o.businessName}</OwnerLink><div className="small text-muted-hh">{o.email}</div></td>
                  <td>{date(o.createdAt)}</td><td><StatusBadge map={OWNER_STATUS} value={o.status} /></td></tr>
              ))}</tbody>
            </table></div>
          </section>
        </div>
        <div className="col-xl-6">
          <section className="hh-card h-100" aria-labelledby="recent-hostels-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="recent-hostels-title"><Icon className="bi bi-houses me-2 text-primary-hh" />Recently created hostels</h2>
              <Link to="/super-admin/hostels" className="small">All hostels</Link></div>
            {d.recentHostels.length === 0 ? <EmptyState icon="bi-houses" title="No hostels yet" /> : (
              <div className="table-responsive"><table className="table table-sm align-middle mb-0">
                <thead><tr><th>Hostel</th><th>Owner</th><th>Created</th></tr></thead>
                <tbody>{d.recentHostels.map((h) => (
                  <tr key={h.id}><td>{h.name}<div className="small text-muted-hh">{h.city}</div></td>
                    <td><OwnerLink id={h.ownerId}>{h.ownerName}</OwnerLink></td><td>{date(h.createdAt)}</td></tr>
                ))}</tbody>
              </table></div>
            )}
          </section>
        </div>
      </div>
    </>
  );
}
