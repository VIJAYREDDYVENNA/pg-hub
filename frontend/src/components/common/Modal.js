import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import Icon from './Icon';

/** Accessible modal dialog (Bootstrap styles, React-controlled). */
export default function Modal({ show, title, onClose, children, footer, size = '', scrollable = true }) {
  const ref = useRef(null);
  useEffect(() => {
    if (!show) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', onKey);
    document.body.classList.add('modal-open');
    setTimeout(() => ref.current?.querySelector('input, select, textarea, button.btn-primary')?.focus(), 50);
    return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('modal-open'); };
  }, [show, onClose]);
  if (!show) return null;
  return createPortal(
    <>
      <div className="modal fade show d-block" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1}
        onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
        <div className={`modal-dialog modal-dialog-centered ${scrollable ? 'modal-dialog-scrollable' : ''} ${size ? `modal-${size}` : ''} modal-fullscreen-sm-down`} ref={ref}>
          <div className="modal-content hh-modal">
            <div className="modal-header">
              <h5 className="modal-title">{title}</h5>
              <button type="button" className="hh-close" aria-label="Close" onClick={onClose}><Icon className="bi bi-x-lg" /></button>
            </div>
            <div className="modal-body">{children}</div>
            {footer && <div className="modal-footer">{footer}</div>}
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show" />
    </>,
    document.body
  );
}
