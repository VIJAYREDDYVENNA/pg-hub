import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import Modal from '../../components/common/Modal';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import FormField, { applyServerErrors } from '../../components/common/FormField';
import { ErrorState, LoadingState } from '../../components/common/States';
import Icon from '../../components/common/Icon';
import { money } from '../../utils/format';
import { saCrumbs } from './saCommon';

const PLAN_STATUS = {
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' },
  INACTIVE: { label: 'Inactive', cls: 'warning', icon: 'bi-pause-circle-fill' },
  ARCHIVED: { label: 'Archived', cls: 'secondary', icon: 'bi-archive' },
};

const blankPlan = {
  code: '', name: '', description: '', monthlyPrice: '', annualPrice: '', maxHostels: '1', maxBedsPerHostel: '50', maxManagers: '1',
  trialDays: '14', graceDays: '7', customPricing: false, sortOrder: '100',
};

function PlanModal({ plan, features, onClose, onSaved }) {
  const { notify } = useToast();
  const editing = !!plan;
  const [serverError, setServerError] = useState(null);
  const [entitlements, setEntitlements] = useState({});
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = useForm({ defaultValues: blankPlan });

  useEffect(() => {
    reset(plan ? {
      code: plan.code, name: plan.name, description: plan.description || '', monthlyPrice: plan.monthlyPrice ?? '', annualPrice: plan.annualPrice ?? '',
      maxHostels: String(plan.maxHostels), maxBedsPerHostel: String(plan.maxBedsPerHostel), maxManagers: String(plan.maxManagers),
      trialDays: String(plan.trialDays), graceDays: String(plan.graceDays), customPricing: plan.customPricing, sortOrder: String(plan.sortOrder),
    } : blankPlan);
    setEntitlements(plan ? { ...plan.entitlements } : Object.fromEntries(features.map((f) => [f.code, false])));
  }, [plan, features, reset]);

  const submit = async (v) => {
    setServerError(null);
    const body = {
      code: editing ? undefined : v.code.trim(), name: v.name.trim(), description: v.description.trim() || null,
      monthlyPrice: v.monthlyPrice === '' ? null : Number(v.monthlyPrice), annualPrice: v.annualPrice === '' ? null : Number(v.annualPrice),
      currency: 'INR', maxHostels: Number(v.maxHostels), maxBedsPerHostel: Number(v.maxBedsPerHostel), maxManagers: Number(v.maxManagers),
      trialDays: Number(v.trialDays), graceDays: Number(v.graceDays), customPricing: !!v.customPricing, sortOrder: Number(v.sortOrder), entitlements,
    };
    try {
      if (editing) await adminApi.updatePlan(plan.id, body); else await adminApi.createPlan(body);
      notify(editing ? 'Plan updated' : 'Plan created');
      onSaved();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not save the plan.'));
    }
  };

  const int = (min, max, what) => ({ required: `Enter ${what}`, min: { value: min, message: `Minimum ${min}` }, max: { value: max, message: `Maximum ${max}` },
    validate: (x) => Number.isInteger(Number(x)) || 'Enter a whole number' });

  return (
    <Modal show title={editing ? `Edit plan · ${plan.name}` : 'New plan'} size="lg" onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={isSubmitting}>Cancel</button>
        <button type="submit" form="plan-form" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}{editing ? 'Save plan' : 'Create plan'}
        </button>
      </>}>
      <form id="plan-form" onSubmit={handleSubmit(submit)} noValidate>
        {serverError && <div className="alert alert-danger py-2" role="alert">{serverError}</div>}
        {editing && plan.subscribers > 0 && (
          <div className="alert alert-info py-2 small">{plan.subscribers} owner(s) are on this plan. New limits apply to them immediately (except owners with custom limits). Nobody's data is removed.</div>
        )}
        <div className="row g-0 gx-3">
          <FormField className="col-md-4" label="Code" name="code" register={register} errors={errors} disabled={editing}
            rules={editing ? {} : { required: 'Enter a code', pattern: { value: /^[A-Z][A-Z0-9_]{1,29}$/, message: 'Capital letters, digits or _ (2–30 characters)' } }}
            help={editing ? 'The code cannot change' : 'e.g. PRO'} />
          <FormField className="col-md-5" label="Name" name="name" register={register} errors={errors} rules={{ required: 'Enter a name', maxLength: { value: 80, message: 'Too long' } }} />
          <FormField className="col-md-3" label="Display order" name="sortOrder" type="number" register={register} errors={errors} rules={int(0, 10000, 'an order')} />
          <FormField className="col-12" label="Description" name="description" register={register} errors={errors} rules={{ maxLength: { value: 500, message: 'Too long' } }} />
          <FormField className="col-md-4" label="Monthly price (₹)" name="monthlyPrice" type="number" step="0.01" min="0" register={register} errors={errors}
            rules={{ min: { value: 0, message: 'Cannot be negative' } }} help="Leave empty only for custom-priced plans" />
          <FormField className="col-md-4" label="Annual price (₹)" name="annualPrice" type="number" step="0.01" min="0" register={register} errors={errors}
            rules={{ min: { value: 0, message: 'Cannot be negative' } }} help="Optional" />
          <div className="col-md-4 mb-3 d-flex align-items-center">
            <div className="form-check mt-3">
              <input id="f-customPricing" type="checkbox" className="form-check-input" {...register('customPricing')} />
              <label htmlFor="f-customPricing" className="form-check-label">Custom pricing (contact sales)</label>
            </div>
          </div>
          <FormField className="col-md-4" label="Hostels per owner" name="maxHostels" type="number" register={register} errors={errors} rules={int(1, 1000, 'the hostel limit')} />
          <FormField className="col-md-4" label="Beds per hostel" name="maxBedsPerHostel" type="number" register={register} errors={errors} rules={int(1, 10000, 'the bed limit')} />
          <FormField className="col-md-4" label="Manager accounts" name="maxManagers" type="number" register={register} errors={errors} rules={int(0, 1000, 'the manager limit')} />
          <FormField className="col-md-6" label="Default trial (days)" name="trialDays" type="number" register={register} errors={errors} rules={int(0, 365, 'the trial length')} />
          <FormField className="col-md-6" label="Grace period (days)" name="graceDays" type="number" register={register} errors={errors} rules={int(0, 90, 'the grace period')} />
        </div>
        <h3 className="h6 mt-2">Features included</h3>
        <div className="row">
          {features.map((f) => (
            <div className="col-md-6" key={f.code}>
              <div className="form-check mb-2">
                <input id={`ent-${f.code}`} type="checkbox" className="form-check-input" checked={!!entitlements[f.code]}
                  onChange={(e) => setEntitlements((cur) => ({ ...cur, [f.code]: e.target.checked }))} />
                <label htmlFor={`ent-${f.code}`} className="form-check-label">{f.label}<span className="d-block small text-muted-hh">{f.description}</span></label>
              </div>
            </div>
          ))}
        </div>
      </form>
    </Modal>
  );
}

