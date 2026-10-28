import { useState } from 'react';
import Icon from '../common/Icon';
import { today } from '../../utils/format';
import { PAYMENT_METHODS } from '../../utils/status';
import { categoryOptions } from './expenseUtils';

/** Filter panel. Values are edited in `draft` and only take effect when Apply is pressed. */
export default function ExpenseFilters({ hostels, draft, setDraft, onApply, onReset, rangeError }) {
  const [more, setMore] = useState(false);
  const set = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }));
  const extraActive = draft.method !== 'ALL' || draft.minAmount !== '' || draft.maxAmount !== '';

  const submit = (e) => { e.preventDefault(); onApply(); };

  return (
    <form className="hh-card hh-exp-filters" onSubmit={submit} aria-label="Expense filters">
      <div className="hh-exp-filter-grid">
        <div>
          <label className="hh-exp-label" htmlFor="exp-hostel">Hostel</label>
          <select id="exp-hostel" className="form-select" value={draft.hostelId} onChange={set('hostelId')}>
            <option value="">All hostels</option>
            {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
          </select>
        </div>
        <div>
          <label className="hh-exp-label" htmlFor="exp-category">Category</label>
          <select id="exp-category" className="form-select" value={draft.category} onChange={set('category')}>
            <option value="ALL">All categories</option>
            {categoryOptions.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </select>
        </div>
        <div>
          <span className="hh-exp-label" id="exp-range-label">Date range</span>
          <div className={`hh-exp-range ${rangeError ? 'invalid' : ''}`} role="group" aria-labelledby="exp-range-label">
            <Icon className="bi bi-calendar3" aria-hidden="true" />
            <input type="date" aria-label="From date" value={draft.from} max={draft.to || today()} onChange={set('from')} />
            <span>to</span>
            <input type="date" aria-label="To date" value={draft.to} min={draft.from || undefined} max={today()} onChange={set('to')} />
          </div>
        </div>
        <div>
          <label className="hh-exp-label" htmlFor="exp-search">Search</label>
          <div className="hh-exp-search">
            <Icon className="bi bi-search" aria-hidden="true" />
            <input id="exp-search" type="search" className="form-control" placeholder="Search expense, vendor, notes..."
              value={draft.q} onChange={set('q')} />
          </div>
        </div>
      </div>

      {more && (
        <div className="hh-exp-more">
          <div>
            <label className="hh-exp-label" htmlFor="exp-method">Paid by</label>
            <select id="exp-method" className="form-select" value={draft.method} onChange={set('method')}>
              <option value="ALL">Any method</option>
              {PAYMENT_METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </div>
          <div>
            <label className="hh-exp-label" htmlFor="exp-min">Min amount (₹)</label>
            <input id="exp-min" type="number" min="0" inputMode="decimal" className="form-control" value={draft.minAmount} onChange={set('minAmount')} />
          </div>
          <div>
            <label className="hh-exp-label" htmlFor="exp-max">Max amount (₹)</label>
            <input id="exp-max" type="number" min="0" inputMode="decimal" className="form-control" value={draft.maxAmount} onChange={set('maxAmount')} />
          </div>
        </div>
      )}
      {rangeError && <div className="invalid-feedback d-block mt-1">{rangeError}</div>}

      <div className="hh-exp-filter-actions">
        <button type="button" className="hh-exp-more-btn" aria-expanded={more} onClick={() => setMore((m) => !m)}>
          <Icon className="bi bi-funnel" aria-hidden="true" />More filters{extraActive && <span className="hh-exp-dot" aria-label="active" />}
        </button>
        <span className="ms-auto d-flex gap-2">
          <button type="button" className="btn btn-light border" onClick={onReset}>Reset</button>
          <button type="submit" className="btn btn-primary px-4" disabled={!!rangeError}>Apply</button>
        </span>
      </div>
    </form>
  );
}
