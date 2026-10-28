import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import SummaryCard from '../../components/common/SummaryCard';
import StatusBadge from '../../components/common/StatusBadge';
import Modal from '../../components/common/Modal';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { PASSWORD_RULE } from '../auth/ResetPasswordPage';
import { date, dateTime, number } from '../../utils/format';
import Icon from '../../components/common/Icon';
import OwnerStatusDialog from './OwnerStatusDialog';
import { ActivityItem, HOSTEL_STATUS, OWNER_STATUS, percentOf, saCrumbs } from './saCommon';
import SubscriptionCard from '../../components/subscription/SubscriptionCard';

function ResetPasswordModal({ show, owner, onClose }) {
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm();
  const close = () => { reset(); setServerError(null); onClose(); };
  const onSubmit = async ({ newPassword }) => {
    try {
      await adminApi.resetOwnerPassword(owner.id, newPassword);
      notify('Password reset. The owner has been signed out everywhere.');
      close();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not reset the password.'));
    }
  };
  return (
    <Modal show={show} title={`Reset password · ${owner.businessName}`} onClose={close}
      footer={<>
        <button type="button" className="btn btn-light" onClick={close} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="reset-owner-pwd" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Reset password
        </button>
      </>}>
      <form id="reset-owner-pwd" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <FormField label="New password" name="newPassword" type="password" autoComplete="new-password" register={register} errors={errors}
          rules={PASSWORD_RULE} help="Share it securely. The owner can change it after signing in." />
      </form>
    </Modal>
  );
}

