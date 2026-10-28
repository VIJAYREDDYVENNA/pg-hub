import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import Icon from './Icon';

/** Right-side panel (full screen on phones). */
export default function Drawer({ show, title, subtitle, onClose, children, footer, width = 520 }) {
  useEffect(() => {
    if (!show) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [show, onClose]);
  if (!show) return null;
  return createPortal(
    <div className="hh-drawer-wrap" role="dialog" aria-modal="true" aria-label={title}>
      <div className="hh-drawer-backdrop" onClick={onClose} />
      <aside className="hh-drawer" style={{ '--drawer-w': `${width}px` }}>
        <header className="hh-drawer-header">
          <div className="min-w-0">
            <h5 className="mb-0 text-truncate">{title}</h5>
            {subtitle && <div className="small text-muted-hh">{subtitle}</div>}
          </div>
          <button type="button" className="hh-close" aria-label="Close" onClick={onClose}><Icon className="bi bi-x-lg" /></button>
        </header>
        <div className="hh-drawer-body">{children}</div>
        {footer && <footer className="hh-drawer-footer">{footer}</footer>}
      </aside>
    </div>,
    document.body
  );
}
