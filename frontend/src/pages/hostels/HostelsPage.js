import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import AuthImage from '../../components/common/AuthImage';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import HostelFormModal, { HOSTEL_TYPES } from '../../components/hostels/HostelFormModal';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { hostelApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { number, percent } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { UsageChip, SubscriptionNotice, hostelAddStatus, useSubscription } from '../../components/subscription/UsagePanel';

const typeLabel = (t) => HOSTEL_TYPES.find((x) => x.value === t)?.label || t;
const TYPE_ICON = { BOYS: 'bi-gender-male', GIRLS: 'bi-gender-female', CO_LIVING: 'bi-people', OTHER: 'bi-house' };

export default function HostelsPage() {
  const { can } = useAuth();
  const { hostels, loading, error, reload, selectHostel } = useHostels();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = new, object = edit
  const [deleting, setDeleting] = useState(null);
  const canManage = can('HOSTEL_MANAGE');
  const { summary: sub } = useSubscription();
  const addStatus = hostelAddStatus(sub);
  const addButton = canManage && (
    <button type="button" className="btn btn-primary" disabled={addStatus.blocked} title={addStatus.blocked ? addStatus.message : undefined} onClick={() => setEditing(null)}>
      <Icon className="bi bi-plus-lg me-1" />Add hostel
    </button>
  );

  useEffect(() => { document.title = 'Hostels · HostelHub'; }, []);
  useEffect(() => { reload(); }, [reload]);

  useEffect(() => {
    if (params.get('new') === '1' && canManage) setEditing(null);
  }, [params, canManage]);

  const closeForm = () => {
    setEditing(undefined);
    if (params.get('new')) { params.delete('new'); setParams(params, { replace: true }); }
  };

  const onSaved = async (saved) => {
    const isNew = editing === null;
    closeForm();
    await reload();
    if (isNew && saved?.id) navigate(`/hostels/${saved.id}`);
  };

  const remove = async () => {
    try {
      await hostelApi.remove(deleting.id);
      notify(`Hostel "${deleting.name}" removed.`);
      setDeleting(null);
      reload();
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };


  let body;
  if (loading && hostels.length === 0) body = <LoadingState label="Loading hostels…" />;
  else if (error) body = <ErrorState error={error} onRetry={reload} />;
  else if (hostels.length === 0) {
    body = (
      <div className="hh-card">
        <EmptyState icon="bi-houses" title="No hostels yet" message="Add your first hostel, then set up its buildings, floors, rooms and beds."
          action={addButton} />
      </div>
    );
  } else {
    body = (
      <div className="row g-3">
        {hostels.map((h) => {
          const s = h.stats || {};
          return (
            <div className="col-12 col-md-6 col-xl-4" key={h.id}>
              <article className="hh-card p-0 h-100 d-flex flex-column overflow-hidden">
                <Link to={`/hostels/${h.id}`} className="hh-hostel-img text-decoration-none" style={{ height: 120 }} aria-label={`Open ${h.name}`}>
                  {h.imageUrl ? <AuthImage src={h.imageUrl} alt="" fallback={<Icon className="bi bi-buildings" />} /> : <Icon className="bi bi-buildings" />}
                </Link>
                <div className="p-3 d-flex flex-column flex-grow-1">
                  <div className="d-flex justify-content-between align-items-start gap-2">
                    <div className="min-w-0">
                      <h2 className="h6 fw-bold mb-1 text-truncate"><Link to={`/hostels/${h.id}`} className="text-reset">{h.name}</Link></h2>
                      <div className="small text-muted-hh text-truncate"><Icon className="bi bi-geo-alt me-1" />{h.address}, {h.city}, {h.state} {h.pinCode}</div>
                    </div>
                    <span className={`badge hh-badge ${h.status === 'ACTIVE' ? 'hh-badge-success' : 'hh-badge-secondary'}`}>
                      <Icon className={`bi ${h.status === 'ACTIVE' ? 'bi-check-circle' : 'bi-pause-circle'} me-1`} />{h.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                  <div className="d-flex flex-wrap gap-2 small mt-2">
                    <span className="badge hh-badge hh-badge-info"><Icon className={`bi ${TYPE_ICON[h.hostelType] || 'bi-house'} me-1`} />{typeLabel(h.hostelType)}</span>
                    <span className="text-muted-hh"><Icon className="bi bi-building me-1" />{number(h.buildingCount)} building{h.buildingCount === 1 ? '' : 's'}</span>
                    {h.managerName && <span className="text-muted-hh"><Icon className="bi bi-person-badge me-1" />{h.managerName}</span>}
                  </div>
                  <div className="d-flex justify-content-between small mt-3">
                    <span className="fw-semibold">{percent(s.occupancyPercent)} occupied</span>
                    <span className="text-muted-hh">{number(s.totalRooms)} rooms</span>
                  </div>
                  <div className="hh-progress mt-1" aria-hidden="true"><span style={{ width: `${Math.min(100, Number(s.occupancyPercent) || 0)}%` }} /></div>
                  <div className="hh-hostel-stats" style={{ gridTemplateColumns: 'repeat(4, 1fr)' }}>
                    <div><strong>{number(s.totalBeds)}</strong>Beds</div>
                    <div><strong className="text-success">{number(s.occupiedBeds)}</strong>Occupied</div>
                    <div><strong className="text-danger">{number(s.vacantBeds)}</strong>Vacant</div>
                    <div><strong>{number(s.reservedBeds)}</strong>Reserved</div>
                  </div>
                  {(h.contactNumber || h.email) && (
                    <div className="small text-muted-hh mt-2 text-truncate">
                      {h.contactNumber && <span className="me-3"><Icon className="bi bi-telephone me-1" />{h.contactNumber}</span>}
                      {h.email && <span><Icon className="bi bi-envelope me-1" />{h.email}</span>}
                    </div>
                  )}
                  <div className="d-flex flex-wrap gap-2 mt-auto pt-3">
                    <Link to={`/hostels/${h.id}`} className="btn btn-sm btn-soft"><Icon className="bi bi-diagram-3 me-1" />Rooms &amp; setup</Link>
                    {canManage && <button type="button" className="btn btn-sm btn-light" onClick={() => setEditing(h)} aria-label={`Edit ${h.name}`}><Icon className="bi bi-pencil me-1" />Edit</button>}
                    {canManage && <button type="button" className="btn btn-sm btn-light text-danger" onClick={() => setDeleting(h)} aria-label={`Delete ${h.name}`}><Icon className="bi bi-trash" /></button>}
                  </div>
                </div>
              </article>
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <>
      <PageHeader title="Hostels" icon="bi-houses" subtitle="Your properties, their occupancy and structure"
        actions={addButton} />
      {sub && (
        <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
          <UsageChip label="Hostels" used={sub.usage.hostels} limit={sub.limits.maxHostels} warnPercent={sub.warningPercent} />
          {addStatus.blocked && <span className="small text-muted-hh">{addStatus.message}</span>}
        </div>
      )}
      <SubscriptionNotice summary={sub} only={(w) => /^Hostels|subscription/i.test(w)} />
      {body}
      <HostelFormModal show={editing !== undefined} hostel={editing || null} onClose={closeForm} onSaved={onSaved} />
      <ConfirmDialog show={!!deleting} title="Delete hostel" variant="danger" confirmLabel="Delete hostel"
        message={deleting && `Remove "${deleting.name}"? Its buildings, rooms and beds will no longer be available. Hostels with active residents cannot be removed.`}
        onConfirm={remove} onClose={() => setDeleting(null)} />
    </>
  );
}
