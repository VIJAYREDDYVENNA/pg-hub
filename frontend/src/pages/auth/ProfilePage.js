import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { authApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import { LoadingState } from '../../components/common/States';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { dateTime, fromNow, label } from '../../utils/format';
import { PASSWORD_RULE } from './ResetPasswordPage';
import Icon from '../../components/common/Icon';

function ProfileForm({ user }) {
  const { reloadUser } = useAuth();
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting, isDirty } } = useForm({
    defaultValues: { fullName: user.fullName || '', phone: user.phone || '' },
  });
  useEffect(() => { reset({ fullName: user.fullName || '', phone: user.phone || '' }); }, [user, reset]);

  const onSubmit = async (v) => {
    setServerError(null);
    try {
      await authApi.updateProfile({ fullName: v.fullName.trim(), phone: v.phone.trim() });
      await reloadUser();
      notify('Profile updated');
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not update your profile.'));
    }
  };

  return (
    <form className="hh-card mb-3" onSubmit={handleSubmit(onSubmit)} noValidate aria-labelledby="profile-title">
      <h2 className="hh-card-title mb-3" id="profile-title"><Icon className="bi bi-person me-2" />Your profile</h2>
      {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
      <FormField label="Full name" name="fullName" autoComplete="name" register={register} errors={errors}
        rules={{ required: 'Enter your name', maxLength: { value: 150, message: 'Too long' } }} />
      <FormField label="Phone" name="phone" type="tel" inputMode="tel" autoComplete="tel" register={register} errors={errors}
        rules={{ pattern: { value: /^$|^[+0-9 ]{7,20}$/, message: 'Enter a valid phone number' } }} />
      <div className="mb-3">
        <label className="form-label" htmlFor="profile-email">Email</label>
        <input id="profile-email" className="form-control" value={user.email || ''} disabled readOnly />
        <div className="form-text">Your email is your sign-in ID. Ask the owner to change it.</div>
      </div>
      <button type="submit" className="btn btn-primary" disabled={isSubmitting || !isDirty}>
        {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Save profile
      </button>
    </form>
  );
}

function ChangePasswordForm() {
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, watch, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  });
  const onSubmit = async (v) => {
    setServerError(null);
    try {
      const r = await authApi.changePassword(v.currentPassword, v.newPassword);
      reset();
      notify(r?.message || 'Password changed. Other sessions have been signed out.');
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not change your password.'));
    }
  };
  return (
    <form className="hh-card" onSubmit={handleSubmit(onSubmit)} noValidate aria-labelledby="pw-title">
      <h2 className="hh-card-title mb-3" id="pw-title"><Icon className="bi bi-key me-2" />Change password</h2>
      {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
      <FormField label="Current password" name="currentPassword" type="password" autoComplete="current-password" register={register} errors={errors}
        rules={{ required: 'Enter your current password' }} />
      <FormField label="New password" name="newPassword" type="password" autoComplete="new-password" register={register} errors={errors}
        rules={{ ...PASSWORD_RULE, validate: (val) => val !== watch('currentPassword') || 'Choose a password different from the current one' }}
        help="At least 8 characters with a letter and a number." />
      <FormField label="Confirm new password" name="confirm" type="password" autoComplete="new-password" register={register} errors={errors}
        rules={{ required: 'Confirm the new password', validate: (val) => val === watch('newPassword') || 'Passwords do not match' }} />
      <div className="small text-muted-hh mb-3"><Icon className="bi bi-info-circle me-1" />Changing your password signs you out on all other devices.</div>
      <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
        {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}Change password
      </button>
    </form>
  );
}

export default function ProfilePage() {
  const { user, isOwner, isSuperAdmin } = useAuth();
  // The super-admin shell has no hostel provider: a platform administrator has no hostels.
  const { hostels } = useHostels() || { hostels: [] };
  useEffect(() => { document.title = 'My profile · HostelHub'; }, []);
  const perms = useMemo(() => [...(user?.permissions || [])].sort(), [user]);
  if (!user) return <LoadingState />;
  const myHostels = isSuperAdmin ? 'Not applicable (platform administrator)' : isOwner ? 'All hostels' : hostels.filter((h) => (user.hostelIds || []).includes(h.id)).map((h) => h.name).join(', ') || '—';

  return (
    <>
      <PageHeader title="My profile" icon="bi-person-circle" subtitle="Your account details and password."
        breadcrumb={isSuperAdmin ? [{ label: 'Platform', to: '/super-admin/dashboard' }, { label: 'My profile' }] : undefined} />
      <div className="row g-3">
        <div className="col-lg-6">
          <ProfileForm user={user} />
          <ChangePasswordForm />
        </div>
        <div className="col-lg-6">
          <section className="hh-card" aria-labelledby="account-title">
            <h2 className="hh-card-title mb-3" id="account-title"><Icon className="bi bi-shield-check me-2" />Account</h2>
            <dl className="hh-kv mb-3">
              <dt>Email</dt><dd className="text-break">{user.email}</dd>
              <dt>Organisation</dt><dd>{user.ownerName || '—'}</dd>
              <dt>Role</dt>
              <dd>{(user.roles || []).map((r) => (
                <span key={r} className={`badge hh-badge ${r === 'OWNER' ? 'hh-badge-warning' : 'hh-badge-info'} me-1`}>
                  <Icon className={`bi ${r === 'OWNER' ? 'bi-star-fill' : 'bi-person-badge'} me-1`} aria-hidden="true" />{label(r)}
                </span>
              ))}</dd>
              <dt>Hostels</dt><dd>{myHostels}</dd>
              <dt>Last sign-in</dt><dd>{user.lastLoginAt ? <span title={dateTime(user.lastLoginAt)}>{dateTime(user.lastLoginAt)} ({fromNow(user.lastLoginAt)})</span> : '—'}</dd>
            </dl>
            <h3 className="h6 fw-bold">Permissions</h3>
            {isOwner && <p className="small text-muted-hh mb-2">As the owner you have every permission.</p>}
            {perms.length === 0 ? <div className="small text-muted-hh">No permissions assigned.</div> : (
              <ul className="list-unstyled d-flex flex-wrap gap-1 mb-2">
                {perms.map((p) => <li key={p}><span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-check2 me-1" aria-hidden="true" />{label(p)}</span></li>)}
              </ul>
            )}
            <div className="small text-muted-hh">Permissions are enforced by the server. Ask the owner or a manager if you need more access.</div>
          </section>
        </div>
      </div>
    </>
  );
}
