import { useEffect, useMemo, useRef, useState } from 'react';
import Icon from './Icon';

/*
 * Data table modelled on the ISTL CRM list tables: bordered card, optional toolbar, sticky uppercase header with
 * sort icons, compact 13px rows with hover, S.No column, column show/hide menu and a footer (pagination).
 * On phones rows become stacked cards.
 *
 * columns: [{ key, label, render?(row), sortValue?(row) | sortable?, align?: 'center'|'end', width?, className?,
 *             hideOnMobile?, required? }]
 */
function SortIcon({ dir }) {
  if (!dir) return <Icon className="bi bi-arrow-down-up hh-sort-icon" aria-hidden="true" />;
  return <Icon className={`bi ${dir === 'asc' ? 'bi-chevron-up' : 'bi-chevron-down'} hh-sort-icon active`} aria-hidden="true" />;
}

function readHidden(tableId) {
  if (!tableId) return [];
  try { return JSON.parse(window.localStorage.getItem(`hh.cols.${tableId}`) || '[]'); } catch (e) { return []; }
}

function ColumnMenu({ columns, hidden, setHidden }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  const toggle = (k) => setHidden(hidden.includes(k) ? hidden.filter((x) => x !== k) : [...hidden, k]);
  return (
    <div className="hh-colmenu" ref={ref}>
      <button type="button" className={`hh-colmenu-btn ${hidden.length ? 'has-hidden' : ''}`} onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <Icon className="bi bi-layout-three-columns" />Columns{hidden.length > 0 && <span className="cnt">{hidden.length}</span>}
      </button>
      {open && (
        <div className="hh-colmenu-pop" role="menu">
          <div className="hh-colmenu-head"><span>Show columns</span><button type="button" onClick={() => setHidden([])}>Reset</button></div>
          {columns.filter((c) => c.label).map((c) => (
            <label key={c.key} className="hh-colmenu-item">
              <input type="checkbox" checked={!hidden.includes(c.key)} disabled={c.required} onChange={() => toggle(c.key)} />
              <span>{c.label}</span>{c.required && <em>required</em>}
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

export default function DataTable({ columns, rows, rowKey = 'id', onRowClick, empty, serial = false, serialStart = 0,
  tableId, toolbar, footer, maxHeight, emptyText = 'No records found' }) {
  const [hidden, setHiddenState] = useState(() => readHidden(tableId));
  const [sort, setSort] = useState({ key: null, dir: 'asc' });
  const setHidden = (h) => {
    setHiddenState(h);
    if (tableId) { try { window.localStorage.setItem(`hh.cols.${tableId}`, JSON.stringify(h)); } catch (e) { /* ignore */ } }
  };
  const visible = columns.filter((c) => !hidden.includes(c.key));

  const sorted = useMemo(() => {
    if (!rows || !sort.key) return rows || [];
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return rows;
    const val = col.sortValue || ((r) => r[col.key]);
    const out = [...rows].sort((a, b) => {
      const x = val(a); const y = val(b);
      if (x === y) return 0;
      if (x === null || x === undefined || x === '') return 1;
      if (y === null || y === undefined || y === '') return -1;
      const nx = Number(x); const ny = Number(y);
      if (!Number.isNaN(nx) && !Number.isNaN(ny)) return nx - ny;
      return String(x).localeCompare(String(y), undefined, { numeric: true, sensitivity: 'base' });
    });
    return sort.dir === 'desc' ? out.reverse() : out;
  }, [rows, sort, columns]);

  const canSort = (c) => c.sortable !== false && (c.sortValue || c.sortable || (!c.render && c.key));
  const onSort = (c) => {
    if (!canSort(c)) return;
    setSort((s) => (s.key === c.key ? { key: c.key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key: c.key, dir: 'asc' }));
  };

  if ((!rows || rows.length === 0) && empty) {
    return (
      <div className="hh-table-card">
        {(toolbar || tableId) && <div className="hh-table-toolbar">{toolbar}{tableId && <div className="ms-auto"><ColumnMenu columns={columns} hidden={hidden} setHidden={setHidden} /></div>}</div>}
        {empty}
        {footer}
      </div>
    );
  }

  return (
    <div className="hh-table-card">
      {(toolbar || tableId) && (
        <div className="hh-table-toolbar">
          {toolbar}
          {tableId && <div className="ms-auto d-none d-md-block"><ColumnMenu columns={columns} hidden={hidden} setHidden={setHidden} /></div>}
        </div>
      )}
      <div className="hh-table-wrapper d-none d-md-block" style={maxHeight ? { maxHeight } : undefined}>
        <table className="hh-crm-table">
          <thead>
            <tr>
              {serial && <th className="hh-sno">S.No</th>}
              {visible.map((c) => (
                <th key={c.key} style={c.width ? { width: c.width } : undefined}
                  className={`${canSort(c) ? 'sortable' : ''} ${c.align ? `text-${c.align}` : ''} ${c.className || ''}`}
                  onClick={() => onSort(c)} aria-sort={sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                  <span className={`hh-th ${c.align === 'center' ? 'justify-content-center' : c.align === 'end' ? 'justify-content-end' : ''}`}>
                    {c.label}{canSort(c) && c.label && <SortIcon dir={sort.key === c.key ? sort.dir : null} />}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sorted.length === 0 ? (
              <tr><td colSpan={visible.length + (serial ? 1 : 0)} className="hh-empty-row">{emptyText}</td></tr>
            ) : sorted.map((r, i) => (
              <tr key={r[rowKey]} className={onRowClick ? 'clickable' : ''} onClick={onRowClick ? () => onRowClick(r) : undefined}
                tabIndex={onRowClick ? 0 : undefined} onKeyDown={onRowClick ? (e) => { if (e.key === 'Enter' && e.target === e.currentTarget) onRowClick(r); } : undefined}>
                {serial && <td className="hh-sno">{serialStart + i + 1}</td>}
                {visible.map((c) => (
                  <td key={c.key} className={`${c.align ? `text-${c.align}` : ''} ${c.className || ''}`}>
                    {c.render ? c.render(r) : (r[c.key] === null || r[c.key] === undefined || r[c.key] === '' ? <span className="hh-dash">—</span> : r[c.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="d-md-none hh-card-list">
        {sorted.length === 0 ? <div className="hh-empty-row">{emptyText}</div> : sorted.map((r, i) => (
          <div key={r[rowKey]} className={`hh-list-card ${onRowClick ? 'clickable' : ''}`} onClick={onRowClick ? () => onRowClick(r) : undefined}>
            {serial && <div className="small text-muted-hh mb-1">#{serialStart + i + 1}</div>}
            {visible.filter((c) => !c.hideOnMobile).map((c) => (
              <div key={c.key} className="d-flex justify-content-between align-items-center gap-2 py-1">
                <span className="text-muted-hh small">{c.label}</span>
                <span className="text-end">{c.render ? c.render(r) : r[c.key]}</span>
              </div>
            ))}
          </div>
        ))}
      </div>
      {footer}
    </div>
  );
}

/** Icon-only row action (view / edit / delete / whatsapp / ...), stops the row click. */
export function RowAction({ variant = 'view', icon, label, onClick, href, disabled }) {
  const icons = { view: 'bi-eye', edit: 'bi-pencil-square', delete: 'bi-trash3', whatsapp: 'bi-whatsapp', pay: 'bi-cash-coin',
    remind: 'bi-bell', call: 'bi-telephone', print: 'bi-printer', checkout: 'bi-box-arrow-right', more: 'bi-three-dots' };
  const content = <Icon className={`bi ${icon || icons[variant] || 'bi-dot'}`} aria-hidden="true" />;
  const props = { className: `hh-row-action ${variant}`, title: label, 'aria-label': label };
  if (href) {
    return <a {...props} href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer" onClick={(e) => e.stopPropagation()}>{content}</a>;
  }
  return (
    <button type="button" {...props} disabled={disabled} onClick={(e) => { e.stopPropagation(); onClick?.(e); }}>{content}</button>
  );
}

export function RowActions({ children }) {
  return <div className="hh-row-actions" onClick={(e) => e.stopPropagation()}>{children}</div>;
}
