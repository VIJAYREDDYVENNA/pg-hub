import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import Modal from '../common/Modal';
import FormField, { applyServerErrors } from '../common/FormField';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { PASSWORD_RULE } from '../../pages/auth/ResetPasswordPage';
import Icon from '../common/Icon';

export const ASSIGNABLE_ROLES = ['MANAGER', 'WARDEN', 'STAFF'];

/** Create or edit a staff login: role, fine-grained permissions and hostel access. */
export default function UserFormModal({ show, user, roles, permissions, onClose, onSaved }) {
  const { can, isOwner } = useAuth();
  const { hostels } = useHostels();
  const { notify } = useToast();
  const editing = !!user?.id;
  const [perms, setPerms] = useState(new Set());
  const [hostelIds, setHostelIds] = useState(new Set());
  const [enabled, setEnabled] = useState(true);
  const [serverError, setServerError] = useState(null);
  const [localError, setLocalError] = useState(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm();

  const assignable = roles.filter((r) => ASSIGNABLE_ROLES.includes(r.code));
  const roleByCode = useMemo(() => Object.fromEntries(roles.map((r) => [r.code, r])), [roles]);
  const grouped = useMemo(() => permissions.reduce((acc, p) => {
    (acc[p.category || 'Other'] = acc[p.category || 'Other'] || []).push(p);
    return acc;
  }, {}), [permissions]);
  // A non-owner can only grant what they have themselves; SETTINGS_MANAGE is owner-only.
  const grantable = (code) => (code === 'SETTINGS_MANAGE' ? isOwner : can(code));

  useEffect(() => {
    if (!show) return;
    setServerError(null);
    setLocalError(null);
    const roleCode = user?.roles?.find((r) => ASSIGNABLE_ROLES.includes(r)) || 'STAFF';
    reset({ fullName: user?.fullName || '', email: user?.email || '', phone: user?.phone || '', password: '', roleCode });
    setPerms(new Set(editing ? user.permissions || [] : (roleByCode[roleCode]?.defaultPermissions || []).filter(grantable)));
    setHostelIds(new Set(editing ? user.hostelIds || [] : hostels.length === 1 ? [hostels[0].id] : []));
    setEnabled(editing ? !!user.enabled : true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show, user]);

  const onRoleChange = (e) => {
    const r = roleByCode[e.target.value];
    if (r) setPerms(new Set((r.defaultPermissions || []).filter(grantable)));
  };
  const toggle = (setter, value) => setter((cur) => {
    const n = new Set(cur);
    if (n.has(value)) n.delete(value); else n.add(value);
    return n;
  });

  const onSubmit = async (v) => {
    setServerError(null);
    setLocalError(null);
    if (hostelIds.size === 0) { setLocalError('Give the user access to at least one hostel.'); return; }
    const common = {
      fullName: v.fullName.trim(), phone: v.phone.trim(), roleCode: v.roleCode,
      permissions: [...perms], hostelIds: [...hostelIds],
    };
    try {
      const saved = editing
        ? await userApi.update(user.id, { ...common, enabled })
        : await userApi.create({ ...common, email: v.email.trim(), password: v.password });
      notify(editing ? 'User updated' : `Login created for ${saved.email}`);
      onSaved?.(saved);
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the user.'));
    }
  };

  return (
    <Modal show={show} title={editing ? `Edit login · ${user.fullName}` : 'New staff login'} onClose={onClose} size="lg"
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="user-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Create login'}
        </button>
      </>}>
      <form id="user-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-6" label="Full name" name="fullName" register={register} errors={errors}
            rules={{ required: 'Enter the name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Email (sign-in ID)" name="email" type="email" autoComplete="off" register={register} errors={errors}
            disabled={editing} rules={editing ? {} : { required: 'Enter the email', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }} />
          <FormField className="col-md-6" label="Phone" name="phone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ pattern: { value: /^$|^[+0-9 ]{7,20}$/, message: 'Enter a valid phone number' } }} />
          {!editing && (
            <FormField className="col-md-6" label="Initial password" name="password" type="password" autoComplete="new-password"
              register={register} errors={errors} rules={{ ...PASSWORD_RULE, required: 'Enter an initial password' }}
              help="At least 8 characters with a letter and a number. Share it securely." />
          )}
          <FormField className="col-md-6" label="Role" name="roleCode" as="select" register={register} errors={errors}
            rules={{ required: 'Choose a role', onChange: onRoleChange }}
            options={assignable.map((r) => ({ value: r.code, label: r.name || r.code }))}
            help="Choosing a role pre-fills its default permissions; you can adjust them below." />
          {editing && (
            <div className="col-md-6 mb-3 d-flex align-items-center">
              <div className="form-check form-switch">
                <input className="form-check-input" type="checkbox" role="switch" id="user-enabled" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
                <label className="form-check-label" htmlFor="user-enabled">{enabled ? 'Login enabled' : 'Login disabled (signed out everywhere)'}</label>
              </div>
            </div>
          )}
        </div>

        <fieldset className="mb-3">
          <legend className="form-label fs-6">Hostel access<span className="text-danger ms-1">*</span></legend>
          <div className="d-flex flex-wrap gap-2">
            {hostels.map((h) => (
              <label key={h.id} className={`btn btn-sm ${hostelIds.has(h.id) ? 'btn-soft' : 'btn-light'} d-inline-flex align-items-center gap-2`}>
                <input type="checkbox" className="form-check-input m-0" checked={hostelIds.has(h.id)} onChange={() => toggle(setHostelIds, h.id)} />{h.name}
              </label>
            ))}
          </div>
          {localError && <div className="invalid-feedback d-block">{localError}</div>}
        </fieldset>

        <fieldset>
          <legend className="form-label fs-6 d-flex justify-content-between align-items-center">
            <span>Permissions <span className="text-muted-hh small">({perms.size} selected)</span></span>
          </legend>
          <div className="row g-2">
            {Object.entries(grouped).map(([cat, list]) => (
              <div className="col-md-6" key={cat}>
                <div className="border rounded-3 p-2 h-100">
                  <div className="fw-semibold small text-uppercase text-muted-hh mb-1">{cat}</div>
                  {list.map((p) => {
                    const ok = grantable(p.code);
                    return (
                      <div className="form-check" key={p.code}>
                        <input id={`perm-${p.code}`} type="checkbox" className="form-check-input" checked={perms.has(p.code)}
                          disabled={!ok} onChange={() => toggle(setPerms, p.code)} />
                        <label className="form-check-label small" htmlFor={`perm-${p.code}`}>
                          {p.name}{!ok && <span className="text-muted-hh"> · {p.code === 'SETTINGS_MANAGE' ? 'owner only' : 'you lack this'}</span>}
                        </label>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
          <div className="form-text mt-2"><Icon className="bi bi-shield-lock me-1" />Permissions are enforced by the server on every request; hiding a menu is only a convenience.</div>
        </fieldset>
      </form>
    </Modal>
  );
}
