import { useRef } from 'react';
import { defaultExpiry, planDefaults } from '../../utils/subscriptionLogic.mjs';
import { money } from '../../utils/format';
import LimitFields from './LimitFields';

/** Entitlement list of a plan, using the feature labels from the catalogue. */
export function EntitlementList({ plan, features }) {
  const labels = Object.fromEntries((features || []).map((f) => [f.code, f.label]));
  const on = Object.entries(plan.entitlements || {}).filter(([, v]) => v).map(([k]) => labels[k] || k);
  return on.length ? <span>{on.join(' · ')}</span> : <span className="text-muted-hh">No optional features</span>;
}

/**
 * The "Subscription" section of the Create Owner form. Choosing a plan fills the trial, grace, limits and price from the
 * plan (all editable); the expiry date follows the start date and trial length until Super Admin sets it by hand.
 * Field names carry the `sub_` prefix so they live beside the owner fields in a single react-hook-form.
 */
export default function SubscriptionFields({ plans, features, form, today }) {
  const { register, watch, setValue, formState: { errors } } = form;
  const expiryEdited = useRef(false);
  const planId = Number(watch('sub_planId'));
  const plan = plans.find((p) => p.id === planId);

  const recomputeExpiry = (startsOn, trialDays) => {
    if (!expiryEdited.current) setValue('sub_expiresOn', defaultExpiry(startsOn, trialDays));
  };

  const applyPlan = (id) => {
    const p = plans.find((x) => x.id === Number(id));
    if (!p) return;
    const d = planDefaults(p);
    ['trialDays', 'graceDays', 'maxHostels', 'maxBedsPerHostel', 'maxManagers', 'customMonthlyPrice']
      .forEach((k) => setValue(`sub_${k}`, d[k], { shouldDirty: true }));
    recomputeExpiry(watch('sub_startsOn') || today, d.trialDays);
  };

  const fieldError = (name) => errors?.[name]?.message;

  return (
    <fieldset className="border-top pt-3 mt-2">
      <legend className="h6 mb-3">Subscription</legend>
      <div className="row g-3">
        <div className="col-md-6">
          <label htmlFor="f-sub_planId" className="form-label">Subscription plan<span className="text-danger ms-1" aria-hidden="true">*</span></label>
          <select id="f-sub_planId" className={`form-select ${fieldError('sub_planId') ? 'is-invalid' : ''}`}
            {...register('sub_planId', { onChange: (e) => applyPlan(e.target.value) })}>
            <option value="">Select a plan…</option>
            {plans.filter((p) => p.status === 'ACTIVE').map((p) => (
              <option key={p.id} value={p.id}>{p.name} — {p.monthlyPrice == null ? 'custom price' : `${money(p.monthlyPrice)}/month`}</option>
            ))}
          </select>
          {fieldError('sub_planId') && <div className="invalid-feedback d-block">{fieldError('sub_planId')}</div>}
        </div>
        <div className="col-md-6">
          {plan && (
            <div className="small border rounded-3 p-2 h-100 bg-body-tertiary">
              <div className="fw-semibold">{plan.name}{plan.description ? ` · ${plan.description}` : ''}</div>
              <div>Includes: <EntitlementList plan={plan} features={features} /></div>
              <div className="text-muted-hh">Default trial {plan.trialDays} days, grace {plan.graceDays} days</div>
            </div>
          )}
        </div>
      </div>

      {plan && (
        <>
          <div className="row g-3 mt-0">
            <div className="col-md-3">
              <label htmlFor="f-sub_startsOn" className="form-label">Start date</label>
              <input id="f-sub_startsOn" type="date" className={`form-control ${fieldError('sub_startsOn') ? 'is-invalid' : ''}`}
                {...register('sub_startsOn', { onChange: (e) => recomputeExpiry(e.target.value, watch('sub_trialDays')) })} />
              {fieldError('sub_startsOn') && <div className="invalid-feedback d-block">{fieldError('sub_startsOn')}</div>}
            </div>
            <div className="col-md-3">
              <label htmlFor="f-sub_trialDays" className="form-label">Trial (days)</label>
              <input id="f-sub_trialDays" type="number" min="0" max="365" step="1" className={`form-control ${fieldError('sub_trialDays') ? 'is-invalid' : ''}`}
                {...register('sub_trialDays', { onChange: (e) => recomputeExpiry(watch('sub_startsOn'), e.target.value) })} />
              {fieldError('sub_trialDays') ? <div className="invalid-feedback d-block">{fieldError('sub_trialDays')}</div>
                : <div className="form-text">0 = no trial, start as a paid subscription</div>}
            </div>
            <div className="col-md-3">
              <label htmlFor="f-sub_expiresOn" className="form-label">{Number(watch('sub_trialDays')) > 0 ? 'Trial ends on' : 'Subscription expires on'}</label>
              <input id="f-sub_expiresOn" type="date" className={`form-control ${fieldError('sub_expiresOn') ? 'is-invalid' : ''}`}
                {...register('sub_expiresOn', { onChange: () => { expiryEdited.current = true; } })} />
              {fieldError('sub_expiresOn') ? <div className="invalid-feedback d-block">{fieldError('sub_expiresOn')}</div>
                : <div className="form-text">Calculated; override if needed</div>}
            </div>
            <div className="col-md-3">
              <label htmlFor="f-sub_graceDays" className="form-label">Grace period (days)</label>
              <input id="f-sub_graceDays" type="number" min="0" max="90" step="1" className={`form-control ${fieldError('sub_graceDays') ? 'is-invalid' : ''}`}
                {...register('sub_graceDays')} />
              {fieldError('sub_graceDays') && <div className="invalid-feedback d-block">{fieldError('sub_graceDays')}</div>}
            </div>
          </div>
          <h3 className="h6 mt-4 mb-2">Limits for this owner</h3>
          <p className="small text-muted-hh">Filled from the plan. Change a value to give this owner a custom limit; existing data is never removed if a limit is later lowered.</p>
          <LimitFields plan={plan} prefix="sub_" register={register} watch={watch} setValue={setValue} errors={errors} />
        </>
      )}
    </fieldset>
  );
}
