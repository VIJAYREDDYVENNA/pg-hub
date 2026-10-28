import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import useApi from '../hooks/useApi';
import { dashboardApi } from '../services/endpoints';
import { useAuth } from '../context/AuthContext';
import { useHostels } from '../context/HostelContext';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import StatCards from '../components/dashboard/StatCards';
import HostelCard from '../components/dashboard/HostelCard';
import BuildingView from '../components/building/BuildingView';
import { AlertsCard, FloorSummaryCard, QuickActionsCard } from '../components/dashboard/SideCards';
import { PendingPaymentsCard, UpcomingVacanciesCard } from '../components/dashboard/BottomCards';
import Icon from '../components/common/Icon';

export default function DashboardPage() {
  const { can } = useAuth();
  const { hostels, loading: hostelsLoading, error: hostelsError, reload: reloadHostels, selectedHostelId, selectHostel } = useHostels();
  const { data: s, error, reload } = useApi(() => dashboardApi.summary(selectedHostelId), [selectedHostelId]);
  const activity = useApi(() => dashboardApi.activity(selectedHostelId), [selectedHostelId]);
  const [floorRooms, setFloorRooms] = useState({ rooms: [], label: '' });
  const planHostel = hostels.find((h) => h.id === selectedHostelId) || hostels[0];

  useEffect(() => { document.title = 'Dashboard · HostelHub'; }, []);

  if (hostelsLoading && hostels.length === 0) return <LoadingState label="Loading your hostels…" />;
  if (hostelsError) return <ErrorState error={hostelsError} onRetry={reloadHostels} />;
  if (hostels.length === 0) {
    return (
      <div className="hh-card">
        <EmptyState icon="bi-houses" title="Welcome to HostelHub" message="Add your first hostel to start managing rooms, residents and payments."
          action={can('HOSTEL_MANAGE') && <Link to="/hostels?new=1" className="btn btn-primary"><Icon className="bi bi-plus-lg me-1" />Add hostel</Link>} />
      </div>
    );
  }

  return (
    <>
      {error && <ErrorState error={error} onRetry={reload} compact />}
      <StatCards s={s} canPay={can('PAYMENT_VIEW')} />

      <section className="mb-3" aria-label="Hostels">
        <div className="hh-hostel-strip">
          {hostels.map((h) => (
            <HostelCard key={h.id} hostel={h} active={h.id === planHostel?.id}
              onSelect={(id) => selectHostel(id === selectedHostelId ? null : id)} />
          ))}
        </div>
        {selectedHostelId && (
          <div className="small text-muted-hh mt-1">
            Figures show <strong>{planHostel?.name}</strong> only.{' '}
            <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => selectHostel(null)}>Show all hostels</button>
          </div>
        )}
      </section>

      <div className="row g-3 mb-3">
        <div className="col-xl-9 min-w-0">
          <BuildingView hostel={planHostel} onRooms={(rooms, label) => setFloorRooms({ rooms, label })} />
        </div>
        <div className="col-xl-3">
          <div className="row g-3">
            <div className="col-md-6 col-xl-12"><FloorSummaryCard rooms={floorRooms.rooms} title={floorRooms.label} /></div>
            <div className="col-md-6 col-xl-12"><QuickActionsCard /></div>
          </div>
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-lg-4"><UpcomingVacanciesCard hostelId={selectedHostelId} /></div>
        {can('PAYMENT_VIEW') && (
          <div className="col-lg-4">
            <PendingPaymentsCard activity={activity.data} loading={activity.loading} error={activity.error} onRetry={activity.reload} />
          </div>
        )}
        <div className="col-lg-4"><AlertsCard /></div>
      </div>
    </>
  );
}
