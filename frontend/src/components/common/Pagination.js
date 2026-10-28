import Icon from './Icon';
/** CRM-style footer: rows-per-page (optional), "1–20 of 240", page buttons « ‹ 1 2 3 › ». Page is 0-based. */
export default function Pagination({ page = 0, totalPages = 0, totalElements = 0, size, onChange, onSizeChange, sizes = [10, 20, 50, 100], noun = 'records' }) {
  if (!totalElements) return null;
  const pages = Math.max(totalPages || 1, 1);
  const from = size ? page * size + 1 : null;
  const to = size ? Math.min(totalElements, (page + 1) * size) : null;
  const start = Math.max(0, Math.min(page - 2, pages - 5));
  const nums = [];
  for (let p = start; p < Math.min(pages, start + 5); p += 1) nums.push(p);
  return (
    <nav className="hh-pagination" aria-label="Pagination">
      <div className="hh-pagination-info">
        {onSizeChange && (
          <>
            <span>Rows per page:</span>
            <select className="hh-pagination-size" value={size} onChange={(e) => onSizeChange(Number(e.target.value))} aria-label="Rows per page">
              {[...new Set([...sizes, size].filter(Boolean))].sort((a, b) => a - b).map((s) =><option key={s} value={s}>{s}</option>)}
            </select>
          </>
        )}
        <span>{from ? `${from}–${to} of ${totalElements} ${noun}` : `${totalElements} ${noun}`}</span>
        {pages > 1 && <span className="faint">Page <strong>{page + 1}</strong> of <strong>{pages}</strong></span>}
      </div>
      {pages > 1 && (
        <div className="hh-pagination-buttons">
          <button type="button" className="hh-page-btn" disabled={page === 0} onClick={() => onChange(0)} aria-label="First page"><Icon className="bi bi-chevron-double-left" /></button>
          <button type="button" className="hh-page-btn" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page"><Icon className="bi bi-chevron-left" /></button>
          {nums.map((p) => (
            <button key={p} type="button" className={`hh-page-btn ${p === page ? 'active' : ''}`} aria-current={p === page ? 'page' : undefined} onClick={() => onChange(p)}>{p + 1}</button>
          ))}
          <button type="button" className="hh-page-btn" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)} aria-label="Next page"><Icon className="bi bi-chevron-right" /></button>
          <button type="button" className="hh-page-btn" disabled={page + 1 >= pages} onClick={() => onChange(pages - 1)} aria-label="Last page"><Icon className="bi bi-chevron-double-right" /></button>
        </div>
      )}
    </nav>
  );
}
