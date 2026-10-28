import { LIMIT_FIELDS, isPriceOverridden, overriddenLimits } from '../../utils/subscriptionLogic.mjs';
import { money } from '../../utils/format';

/**
 * The three usage limits and the price for one owner, pre-filled from the selected plan. A value that differs from the
 * plan is shown as a custom override (with the plan's value and a reset button) so it is never mistaken for the plan.
 * Field names are `${prefix}maxHostels` etc. so one component serves the create form and the edit dialog.
 */
export default function LimitFields({ plan, prefix = '', register, watch, setValue, errors }) {
  const form = {
    maxHostels: watch(`${prefix}maxHostels`),
    maxBedsPerHostel: watch(`${prefix}maxBedsPerHostel`),
    maxManagers: watch(`${prefix}maxManagers`),
    customMonthlyPrice: watch(`${prefix}customMonthlyPrice`),
  };
  const overridden = overriddenLimits(plan, form);
  const priceOverridden = isPriceOverridden(plan, form);

  return (
    <div className="row g-3">
      {LIMIT_FIELDS.map(({ key, label, min, max }) => {
        const custom = overridden.includes(key);
        const error = errors?.[`${prefix}${key}`]?.message;
        return (
          <div className="col-md-4" key={key}>
            <label htmlFor={`f-${prefix}${key}`} className="form-label">{label}</label>
            <input id={`f-${prefix}${key}`} type="number" inputMode="numeric" min={min} max={max} step="1"
              className={`form-control ${error ? 'is-invalid' : ''} ${custom ? 'border-warning' : ''}`}
              {...register(`${prefix}${key}`)} />
            {error && <div className="invalid-feedback d-block">{error}</div>}
            {custom ? (
              <div className="form-text text-warning-emphasis">
                <span className="badge text-bg-warning me-1">Custom</span>
                overrides the plan ({plan[key]}).{' '}
                <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => setValue(`${prefix}${key}`, String(plan[key]), { shouldDirty: true })}>Use plan value</button>
              </div>
            ) : <div className="form-text">From the plan</div>}
          </div>
        );
      })}
      <div className="col-md-4">
        <label htmlFor={`f-${prefix}customMonthlyPrice`} className="form-label">Monthly price (₹)</label>
        <input id={`f-${prefix}customMonthlyPrice`} type="number" inputMode="decimal" min="0" step="0.01"
          className={`form-control ${errors?.[`${prefix}customMonthlyPrice`] ? 'is-invalid' : ''} ${priceOverridden ? 'border-warning' : ''}`}
          {...register(`${prefix}customMonthlyPrice`)} />
        {errors?.[`${prefix}customMonthlyPrice`] && <div className="invalid-feedback d-block">{errors[`${prefix}customMonthlyPrice`].message}</div>}
        {priceOverridden
          ? <div className="form-text"><span className="badge text-bg-warning me-1">Custom</span>plan price: {plan.monthlyPrice == null ? 'custom' : money(plan.monthlyPrice)}</div>
          : <div className="form-text">{plan.monthlyPrice == null ? 'Custom-priced plan: enter the agreed price (optional)' : 'From the plan'}</div>}
      </div>
    </div>
  );
}