/** Super Admin: the subscription plan catalogue (limits, prices, trial/grace and features), all stored in the database. */
export default function SaPlansPage() {
  const { notify } = useToast();
  const plansQ = useApi(() => adminApi.plans(), []);
  const featuresQ = useApi(() => adminApi.planFeatures(), []);
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = new, object = edit
  const [statusChange, setStatusChange] = useState(null);

  useEffect(() => { document.title = 'Subscription plans · HostelHub'; }, []);
  const crumbs = saCrumbs({ label: 'Subscription plans' });
  if (plansQ.error) return <><PageHeader title="Subscription plans" icon="bi-tags-fill" breadcrumb={crumbs} /><ErrorState error={plansQ.error} onRetry={plansQ.reload} /></>;
  if (!plansQ.data || !featuresQ.data) return <LoadingState />;

  const changeStatus = async () => {
    try {
      await adminApi.setPlanStatus(statusChange.plan.id, statusChange.status);
      notify(`Plan ${statusChange.plan.name} is now ${statusChange.status.toLowerCase()}`);
      setStatusChange(null);
      plansQ.reload();
    } catch (e) {
      notify(errorMessage(e), 'danger');
      setStatusChange(null);
    }
  };

  return (
    <>
      <PageHeader title="Subscription plans" icon="bi-tags-fill" breadcrumb={crumbs}
        subtitle="Prices, limits, trial and grace periods and features. Changes apply without a code change."
        actions={<button type="button" className="btn btn-primary" onClick={() => setEditing(null)}><Icon className="bi bi-plus-lg me-1" />New plan</button>} />
      <section className="hh-card">
        <div className="table-responsive">
          <table className="table align-middle mb-0">
            <thead><tr><th>Plan</th><th className="text-end">Price / month</th><th className="text-center">Hostels</th><th className="text-center">Beds / hostel</th>
              <th className="text-center">Managers</th><th className="text-center">Trial / grace</th><th className="text-center">Owners</th><th>Status</th><th /></tr></thead>
            <tbody>
              {plansQ.data.map((p) => (
                <tr key={p.id}>
                  <td className="fw-medium">{p.name}<div className="small text-muted-hh fw-normal">{p.code}</div></td>
                  <td className="text-end">{p.monthlyPrice == null ? 'Custom' : money(p.monthlyPrice)}</td>
                  <td className="text-center">{p.maxHostels}</td><td className="text-center">{p.maxBedsPerHostel}</td><td className="text-center">{p.maxManagers}</td>
                  <td className="text-center">{p.trialDays} / {p.graceDays} d</td>
                  <td className="text-center">{p.subscribers}</td>
                  <td><StatusBadge map={PLAN_STATUS} value={p.status} /></td>
                  <td className="text-end text-nowrap">
                    {p.status !== 'ARCHIVED' && <button type="button" className="btn btn-light btn-sm me-1" onClick={() => setEditing(p)}><Icon className="bi bi-pencil me-1" />Edit</button>}
                    {p.status === 'ACTIVE' && <button type="button" className="btn btn-outline-warning btn-sm me-1" onClick={() => setStatusChange({ plan: p, status: 'INACTIVE' })}>Deactivate</button>}
                    {p.status === 'INACTIVE' && <button type="button" className="btn btn-outline-success btn-sm me-1" onClick={() => setStatusChange({ plan: p, status: 'ACTIVE' })}>Activate</button>}
                    {p.status !== 'ARCHIVED' && <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setStatusChange({ plan: p, status: 'ARCHIVED' })}>Archive</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <p className="small text-muted-hh mt-2">
        Deactivated and archived plans cannot be assigned to new owners but remain on the owners who already have them. Lowering a limit never removes an owner's existing hostels, beds or managers.
      </p>
      {editing !== undefined && <PlanModal plan={editing} features={featuresQ.data} onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); plansQ.reload(); }} />}
      <ConfirmDialog show={!!statusChange} variant={statusChange?.status === 'ARCHIVED' ? 'danger' : 'primary'}
        title={statusChange ? `${statusChange.status === 'ACTIVE' ? 'Activate' : statusChange.status === 'INACTIVE' ? 'Deactivate' : 'Archive'} ${statusChange.plan.name}?` : ''}
        confirmLabel={statusChange?.status === 'ACTIVE' ? 'Activate' : statusChange?.status === 'INACTIVE' ? 'Deactivate' : 'Archive plan'}
        message={statusChange?.status === 'ARCHIVED'
          ? 'An archived plan can no longer be edited or assigned and cannot be reactivated. Owners already on it keep it.'
          : statusChange?.status === 'INACTIVE' ? 'It will no longer be offered when creating or changing owners. Owners already on it are not affected.' : 'It will be available for new assignments again.'}
        onConfirm={changeStatus} onClose={() => setStatusChange(null)} />
    </>
  );
}