export default function SaOwnerDetailPage() {
  const { ownerId } = useParams();
  const { data, loading, error, reload } = useApi(() => adminApi.owner(ownerId), [ownerId]);
  const [change, setChange] = useState(null);
  const [resetting, setResetting] = useState(false);

  const crumbs = saCrumbs({ label: 'Owners', to: '/super-admin/owners' }, { label: data?.owner?.businessName || 'Owner' });
  if (error) return <><PageHeader title="Owner" icon="bi-person" breadcrumb={crumbs} /><ErrorState error={error} onRetry={reload} /></>;
  if (loading && !data) return <LoadingState />;
  const { owner: o, hostels, managers, activity } = data;
  const occupancy = percentOf(o.residents, o.beds);

  return (
    <>
      <PageHeader title={o.businessName} icon="bi-person-badge" breadcrumb={crumbs}
        subtitle={`Owner account · registered ${date(o.createdAt)}`}
        actions={<>
          <Link to={`/super-admin/owners/${o.id}/edit`} className="btn btn-light"><Icon className="bi bi-pencil me-1" />Edit</Link>
          <button type="button" className="btn btn-light" onClick={() => setResetting(true)}><Icon className="bi bi-key me-1" />Reset password</button>
          {o.status === 'ACTIVE' ? (
            <>
              <button type="button" className="btn btn-outline-warning" onClick={() => setChange({ owner: o, status: 'SUSPENDED' })}><Icon className="bi bi-pause-circle me-1" />Suspend</button>
              <button type="button" className="btn btn-outline-danger" onClick={() => setChange({ owner: o, status: 'INACTIVE' })}><Icon className="bi bi-slash-circle me-1" />Deactivate</button>
            </>
          ) : (
            <button type="button" className="btn btn-primary" onClick={() => setChange({ owner: o, status: 'ACTIVE' })}><Icon className="bi bi-play-circle me-1" />Reactivate</button>
          )}
        </>} />

      <div className="row g-3 mb-3">
        <div className="col-lg-4">
          <section className="hh-card h-100 p-3" aria-label="Owner profile">
            <div className="d-flex justify-content-between align-items-start mb-2">
              <h2 className="h6 mb-0">Profile</h2><StatusBadge map={OWNER_STATUS} value={o.status} />
            </div>
            <dl className="hh-dl mb-0">
              <dt>Owner</dt><dd>{o.contactName || '—'}</dd>
              <dt>Business</dt><dd>{o.businessName}</dd>
              <dt>Email</dt><dd className="text-break">{o.email}</dd>
              <dt>Mobile</dt><dd>{o.phone || '—'}</dd>
              <dt>Registered</dt><dd>{dateTime(o.createdAt)}</dd>
            </dl>
          </section>
        </div>
        <div className="col-lg-8">
          <div className="row g-3">
            <div className="col-6 col-md-4"><SummaryCard label="Hostels" value={number(o.hostels)} icon="bi-houses" tone="orange" /></div>
            <div className="col-6 col-md-4"><SummaryCard label="Managers" value={number(o.managers)} icon="bi-person-gear" tone="purple" /></div>
            <div className="col-6 col-md-4"><SummaryCard label="Rooms" value={number(o.rooms)} icon="bi-door-closed" tone="blue" /></div>
            <div className="col-6 col-md-4"><SummaryCard label="Beds" value={number(o.beds)} icon="bi-box-seam-fill" tone="gray" /></div>
            <div className="col-6 col-md-4"><SummaryCard label="Residents" value={number(o.residents)} icon="bi-people-fill" tone="green" /></div>
            <div className="col-6 col-md-4"><SummaryCard label="Occupancy" value={`${occupancy}%`} icon="bi-pie-chart-fill" tone="yellow" /></div>
          </div>
        </div>
      </div>

      <SubscriptionCard ownerId={o.id} />

      <section className="hh-card mb-3" aria-labelledby="owner-hostels">
        <div className="hh-card-header"><h2 className="hh-card-title" id="owner-hostels"><Icon className="bi bi-houses me-2 text-primary-hh" />Hostels ({hostels.length})</h2></div>
        {hostels.length === 0 ? <EmptyState icon="bi-houses" title="No hostels yet" message="The owner has not created any hostels." /> : (
          <div className="table-responsive"><table className="table align-middle mb-0">
            <thead><tr><th>Hostel</th><th>City</th><th className="text-center">Rooms</th><th className="text-center">Beds</th><th className="text-center">Residents</th><th className="text-center">Managers</th><th>Status</th></tr></thead>
            <tbody>{hostels.map((h) => (
              <tr key={h.id}><td className="fw-medium">{h.name}</td><td>{h.city}, {h.state}</td><td className="text-center">{h.rooms}</td>
                <td className="text-center">{h.beds}</td><td className="text-center">{h.residents}</td><td className="text-center">{h.managers}</td>
                <td><StatusBadge map={HOSTEL_STATUS} value={h.status} /></td></tr>
            ))}</tbody>
          </table></div>
        )}
      </section>

      <div className="row g-3">
        <div className="col-xl-7">
          <section className="hh-card h-100" aria-labelledby="owner-managers">
            <div className="hh-card-header"><h2 className="hh-card-title" id="owner-managers"><Icon className="bi bi-person-gear me-2 text-primary-hh" />Managers ({managers.length})</h2></div>
            {managers.length === 0 ? <EmptyState icon="bi-person-gear" title="No managers yet" message="The owner has not created any managers." /> : (
              <div className="table-responsive"><table className="table align-middle mb-0">
                <thead><tr><th>Manager</th><th>Mobile</th><th className="text-center">Hostels</th><th>Status</th></tr></thead>
                <tbody>{managers.map((m) => (
                  <tr key={m.id}><td className="fw-medium">{m.fullName}<div className="small text-muted-hh fw-normal">{m.email}</div></td><td>{m.phone || '—'}</td>
                    <td className="text-center">{m.hostels}</td>
                    <td><StatusBadge map={{ true: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' }, false: { label: 'Inactive', cls: 'secondary', icon: 'bi-slash-circle' } }} value={String(m.enabled)} /></td></tr>
                ))}</tbody>
              </table></div>
            )}
          </section>
        </div>
        <div className="col-xl-5">
          <section className="hh-card h-100" aria-labelledby="owner-activity">
            <div className="hh-card-header"><h2 className="hh-card-title" id="owner-activity"><Icon className="bi bi-clock-history me-2 text-primary-hh" />Recent activity</h2></div>
            {activity.length === 0 ? <EmptyState icon="bi-clock-history" title="No activity yet" /> : (
              <ul className="list-unstyled mb-0 px-3">{activity.map((a) => <ActivityItem key={a.id} a={a} />)}</ul>
            )}
          </section>
        </div>
      </div>

      <OwnerStatusDialog change={change} onClose={() => setChange(null)} onDone={() => { setChange(null); reload(); }} />
      <ResetPasswordModal show={resetting} owner={o} onClose={() => setResetting(false)} />
    </>
  );
}
