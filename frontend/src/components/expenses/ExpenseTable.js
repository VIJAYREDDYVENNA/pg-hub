import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Icon from '../common/Icon';
import Pagination from '../common/Pagination';
import { EmptyState, LoadingState } from '../common/States';
import { date, label, money } from '../../utils/format';
import { METHOD_ICONS, methodLabel } from '../payments/financeUtils';
import { CATEGORY_ICONS, CATEGORY_STYLE, downloadCsv } from './expenseUtils';

const COLUMNS = [
  { key: 'description', label: 'Expense', required: true, value: (r) => r.description.toLowerCase() },
  { key: 'expenseDate', label: 'Date', value: (r) => r.expenseDate || '' },
  { key: 'category', label: 'Category', value: (r) => r.category },
  { key: 'hostel', label: 'Hostel', value: (r, ctx) => ctx.hostelName(r.hostelId).toLowerCase() },
  { key: 'paymentMethod', label: 'Paid by', value: (r) => methodLabel(r.paymentMethod) },
  { key: 'amount', label: 'Amount', align: 'end', value: (r) => Number(r.amount) },
];

/** A small floating menu anchored to a button, rendered in a portal so table scrolling never clips it. */
function FloatingMenu({ trigger, children, align = 'end', menuLabel }) {
  const [pos, setPos] = useState(null);
  const btn = useRef(null);
  const menu = useRef(null);
  const close = () => setPos(null);

  useEffect(() => {
    if (!pos) return undefined;
    const onDown = (e) => { if (!menu.current?.contains(e.target) && !btn.current?.contains(e.target)) close(); };
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);

  const toggle = () => {
    if (pos) { close(); return; }
    const r = btn.current.getBoundingClientRect();
    setPos({ top: r.bottom + 4, right: window.innerWidth - r.right, left: r.left, align });
  };

  return (
    <>
      {trigger({ ref: btn, onClick: toggle, 'aria-haspopup': 'menu', 'aria-expanded': !!pos })}
      {pos && createPortal(
        <div ref={menu} className="hh-exp-menu" role="menu" aria-label={menuLabel}
          style={{ top: pos.top, ...(pos.align === 'end' ? { right: pos.right } : { left: pos.left }) }}
          onClick={close}>
          {children}
        </div>, document.body)}
    </>
  );
}

function SortMark({ dir }) {
  if (!dir) return <Icon className="bi bi-arrow-down-up hh-exp-sort" aria-hidden="true" />;
  return <Icon className={`bi ${dir === 'asc' ? 'bi-arrow-up' : 'bi-arrow-down'} hh-exp-sort active`} aria-hidden="true" />;
}

/**
 * The expenses table: row selection, sortable columns, column visibility, CSV export, per-row menu and pagination.
 * `rows` is the full filtered dataset; sorting and paging happen here.
 */
export default function ExpenseTable({ rows, loading, hostelName, onEdit, onRefresh, filtered }) {
  const [sort, setSort] = useState({ key: null, dir: 'asc' });
  const [hidden, setHidden] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  useEffect(() => { setPage(0); setSelected(new Set()); }, [rows]);

  const visible = COLUMNS.filter((c) => !hidden.includes(c.key));
  const sorted = useMemo(() => {
    const col = COLUMNS.find((c) => c.key === sort.key);
    if (!col) return rows;
    const ctx = { hostelName };
    const out = [...rows].sort((a, b) => {
      const x = col.value(a, ctx);
      const y = col.value(b, ctx);
      return x < y ? -1 : x > y ? 1 : 0;
    });
    return sort.dir === 'desc' ? out.reverse() : out;
  }, [rows, sort, hostelName]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / size));
  const current = Math.min(page, totalPages - 1);
  const pageRows = sorted.slice(current * size, current * size + size);
  const allOnPage = pageRows.length > 0 && pageRows.every((r) => selected.has(r.id));

  const cycleSort = (key) => setSort((s) => (s.key !== key ? { key, dir: 'asc' } : s.dir === 'asc' ? { key, dir: 'desc' } : { key: null, dir: 'asc' }));
  const toggleRow = (id) => setSelected((cur) => { const n = new Set(cur); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const togglePage = () => setSelected((cur) => {
    const n = new Set(cur);
    pageRows.forEach((r) => (allOnPage ? n.delete(r.id) : n.add(r.id)));
    return n;
  });
  const toggleColumn = (key) => setHidden((h) => (h.includes(key) ? h.filter((k) => k !== key) : [...h, key]));
  const exportRows = () => downloadCsv(selected.size ? sorted.filter((r) => selected.has(r.id)) : sorted, hostelName);

  const cell = (c, r) => {
    switch (c.key) {
      case 'description':
        return (
          <div className="hh-exp-name">
            <Icon className={`bi ${CATEGORY_ICONS[r.category] || 'bi-tag'} hh-exp-name-icon`} style={{ color: (CATEGORY_STYLE[r.category] || CATEGORY_STYLE.OTHER).fg }} aria-hidden="true" />
            <span className="min-w-0">
              <span className="d-block text-truncate">{r.description}</span>
              {(r.vendor || r.reference) && <span className="hh-exp-sub text-truncate d-block">{[r.vendor, r.reference && `Ref ${r.reference}`].filter(Boolean).join(' · ')}</span>}
            </span>
          </div>
        );
      case 'expenseDate': return <span className="text-muted-hh">{date(r.expenseDate)}</span>;
      case 'category': {
        const st = CATEGORY_STYLE[r.category] || CATEGORY_STYLE.OTHER;
        return <span className="hh-exp-badge" style={{ color: st.fg, background: st.bg }}><Icon className={`bi ${CATEGORY_ICONS[r.category] || 'bi-tag'}`} aria-hidden="true" />{label(r.category)}</span>;
      }
      case 'hostel': return <span className="text-muted-hh">{hostelName(r.hostelId)}</span>;
      case 'paymentMethod': return <span className="text-muted-hh"><Icon className={`bi ${METHOD_ICONS[r.paymentMethod] || 'bi-cash'} me-1`} aria-hidden="true" />{methodLabel(r.paymentMethod)}</span>;
      case 'amount': return <span className="fw-bold">{money(r.amount)}</span>;
      default: return null;
    }
  };

  return (
    <section className="hh-card hh-exp-table-card" aria-labelledby="exp-table-title">
      <div className="hh-exp-table-head">
        <h2 id="exp-table-title">Expenses <span>({rows.length})</span></h2>
        <div className="d-flex gap-2">
          <FloatingMenu menuLabel="Visible columns"
            trigger={(p) => <button type="button" className="btn btn-light border btn-sm" {...p}><Icon className="bi bi-layout-three-columns me-1" />Columns</button>}>
            {COLUMNS.map((c) => (
              <label key={c.key} className="hh-exp-menu-item" onClick={(e) => e.stopPropagation()}>
                <input type="checkbox" className="form-check-input m-0" checked={!hidden.includes(c.key)} disabled={c.required} onChange={() => toggleColumn(c.key)} />
                {c.label}
              </label>
            ))}
          </FloatingMenu>
          <button type="button" className="btn btn-light border btn-sm" onClick={exportRows} disabled={rows.length === 0}
            title={selected.size ? `Export ${selected.size} selected` : 'Export all rows shown'}>
            <Icon className="bi bi-download me-1" />Export{selected.size > 0 && ` (${selected.size})`}
          </button>
          <FloatingMenu menuLabel="More actions"
            trigger={(p) => <button type="button" className="btn btn-light border btn-sm" aria-label="More actions" {...p}><Icon className="bi bi-three-dots" /></button>}>
            <button type="button" role="menuitem" className="hh-exp-menu-item" onClick={onRefresh}><Icon className="bi bi-arrow-clockwise" />Refresh</button>
            <button type="button" role="menuitem" className="hh-exp-menu-item" disabled={selected.size === 0} onClick={() => setSelected(new Set())}><Icon className="bi bi-x-lg" />Clear selection</button>
            <button type="button" role="menuitem" className="hh-exp-menu-item" disabled={!sort.key} onClick={() => setSort({ key: null, dir: 'asc' })}><Icon className="bi bi-arrow-down-up" />Reset sorting</button>
          </FloatingMenu>
        </div>
      </div>

      <div className="hh-exp-scroll">
        <table className="hh-exp-table">
          <thead>
            <tr>
              <th className="w-check"><input type="checkbox" className="form-check-input" aria-label="Select all rows on this page" checked={allOnPage} onChange={togglePage} disabled={pageRows.length === 0} /></th>
              <th className="w-sno">S.No</th>
              {visible.map((c) => {
                const dir = sort.key === c.key ? sort.dir : null;
                return (
                  <th key={c.key} className={c.align === 'end' ? 'text-end' : ''} aria-sort={dir ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                    <button type="button" className="hh-exp-th" onClick={() => cycleSort(c.key)}>{c.label}<SortMark dir={dir} /></button>
                  </th>
                );
              })}
              <th className="text-center w-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((r, i) => (
              <tr key={r.id} className={selected.has(r.id) ? 'selected' : ''}>
                <td className="w-check"><input type="checkbox" className="form-check-input" aria-label={`Select ${r.description}`} checked={selected.has(r.id)} onChange={() => toggleRow(r.id)} /></td>
                <td className="w-sno text-muted-hh">{current * size + i + 1}</td>
                {visible.map((c) => <td key={c.key} className={c.align === 'end' ? 'text-end' : ''}>{cell(c, r)}</td>)}
                <td className="text-center w-actions">
                  <FloatingMenu menuLabel={`Actions for ${r.description}`}
                    trigger={(p) => <button type="button" className="hh-exp-kebab" aria-label={`Actions for ${r.description}`} {...p}><Icon className="bi bi-three-dots-vertical" /></button>}>
                    <button type="button" role="menuitem" className="hh-exp-menu-item" onClick={() => onEdit(r)}><Icon className="bi bi-pencil-square" />Edit expense</button>
                  </FloatingMenu>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {pageRows.length === 0 && (loading ? <LoadingState /> : (
          <EmptyState icon="bi-wallet" title={filtered ? 'No matching expenses' : 'No expenses recorded'}
            message={filtered ? 'Try different filters or a wider date range.' : 'Add bills, salaries and other costs to see where the money goes.'} />
        ))}
      </div>

      <Pagination page={current} totalPages={totalPages} totalElements={sorted.length} size={size} noun="expenses"
        onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />
    </section>
  );
}
