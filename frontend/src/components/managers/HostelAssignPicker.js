import { useMemo, useState } from 'react';
import Icon from '../common/Icon';

/**
 * Searchable, multi-select list of hostel cards used to assign hostels to a manager.
 * `hostels` must already be limited to the signed-in owner's hostels (the backend re-checks on save).
 * `managerNames`: Map(hostelId -> names of the other managers currently assigned).
 */
export default function HostelAssignPicker({ hostels, value, onChange, managerNames = new Map(), error }) {
  const [q, setQ] = useState('');
  const selected = useMemo(() => new Set(value), [value]);
  const term = q.trim().toLowerCase();
  const visible = hostels.filter((h) => !term || [h.name, h.city, h.state].some((v) => (v || '').toLowerCase().includes(term)));

  const toggle = (id) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id); else next.add(id);
    onChange([...next]);
  };

  return (
    <fieldset>
      <legend className="form-label fs-6 d-flex flex-wrap justify-content-between align-items-center gap-2">
        <span>Assign hostels — <strong>{selected.size} selected</strong><span className="text-danger ms-1" aria-hidden="true">*</span></span>
        <span className="d-flex gap-2">
          <button type="button" className="btn btn-sm btn-light" onClick={() => onChange(hostels.map((h) => h.id))} disabled={hostels.length === 0}>Select all</button>
          <button type="button" className="btn btn-sm btn-light" onClick={() => onChange([])} disabled={selected.size === 0}>Clear selection</button>
        </span>
      </legend>
      <div className="hh-search-input mb-2" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search your hostels…" aria-label="Search hostels"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {hostels.length === 0 && <div className="text-muted-hh small">You have no hostels yet. Create a hostel first, then assign it.</div>}
      {hostels.length > 0 && visible.length === 0 && <div className="text-muted-hh small">No hostels match “{q}”.</div>}
      <div className="row g-2">
        {visible.map((h) => {
          const on = selected.has(h.id);
          const others = managerNames.get(h.id) || [];
          return (
            <div className="col-md-6" key={h.id}>
              <label className={`d-flex gap-2 p-2 border rounded-3 h-100 ${on ? 'border-primary bg-light' : ''}`} style={{ cursor: 'pointer', borderWidth: on ? 2 : 1 }}>
                <input type="checkbox" className="form-check-input mt-1" checked={on} onChange={() => toggle(h.id)} aria-label={`Assign ${h.name}`} />
                <span className="min-w-0">
                  <span className="d-flex align-items-center gap-2">
                    <span className="fw-semibold text-truncate">{h.name}</span>
                    <span className={`badge hh-badge ${on ? 'hh-badge-success' : 'hh-badge-secondary'}`}>{on ? 'Selected' : 'Not selected'}</span>
                  </span>
                  <span className="d-block small text-muted-hh"><Icon className="bi bi-geo-alt me-1" />{[h.city, h.state].filter(Boolean).join(', ') || '—'}</span>
                  <span className="d-block small text-muted-hh">
                    {h.stats?.totalRooms ?? 0} rooms · {h.stats?.occupiedBeds ?? 0} residents
                  </span>
                  <span className="d-block small text-muted-hh">
                    Current manager{others.length === 1 ? '' : 's'}: {others.length ? others.join(', ') : 'none'}
                  </span>
                </span>
              </label>
            </div>
          );
        })}
      </div>
      {error && <div className="invalid-feedback d-block">{error}</div>}
    </fieldset>
  );
}
