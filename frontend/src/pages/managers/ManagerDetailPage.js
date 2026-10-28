import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import Avatar from '../../components/common/Avatar';
import AssignHostelsModal from '../../components/managers/AssignHostelsModal';
import ResetPasswordModal from '../../components/managers/ResetPasswordModal';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { dateTime } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { MANAGER_STATUS, updateBody, useManagers } from './managerCommon';

const crumb = (label) => [{ label: 'Dashboard', to: '/' }, { label: 'Managers', to: '/managers' }, { label }];

export default function ManagerDetailPage() {
  const { managerId } = useParams();
  const { hostels } = useHostels();
  const { notify } = useToast();
  const { managers, data, error, reload } = useManagers();
  const perms = useApi(() => userApi.permissions(), []);
  const [assigning, setAssigning] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [toggling, setToggling] = useState(false);
  const manager = useMemo(() => managers.find((m) => String(m.id) === String(managerId)), [managers, managerId]);

  const grouped = useMemo(() => {
    const granted = new Set(manager?.permissions || []);
    return (perms.data || []).filter((p) => granted.has(p.code)).reduce((acc, p) => {
      (acc[p.category || 'Other'] = acc[p.category || 'Other'] || []).push(p.name);
      return acc;
    }, {});
  }, [perms.data, manager]);

  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (!data) return <LoadingState />;
  if (!manager) {
    return <><PageHeader title="Manager not found" icon="bi-person-gear" breadcrumb={crumb('Not found')} />
      <div className="hh-card hh-state py-5"><p className="mb-0">This manager does not exist or is not yours.</p></div></>;
  }

  const assigned = (manager.hostelIds || []).map((id) => hostels.find((h) => h.id === id)).filter(Boolean);
  const toggle = async () => {
    try {
      await userApi.update(manager.id, updateBody(manager, { enabled: !manager.enabled }));
      notify(manager.enabled ? `${manager.fullName} deactivated and signed out` : `${manager.fullName} activated`);
      setToggling(false);
      reload();
    } catch (e) {
      setToggling(false);
      notify(errorMessage(e, 'Could not update the manager.'), 'danger');
    }
  };

  return (
    <>
      <PageHeader title={manager.fullName} icon="bi-person-gear" subtitle="Hostel manager" breadcrumb={crumb(manager.fullName)}
        actions={<>
          <Link to={`/managers/${manager.id}/edit`} className="btn btn-light"><Icon className="bi bi-pencil me-1" />Edit</Link>
          <button type="button" className="btn btn-light" onClick={() => setAssigning(true)}><Icon className="bi bi-houses me-1" />Change assignments</button>
          <button type="button" className="btn btn-light" onClick={() => setResetting(true)}><Icon className="bi bi-key me-1" />Reset password</button>
          <button type="button" className={`btn ${manager.enabled ? 'btn-outline-danger' : 'btn-primary'}`} onClick={() => setToggling(true)}>
            <Icon className={`bi ${manager.enabled ? 'bi-slash-circle' : 'bi-play-circle'} me-1`} />{manager.enabled ? 'Deactivate' : 'Activate'}
          </button>
        </>} />

      <div className="row g-3 mb-3">
        <div className="col-lg-5">
          <section className="hh-card h-100 p-3" aria-label="Manager profile">
            <div className="d-flex align-items-center gap-3 mb-3">
              <Avatar name={manager.fullName} />
              <div className="min-w-0"><div className="fw-semibold">{manager.fullName}</div>
                <StatusBadge map={MANAGER_STATUS} value={String(manager.enabled)} /></div>
            </div>
            <dl className="hh-dl mb-0">
              <dt>Email</dt><dd className="text-break">{manager.email}</dd>
              <dt>Mobile</dt><dd>{manager.phone || '—'}</dd>
              <dt>Created</dt><dd>{dateTime(manager.createdAt)}</dd>
              <dt>Last sign-in</dt><dd>{manager.lastLoginAt ? dateTime(manager.lastLoginAt) : 'Never'}</dd>
            </dl>
          </section>
        </div>
        <div className="col-lg-7">
          <section className="hh-card h-100" aria-labelledby="perm-title">
            <div className="hh-card-header"><h2 className="hh-card-title" id="perm-title"><Icon className="bi bi-shield-check me-2 text-primary-hh" />Role &amp; permissions</h2>
              <span className="badge hh-badge hh-badge-info">Hostel manager</span></div>
            <div className="p-3 pt-0">
              {Object.keys(grouped).length === 0 ? <div className="small text-muted-hh">No permissions granted.</div> : (
                <div className="row g-2">
                  {Object.entries(grouped).map(([cat, names]) => (
                    <div className="col-sm-6" key={cat}>
                      <div className="fw-semibold small text-uppercase text-muted-hh">{cat}</div>
                      <ul className="mb-0 ps-3 small">{names.map((n) => <li key={n}>{n}</li>)}</ul>
                    </div>
                  ))}
                </div>
              )}
              <div className="small text-muted-hh mt-3">Permissions apply only inside the assigned hostels. Fine-tune them under Staff → Logins.</div>
            </div>
          </section>
        </div>
      </div>

      <section aria-labelledby="assigned-title">
        <div className="d-flex align-items-center justify-content-between mb-2">
          <h2 className="h6 mb-0" id="assigned-title">Assigned hostels ({assigned.length})</h2>
          <button type="button" className="btn btn-sm btn-light" onClick={() => setAssigning(true)}>Manage assignments</button>
        </div>
        {assigned.length === 0 ? <EmptyState icon="bi-houses" title="No hostels assigned" message="This manager cannot see any hostel yet." /> : (
          <div className="row g-3">
            {assigned.map((h) => (
              <div className="col-md-6 col-xl-4" key={h.id}>
                <div className="hh-card p-3 h-100">
                  <div className="fw-semibold">{h.name}</div>
                  <div className="small text-muted-hh mb-2"><Icon className="bi bi-geo-alt me-1" />{[h.city, h.state].filter(Boolean).join(', ')}</div>
                  <div className="d-flex gap-3 small">
                    <span>{h.stats?.totalRooms ?? 0} rooms</span><span>{h.stats?.totalBeds ?? 0} beds</span><span>{h.stats?.occupiedBeds ?? 0} residents</span>
                  </div>
                  <Link to={`/hostels/${h.id}`} className="small mt-2 d-inline-block">Open hostel</Link>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <ResetPasswordModal user={resetting ? manager : null} onClose={() => setResetting(false)} />
      <AssignHostelsModal manager={assigning ? manager : null} managers={managers} onClose={() => setAssigning(false)}
        onSaved={() => { setAssigning(false); reload(); }} />
      {toggling && (
        <ConfirmDialog show title={manager.enabled ? 'Deactivate manager' : 'Activate manager'}
          message={manager.enabled
            ? `Deactivate ${manager.fullName}? They are signed out immediately and cannot sign in until reactivated. Their hostel assignments are kept.`
            : `Activate ${manager.fullName}? They can sign in again and will see their assigned hostels.`}
          confirmLabel={manager.enabled ? 'Deactivate' : 'Activate'} variant={manager.enabled ? 'danger' : 'primary'}
          onConfirm={toggle} onClose={() => setToggling(false)} />
      )}
    </>
  );
}
