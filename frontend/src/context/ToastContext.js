import { createContext, useCallback, useContext, useState } from 'react';
import Icon from '../components/common/Icon';

const ToastContext = createContext({ notify: () => {} });

let seq = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const notify = useCallback((message, variant = 'success') => {
    const id = ++seq;
    setToasts((t) => [...t, { id, message, variant }]);
    setTimeout(() => dismiss(id), variant === 'danger' ? 7000 : 4000);
  }, [dismiss]);

  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      <div className="toast-stack" aria-live="polite" aria-atomic="true">
        {toasts.map((t) => (
          <div key={t.id} className={`hh-toast hh-toast-${t.variant}`} role="status">
            <Icon className={`bi ${t.variant === 'danger' ? 'bi-exclamation-octagon' : t.variant === 'warning' ? 'bi-exclamation-triangle' : 'bi-check-circle'} me-2`} />
            <span className="flex-grow-1">{t.message}</span>
            <button type="button" className="hh-close sm ms-2" aria-label="Dismiss" onClick={() => dismiss(t.id)}><Icon className="bi bi-x-lg" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
