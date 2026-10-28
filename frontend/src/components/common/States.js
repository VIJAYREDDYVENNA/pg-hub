import Icon from './Icon';
export function LoadingState({ label = 'Loading…', compact = false }) {
  return (
    <div className={`hh-state ${compact ? 'py-3' : 'py-5'}`} role="status" aria-live="polite">
      <div className="spinner-border text-primary-hh" aria-hidden="true" />
      <div className="mt-2 text-muted-hh">{label}</div>
    </div>
  );
}

export function ErrorState({ error, message, onRetry, compact = false }) {
  const text = message || error?.response?.data?.message
    || (error && !error.response ? 'Cannot reach the server. Is the backend running?' : 'Something went wrong while loading this information.');
  return (
    <div className={`hh-state ${compact ? 'py-3' : 'py-5'}`} role="alert">
      <Icon className="bi bi-exclamation-triangle fs-2 text-danger" aria-hidden="true" />
      <div className="mt-2">{text}</div>
      {onRetry && <button type="button" className="btn btn-outline-primary btn-sm mt-3" onClick={onRetry}><Icon className="bi bi-arrow-clockwise me-1" />Try again</button>}
    </div>
  );
}

export function EmptyState({ icon = 'bi-inbox', title = 'Nothing here yet', message, action }) {
  return (
    <div className="hh-state py-5">
      <Icon className={`bi ${icon} fs-1 text-muted-hh`} aria-hidden="true" />
      <div className="fw-semibold mt-2">{title}</div>
      {message && <div className="text-muted-hh small mt-1">{message}</div>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

/** Renders loading / error / empty / content in a consistent way. */
export function AsyncContent({ loading, error, onRetry, empty, emptyProps, children, compact }) {
  if (loading) return <LoadingState compact={compact} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} compact={compact} />;
  if (empty) return <EmptyState {...emptyProps} />;
  return children;
}
