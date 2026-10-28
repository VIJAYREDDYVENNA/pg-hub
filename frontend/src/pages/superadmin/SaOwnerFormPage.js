import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { ErrorState, LoadingState } from '../../components/common/States';
import { PASSWORD_RULE } from '../auth/ResetPasswordPage';
import Icon from '../../components/common/Icon';
import { saCrumbs } from './saCommon';
import SubscriptionFields from '../../components/subscription/SubscriptionFields';
import { buildSubscriptionInput, validateSubscriptionForm } from '../../utils/subscriptionLogic.mjs';
import { today as todayIso } from '../../utils/format';

const STATUS_OPTIONS = [{ value: 'ACTIVE', label: 'Active' }, { value: 'SUSPENDED', label: 'Suspended' }, { value: 'INACTIVE', label: 'Inactive' }];
const emptyForm = {
  businessName: '', fullName: '', email: '', phone: '', password: '', status: 'ACTIVE',
  sub_planId: '', sub_startsOn: todayIso(), sub_expiresOn: '', sub_trialDays: '', sub_graceDays: '',
  sub_maxHostels: '', sub_maxBedsPerHostel: '', sub_maxManagers: '', sub_customMonthlyPrice: '',
};
const SUB_KEYS = ['planId', 'startsOn', 'expiresOn', 'trialDays', 'graceDays', 'maxHostels', 'maxBedsPerHostel', 'maxManagers', 'customMonthlyPrice'];

/** Create a new owner account, or edit an existing one (route param ownerId). */
export default function SaOwnerFormPage() {
  const { ownerId } = useParams();
  const editing = !!ownerId;
  const navigate = useNavigate();
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const [showPwd, setShowPwd] = useState(false);
  const detail = useApi(() => adminApi.owner(ownerId), [ownerId], { enabled: editing });
  const plansQ = useApi(() => adminApi.plans(), [], { enabled: !editing });
  const featuresQ = useApi(() => adminApi.planFeatures(), [], { enabled: !editing });
  const form = useForm({ defaultValues: emptyForm });
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = form;

  useEffect(() => {
    const o = detail.data?.owner;
    if (o) reset({ businessName: o.businessName, fullName: o.contactName || '', email: o.email, phone: o.phone || '', password: '', status: o.status });
  }, [detail.data, reset]);

  const onSubmit = async (v) => {
    setServerError(null);
    try {
      const base = { businessName: v.businessName.trim(), fullName: v.fullName.trim(), email: v.email.trim(), phone: v.phone.trim() };
      let saved;
      if (editing) {
        saved = await adminApi.updateOwner(ownerId, base);
      } else {
        const subForm = Object.fromEntries(SUB_KEYS.map((k) => [k, v[`sub_${k}`]]));
        const plan = plans.find((p) => p.id === Number(subForm.planId));
        const subErrors = validateSubscriptionForm({ plan, form: subForm });
        const names = Object.keys(subErrors);
        if (names.length > 0) {
          names.forEach((k) => setError(`sub_${k}`, { type: 'validate', message: subErrors[k] }));
          return;
        }
        saved = await adminApi.createOwner({ ...base, password: v.password, status: v.status, subscription: buildSubscriptionInput({ plan, form: subForm }) });
      }
      notify(editing ? 'Owner updated' : `Owner account created for ${saved.businessName}`);
      navigate(`/super-admin/owners/${saved.id}`, { replace: true });
    } catch (e) {
      // Server field errors for the nested subscription object map onto the flat sub_* form fields
      const mapped = (name, err) => setError(name.startsWith('subscription.') ? `sub_${name.slice('subscription.'.length)}` : name, err);
      if (!applyServerErrors(e, mapped)) setServerError(errorMessage(e, 'Could not save the owner.'));
    }
  };

  const plans = plansQ.data || [];

  const crumbs = saCrumbs({ label: 'Owners', to: '/super-admin/owners' }, { label: editing ? 'Edit owner' : 'Create owner' });
  if (editing && detail.error) return <ErrorState error={detail.error} onRetry={detail.reload} />;
  if (editing && !detail.data) return <LoadingState />;

  return (
    <>
      <PageHeader title={editing ? 'Edit owner' : 'Create owner'} icon={editing ? 'bi-pencil-square' : 'bi-person-plus'}
        subtitle={editing ? detail.data.owner.businessName : 'Create a hostel owner account and its first sign-in'} breadcrumb={crumbs} />
      <form className="hh-card p-3 p-md-4" style={{ maxWidth: 980 }} onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        <div className="row g-0 gx-md-3">
          <FormField className="col-md-6" label="Business / organisation name" name="businessName" register={register} errors={errors}
            rules={{ required: 'Enter the business name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Owner full name" name="fullName" register={register} errors={errors}
            rules={{ required: 'Enter the owner name', maxLength: { value: 150, message: 'Too long' } }} />
          <FormField className="col-md-6" label="Email address (sign-in ID)" name="email" type="email" autoComplete="off" register={register} errors={errors}
            rules={{ required: 'Enter the email', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' } }} />
          <FormField className="col-md-6" label="Mobile number" name="phone" type="tel" inputMode="tel" register={register} errors={errors}
            rules={{ required: 'Enter the mobile number', pattern: { value: /^[+0-9 ]{7,20}$/, message: 'Enter a valid mobile number' } }} />
          {!editing && (
            <>
              <div className="col-md-6 mb-3">
                <label htmlFor="f-password" className="form-label">Initial password<span className="text-danger ms-1" aria-hidden="true">*</span></label>
                <div className="position-relative">
                  <input id="f-password" type={showPwd ? 'text' : 'password'} autoComplete="new-password"
                    className={`form-control pe-5 ${errors.password ? 'is-invalid' : ''}`} {...register('password', { ...PASSWORD_RULE, required: 'Enter an initial password' })} />
                  <button type="button" className="btn btn-link position-absolute end-0 top-50 translate-middle-y text-muted-hh" style={{ zIndex: 5 }}
                    onClick={() => setShowPwd((s) => !s)} aria-label={showPwd ? 'Hide password' : 'Show password'}>
                    <Icon className={`bi ${showPwd ? 'bi-eye-slash' : 'bi-eye'}`} />
                  </button>
                </div>
                {errors.password ? <div className="invalid-feedback d-block">{errors.password.message}</div>
                  : <div className="form-text">At least 8 characters with a letter and a number. Share it securely; the owner can change it after signing in.</div>}
              </div>
              <FormField className="col-md-6" label="Account status" name="status" as="select" register={register} errors={errors}
                options={STATUS_OPTIONS} rules={{ required: true }} />
            </>
          )}
        </div>
        {!editing && (plansQ.loading && !plansQ.data
          ? <p className="text-muted-hh">Loading subscription plans…</p>
          : plansQ.error
            ? <div className="alert alert-danger py-2" role="alert">Could not load subscription plans. <button type="button" className="btn btn-link p-0 align-baseline" onClick={plansQ.reload}>Retry</button></div>
            : <SubscriptionFields plans={plans} features={featuresQ.data || []} form={form} today={todayIso()} />)}
        {editing && <p className="small text-muted-hh">Status changes and the subscription are managed from the owner details page, where they are confirmed and logged.</p>}
        <div className="d-flex gap-2 justify-content-end mt-2">
          <Link to={editing ? `/super-admin/owners/${ownerId}` : '/super-admin/owners'} className="btn btn-light">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save changes' : 'Create owner'}
          </button>
        </div>
      </form>
    </>
  );
}
