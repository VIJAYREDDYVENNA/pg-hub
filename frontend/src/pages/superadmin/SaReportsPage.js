import { useMemo } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import PageHeader from '../../components/common/PageHeader';
import SummaryCard from '../../components/common/SummaryCard';
import DataTable from '../../components/common/DataTable';
import StatusBadge from '../../components/common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { number } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { OWNER_STATUS, OwnerLink, percentOf, saCrumbs } from './saCommon';

const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;

/** Platform-level report: occupancy and size per owner and per city, with CSV export. No financial data. */
export default function SaReportsPage() {
  const owners = useApi(() => adminApi.owners(), []);
  const hostels = useApi(() => adminApi.hostels(), []);

  const rows = useMemo(() => (owners.data || []).map((o) => ({ ...o, occupancy: percentOf(o.residents, o.beds) })), [owners.data]);
  const byCity = useMemo(() => {
    const m = new Map();
    (hostels.data || []).forEach((h) => m.set(h.city, (m.get(h.city) || 0) + 1));
    return [...m.entries()].map(([city, count]) => ({ city, count })).sort((a, b) => b.count - a.count).slice(0, 10);
  }, [hostels.data]);

  const crumbs = saCrumbs({ label: 'Platform Reports' });
  const error = owners.error || hostels.error;
  if (error) return <><PageHeader title="Platform reports" icon="bi-file-earmark-bar-graph" breadcrumb={crumbs} /><ErrorState error={error} onRetry={() => { owners.reload(); hostels.reload(); }} /></>;
  if (!owners.data || !hostels.data) return <LoadingState />;

  const totals = rows.reduce((t, o) => ({ beds: t.beds + o.beds, residents: t.residents + o.residents, rooms: t.rooms + o.rooms }), { beds: 0, residents: 0, rooms: 0 });

  const exportCsv = () => {
    const head = ['Owner', 'Status', 'Hostels', 'Managers', 'Rooms', 'Beds', 'Residents', 'Occupancy %'];
    const lines = [head, ...rows.map((o) => [o.businessName, o.status, o.hostels, o.managers, o.rooms, o.beds, o.residents, o.occupancy])]
      .map((r) => r.map(csvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([lines], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'platform-owner-report.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const columns = [
    { key: 'owner', label: 'Owner', sortValue: (o) => o.businessName.toLowerCase(), render: (o) => <OwnerLink id={o.id}>{o.businessName}</OwnerLink> },
    { key: 'status', label: 'Status', align: 'center', sortValue: (o) => o.status, render: (o) => <StatusBadge map={OWNER_STATUS} value={o.status} /> },
    { key: 'hostels', label: 'Hostels', align: 'center', sortValue: (o) => o.hostels, render: (o) => o.hostels },
    { key: 'managers', label: 'Managers', align: 'center', sortValue: (o) => o.managers, render: (o) => o.managers },
    { key: 'rooms', label: 'Rooms', align: 'center', sortValue: (o) => o.rooms, hideOnMobile: true, render: (o) => number(o.rooms) },
    { key: 'beds', label: 'Beds', align: 'center', sortValue: (o) => o.beds, render: (o) => number(o.beds) },
    { key: 'residents', label: 'Residents', align: 'center', sortValue: (o) => o.residents, render: (o) => number(o.residents) },
    { key: 'occupancy', label: 'Occupancy', align: 'center', sortValue: (o) => o.occupancy, render: (o) => `${o.occupancy}%` },
  ];

  return (
    <>
      <PageHeader title="Platform reports" icon="bi-file-earmark-bar-graph" subtitle="Size and occupancy across owner accounts"
        breadcrumb={crumbs} actions={<button type="button" className="btn btn-light" onClick={exportCsv} disabled={rows.length === 0}><Icon className="bi bi-download me-1" />Export CSV</button>} />
      <div className="row g-3 mb-3">
        <div className="col-6 col-lg-3"><SummaryCard label="Owners" value={number(rows.length)} icon="bi-people-fill" tone="orange" /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Beds" value={number(totals.beds)} icon="bi-box-seam-fill" tone="blue" /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Residents" value={number(totals.residents)} icon="bi-people-fill" tone="green" /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Platform occupancy" value={`${percentOf(totals.residents, totals.beds)}%`} icon="bi-pie-chart-fill" tone="yellow" /></div>
      </div>
      <div className="row g-3">
        <div className="col-xl-8">
          <DataTable tableId="sa-report-owners" columns={columns} rows={rows} serial
            toolbar={<span className="fw-semibold"><Icon className="bi bi-people me-2 text-primary-hh" />Owner-wise summary</span>}
            empty={<EmptyState icon="bi-people" title="No owners yet" />} />
        </div>
        <div className="col-xl-4">
          <section className="hh-card h-100" aria-labelledby="city-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="city-title"><Icon className="bi bi-geo-alt me-2 text-primary-hh" />Hostels by city</h2></div>
            {byCity.length === 0 ? <EmptyState icon="bi-houses" title="No hostels yet" /> : (
              <div style={{ width: '100%', height: 300 }} className="p-2">
                <ResponsiveContainer>
                  <BarChart data={byCity} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--hh-border)" horizontal={false} />
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
                    <YAxis type="category" dataKey="city" width={90} tick={{ fontSize: 12 }} />
                    <Tooltip />
                    <Bar dataKey="count" name="Hostels" fill="#2F6FED" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
        </div>
      </div>
    </>
  );
}
