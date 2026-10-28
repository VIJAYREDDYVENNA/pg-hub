import { useEffect, useState } from 'react';
import { residentApi } from '../../services/endpoints';
import { useHostels } from '../../context/HostelContext';
import { errorMessage } from '../../services/apiClient';
import useDebounce from '../../hooks/useDebounce';
import { label, money } from '../../utils/format';
import Icon from '../common/Icon';

/**
 * Search-as-you-type resident selector for finance forms.
 * value: selected resident ({ id, fullName, mobile, hostelName, roomNumber?, bedLabel? }) or null.
 * onChange(resident | null) receives a ResidentSummary from the list API.
 */
export default function ResidentPicker({ value, onChange, hostelId: hostelIdProp, id = 'resident-picker', label: fieldLabel = 'Resident',
  required = false, autoFocus = false, disabled = false, error }) {
  const { hostels, selectedHostelId } = useHostels();
  // With several hostels and no fixed hostel from the caller, let the user narrow the search.
  const canFilter = hostelIdProp === undefined && hostels.length > 1;
  const [hostelPick, setHostelPick] = useState(selectedHostelId || '');
  const hostelId = canFilter ? (hostelPick || undefined) : hostelIdProp;
  const [q, setQ] = useState('');
  const dq = useDebounce(q.trim(), 300);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [active, setActive] = useState(0);

  useEffect(() => {
    if (value || dq.length < 1) { setResults([]); setLoadError(null); return undefined; }
    let alive = true;
    setLoading(true);
    setLoadError(null);
    residentApi.list({ q: dq, hostelId, size: 8 })
      .then((r) => { if (alive) { setResults(r?.content || []); setActive(0); } })
      .catch((e) => { if (alive) setLoadError(errorMessage(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [dq, hostelId, value]);

  const pick = (r) => { onChange(r); setQ(''); setResults([]); };

  const onKeyDown = (e) => {
    if (!results.length) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    if (e.key === 'Enter') { e.preventDefault(); pick(results[active]); }
    if (e.key === 'Escape') setResults([]);
  };

  if (value) {
    const room = value.roomNumber ? `Room ${value.roomNumber}${value.bedLabel ? ` / ${value.bedLabel}` : ''}` : null;
    return (
      <div className="mb-3">
        {fieldLabel && <div className="form-label">{fieldLabel}</div>}
        <div className="d-flex align-items-center gap-3 p-2 border rounded-3 bg-primary-light">
          <div className="hh-avatar flex-shrink-0" aria-hidden="true">{(value.fullName || '?').charAt(0)}</div>
          <div className="min-w-0 flex-grow-1">
            <div className="fw-semibold text-truncate">{value.fullName}</div>
            <div className="small text-muted-hh text-truncate">
              {[value.mobile, value.hostelName, room].filter(Boolean).join(' · ')}
            </div>
          </div>
          {!disabled && (
            <button type="button" className="btn btn-light btn-sm" onClick={() => onChange(null)}>
              <Icon className="bi bi-arrow-repeat me-1" />Change
            </button>
          )}
        </div>
      </div>
    );
  }

  const listId = `${id}-results`;
  return (
    <div className="mb-3 position-relative">
      {fieldLabel && (
        <label htmlFor={id} className="form-label">{fieldLabel}{required && <span className="text-danger ms-1" aria-hidden="true">*</span>}</label>
      )}
      <div className="d-flex flex-column flex-sm-row gap-2">
      {canFilter && (
        <select className="form-select w-auto flex-shrink-0" aria-label="Filter by hostel" value={hostelPick}
          onChange={(e) => setHostelPick(e.target.value ? Number(e.target.value) : '')} disabled={disabled}>
          <option value="">All hostels</option>
          {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
        </select>
      )}
      <div className="position-relative flex-grow-1">
        <Icon className="bi bi-search position-absolute text-muted-hh" style={{ left: 12, top: '50%', transform: 'translateY(-50%)' }} aria-hidden="true" />
        <input id={id} type="search" className={`form-control ps-5 ${error ? 'is-invalid' : ''}`} placeholder="Search by name, mobile or room number…"
          value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKeyDown} autoFocus={autoFocus} disabled={disabled}
          autoComplete="off" role="combobox" aria-expanded={results.length > 0} aria-controls={listId} />
      </div>
      </div>
      {error && <div className="invalid-feedback d-block">{error}</div>}
      {loading && <div className="form-text"><span className="spinner-border spinner-border-sm me-1" />Searching…</div>}
      {loadError && <div className="small text-danger mt-1">{loadError}</div>}
      {!loading && !loadError && dq.length >= 1 && results.length === 0 && (
        <div className="form-text">No residents match “{dq}”.</div>
      )}
      {results.length > 0 && (
        <ul id={listId} role="listbox" className="list-group position-absolute w-100 shadow-sm mt-1" style={{ zIndex: 20, maxHeight: 320, overflowY: 'auto' }}>
          {results.map((r, i) => (
            <li key={r.id} role="option" aria-selected={i === active}
              className={`list-group-item list-group-item-action d-flex justify-content-between gap-2 py-2 ${i === active ? 'bg-primary-light' : ''}`}
              style={{ cursor: 'pointer' }} onMouseEnter={() => setActive(i)} onMouseDown={(e) => { e.preventDefault(); pick(r); }}>
              <div className="min-w-0">
                <div className="fw-semibold text-truncate">{r.fullName}</div>
                <div className={`small text-truncate text-muted-hh`}>
                  {[r.mobile, r.hostelName, r.roomNumber && `Room ${r.roomNumber}${r.bedLabel ? ` / ${r.bedLabel}` : ''}`, label(r.status)].filter(Boolean).join(' · ')}
                </div>
              </div>
              {Number(r.outstandingAmount) > 0 && (
                <div className="text-end small flex-shrink-0">
                  <div className="fw-semibold">{money(r.outstandingAmount)}</div>
                  <div className="text-muted-hh">due</div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
