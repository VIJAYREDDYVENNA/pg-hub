import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useSearchParams } from 'react-router-dom';
import useApi from '../hooks/useApi';
import { staffApi, userApi } from '../services/endpoints';
import { errorMessage } from '../services/apiClient';
import { useAuth } from '../context/AuthContext';
import { useHostels } from '../context/HostelContext';
import { useToast } from '../context/ToastContext';
import PageHeader from '../components/common/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import DataTable, { RowAction, RowActions } from '../components/common/DataTable';
import Pagination from '../components/common/Pagination';
import HostelFilter from '../components/common/HostelFilter';
import StatusBadge from '../components/common/StatusBadge';
import Modal from '../components/common/Modal';
import FormField, { applyServerErrors } from '../components/common/FormField';
import Tabs from '../components/operations/Tabs';
import UserFormModal from '../components/operations/UserFormModal';
import { STAFF_STATUS, numOrNull, orNull } from '../components/operations/constants';
import ResetPasswordModal from '../components/managers/ResetPasswordModal';
import { date, dateTime, fromNow, label, money, telLink, whatsappLink } from '../utils/format';
import Icon from '../components/common/Icon';

function StaffFormModal({ show, staff, users, onClose, onSaved }) {
  const { isOwner } = useAuth();
  const { hostels, selectedHostelId } = useHostels();
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm();
  const editing = !!staff?.id;

  useEffect(() => {
    if (!show) return;
    setServerError(null);
    const s = staff || {};
    reset({
      hostelId: s.hostelId ?? (editing ? '' : selectedHostelId || (isOwner ? '' : hostels[0]?.id || '')),
      fullName: s.fullName || '', mobile: s.mobile || '', designation: s.designation || '',
      monthlySalary: s.monthlySalary ?? '', joinedOn: s.joinedOn || '', status: s.status || 'ACTIVE', userId: s.userId ?? '',
    });
  }, [show, staff, editing, selectedHostelId, isOwner, hostels, reset]);

  const onSubmit = async (v) => {
    setServerError(null);
    const body = {
      hostelId: numOrNull(v.hostelId), userId: numOrNull(v.userId), fullName: v.fullName.trim(), mobile: v.mobile.trim(),
      designation: v.designation.trim(), monthlySalary: numOrNull(v.monthlySalary), joinedOn: orNull(v.joinedOn), status: v.status,
    };
    try {
      const saved = editing ? await staffApi.update(staff.id, body) : await staffApi.create(body);
      notify(editing ? 'Staff member updated' : 'Staff member added');
      onSaved?.(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the staff member.'));
    }
  };

  return (
    <Modal show={show} title={editing ? `Edit · ${staff.fullName}` : 'Add staff member'} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="staff-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Add staff'}
        </button>
      </>}>
      <form id="staff-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <FormField label="Hostel" name="hostelId" as="select" register={register} errors={errors}
          rules={isOwner ? {} : { required: 'Select the hostel this staff member works at' }}
          options={[...(isOwner ? [{ value: '', label: 'All hostels (head office)' }] : [{ value: '', label: 'Select hostel…' }]),
            ...hostels.map((h) => ({ value: h.id, label: h.name }))]} />
        <FormField label="Full name" name="fullName" register={register} errors={errors}
          rules={{ required: 'Enter the name', maxLength: { value: 150, message: 'Too long' } }} />
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-6" label="Mobile" name="mobile" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: /^[+]?[0-9 ]{10,15}$/, message: 'Enter a valid mobile number' } }} />
          <FormField className="col-md-6" label="Designation" name="designation" register={register} errors={errors} placeholder="e.g. Warden, Cook, Electrician"
            rules={{ required: 'Enter the designation', maxLength: { value: 60, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Monthly salary (₹)" name="monthlySalary" type="number" min="0" step="0.01" inputMode="decimal"
            register={register} errors={errors} rules={{ min: { value: 0, message: 'Cannot be negative' } }} />
          <FormField className="col-md-6" label="Joined on" name="joinedOn" type="date" register={register} errors={errors} />
          <FormField className="col-md-6" label="Status" name="status" as="select" register={register} errors={errors}
            options={Object.entries(STAFF_STATUS).map(([value, m]) => ({ value, label: m.label }))} />
          <FormField className="col-md-6" label="Linked login (optional)" name="userId" as="select" register={register} errors={errors}
            options={[{ value: '', label: 'No login' }, ...users.filter((u) => !u.roles?.includes('OWNER')).map((u) => ({ value: u.id, label: `${u.fullName} · ${u.email}` }))]}
            help="Link if this person also signs in to HostelHub." />
        </div>
      </form>
    </Modal>
  );
}

function StaffTab({ users }) {
  const { selectedHostelId, hostels } = useHostels();
  const [hostelId, setHostelId] = useState(selectedHostelId);
  const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState(null);
  const { data, loading, error, reload } = useApi(() => staffApi.list(hostelId || undefined), [hostelId]);
  useEffect(() => { setHostelId(selectedHostelId); }, [selectedHostelId]);
  const hostelName = (id) => (id ? hostels.find((h) => h.id === id)?.name || `#${id}` : 'All hostels');
  const userName = (id) => users.find((u) => u.id === id)?.email;
  const rows = (data || []).filter((s) => showInactive || s.status === 'ACTIVE');

  const columns = [
    { key: 'fullName', label: 'Name', required: true, sortValue: (s) => s.fullName, render: (s) => (
      <div><span className="hh-cell-primary">{s.fullName}</span><span className="hh-cell-sub">{s.designation}</span></div>
    ) },
    { key: 'mobile', label: 'Contact', sortValue: (s) => s.mobile, render: (s) => (s.mobile
      ? <span className="hh-contact-line"><Icon className="bi bi-telephone" />{s.mobile}</span> : <span className="hh-dash">—</span>) },
    { key: 'hostel', label: 'Hostel', sortValue: (s) => hostelName(s.hostelId), render: (s) => hostelName(s.hostelId) },
    { key: 'monthlySalary', label: 'Salary', align: 'end', hideOnMobile: true, sortValue: (s) => Number(s.monthlySalary) || 0,
      render: (s) => (s.monthlySalary != null ? money(s.monthlySalary) : <span className="hh-dash">—</span>) },
    { key: 'joinedOn', label: 'Joined', hideOnMobile: true, sortValue: (s) => s.joinedOn || '', render: (s) => (s.joinedOn ? date(s.joinedOn) : <span className="hh-dash">—</span>) },
    { key: 'login', label: 'Login', hideOnMobile: true, sortValue: (s) => (s.userId ? userName(s.userId) || 'Linked' : ''),
      render: (s) => (s.userId ? <span className="hh-contact-line"><Icon className="bi bi-person-badge" />{userName(s.userId) || 'Linked'}</span> : <span className="hh-dash">—</span>) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (s) => s.status, render: (s) => <StatusBadge map={STAFF_STATUS} value={s.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (s) => (
      <RowActions>
        <RowAction variant="edit" label={`Edit ${s.fullName}`} onClick={() => setEditing(s)} />
        {s.mobile && <RowAction variant="call" label={`Call ${s.mobile}`} href={telLink(s.mobile)} />}
        {s.mobile && <RowAction variant="whatsapp" label="Open chat in WhatsApp" href={whatsappLink(s.mobile)} />}
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={setHostelId} id="staff-hostel" /></div>
      <div className="form-check form-switch ms-1 mb-0">
        <input className="form-check-input" type="checkbox" role="switch" id="staff-inactive" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
        <label className="form-check-label small" htmlFor="staff-inactive">Show inactive</label>
      </div>
      <button type="button" className="btn btn-sm btn-primary" onClick={() => setEditing({})}><Icon className="bi bi-person-plus me-1" />Add staff</button>
    </>
  );

  return (
    <>
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <DataTable tableId="staff" columns={columns} rows={rows} serial onRowClick={(s) => setEditing(s)} toolbar={toolbar}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-person-badge" title="No staff yet" message="Add wardens, cooks, cleaners and technicians to assign maintenance work." />
          )}
          footer={<Pagination page={0} totalPages={1} totalElements={rows.length} noun="staff members" onChange={() => {}} />} />
      )}
      <StaffFormModal show={!!editing} staff={editing?.id ? editing : null} users={users} onClose={() => setEditing(null)}
        onSaved={() => { setEditing(null); reload(); }} />
    </>
  );
}

function UsersTab({ usersApi }) {
  const { user: me } = useAuth();
  const { hostels } = useHostels();
  const { data: users, loading, error, reload } = usersApi;
  const { data: meta, loading: metaLoading, error: metaError, reload: reloadMeta } = useApi(
    () => Promise.all([userApi.roles(), userApi.permissions()]).then(([roles, permissions]) => ({ roles, permissions })), []);
  const [editing, setEditing] = useState(null);
  const [resetting, setResetting] = useState(null);
  const permName = useMemo(() => Object.fromEntries((meta?.permissions || []).map((p) => [p.code, p.name])), [meta]);
  const roleName = (code) => meta?.roles?.find((r) => r.code === code)?.name || label(code);
  const hostelNames = (ids) => (ids || []).map((id) => hostels.find((h) => h.id === id)?.name).filter(Boolean).join(', ');

  const columns = [
    { key: 'fullName', label: 'User', required: true, sortValue: (u) => u.fullName, render: (u) => (
      <div className="min-w-0">
        <span className="hh-cell-primary">{u.fullName}</span>{u.id === me?.id && <span className="badge bg-light text-dark ms-1">You</span>}
        <span className="hh-contact-line text-break"><Icon className="bi bi-envelope" />{u.email}</span>
      </div>
    ) },
    { key: 'roles', label: 'Role', sortValue: (u) => (u.roles || []).join(','), render: (u) => (u.roles || []).map((r) => (
      <span key={r} className={`badge hh-badge ${r === 'OWNER' ? 'hh-badge-warning' : 'hh-badge-info'} me-1`}>
        <Icon className={`bi ${r === 'OWNER' ? 'bi-star-fill' : 'bi-person-badge'} me-1`} aria-hidden="true" />{roleName(r)}
      </span>
    )) },
    { key: 'access', label: 'Access', hideOnMobile: true, sortable: false, render: (u) => (u.roles?.includes('OWNER')
      ? <span>All hostels · full access</span>
      : <span title={(u.permissions || []).map((p) => permName[p] || p).join('\n')}>{hostelNames(u.hostelIds) || 'No hostels'}
        <span className="hh-cell-sub">{(u.permissions || []).length} permissions</span></span>) },
    { key: 'enabled', label: 'Status', align: 'center', sortValue: (u) => (u.enabled ? 1 : 0), render: (u) => (u.enabled
      ? <span className="badge hh-badge hh-badge-success"><Icon className="bi bi-check-circle me-1" />Active</span>
      : <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-slash-circle me-1" />Disabled</span>) },
    { key: 'lastLoginAt', label: 'Last sign-in', hideOnMobile: true, sortValue: (u) => u.lastLoginAt || '',
      render: (u) => (u.lastLoginAt ? <span title={dateTime(u.lastLoginAt)}>{fromNow(u.lastLoginAt)}</span> : 'Never') },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (u) => {
      if (u.roles?.includes('OWNER')) return <span className="small text-muted-hh"><Icon className="bi bi-lock me-1" />Read-only</span>;
      if (u.id === me?.id) return <span className="small text-muted-hh">Use Profile</span>;
      return (
        <RowActions>
          <RowAction variant="edit" label={`Edit ${u.fullName}`} onClick={() => setEditing(u)} disabled={!meta} />
          <RowAction variant="remind" icon="bi-key" label="Reset password" onClick={() => setResetting(u)} />
        </RowActions>
      );
    } },
  ];

  const toolbar = (
    <>
      <span className="small text-muted-hh"><Icon className="bi bi-shield-lock me-1" aria-hidden="true" />Sign-in accounts</span>
      <button type="button" className="btn btn-sm btn-primary" onClick={() => setEditing({})} disabled={!meta}><Icon className="bi bi-person-plus me-1" />New login</button>
    </>
  );

  return (
    <>
      <div className="alert alert-light border d-flex gap-2 small" role="note">
        <Icon className="bi bi-shield-lock fs-5 text-primary-hh" aria-hidden="true" />
        <div>Each login gets a role, a set of permissions and access to specific hostels. Permissions are enforced by the server on every request —
          the app only hides what a user cannot do. You can grant only permissions you have yourself.</div>
      </div>
      {error || metaError ? <ErrorState error={error || metaError} onRetry={() => { reload(); reloadMeta(); }} /> : (
        <DataTable tableId="users" columns={columns} rows={users || []} serial toolbar={toolbar}
          empty={(loading && !users) || (metaLoading && !meta) ? <LoadingState /> : <EmptyState icon="bi-people" title="No users yet" />}
          footer={<Pagination page={0} totalPages={1} totalElements={(users || []).length} noun="logins" onChange={() => {}} />} />
      )}
      {meta && (
        <UserFormModal show={!!editing} user={editing?.id ? editing : null} roles={meta.roles} permissions={meta.permissions}
          onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />
      )}
      <ResetPasswordModal user={resetting} onClose={() => setResetting(null)} />
    </>
  );
}

export default function StaffPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'logins' ? 'logins' : 'staff';
  const usersApi = useApi(() => userApi.list(), []);
  useEffect(() => { document.title = 'Staff & logins · HostelHub'; }, []);

  return (
    <>
      <PageHeader title="Staff & logins" icon="bi-person-badge" subtitle="Manage your team, their sign-in accounts and what each person can do." />
      <Tabs active={tab} onChange={(t) => setParams(t === 'logins' ? { tab: 'logins' } : {}, { replace: true })} tabs={[
        { key: 'staff', label: 'Staff', icon: 'bi-person-badge' },
        { key: 'logins', label: 'Logins & permissions', icon: 'bi-shield-lock', count: usersApi.data?.length },
      ]} />
      {tab === 'staff' ? <StaffTab users={usersApi.data || []} /> : <UsersTab usersApi={usersApi} />}
    </>
  );
}
