import { Link } from 'react-router-dom';
import Icon from '../common/Icon';

/** Banner describing the WhatsApp integration mode returned by /whatsapp/status. */
export default function WhatsAppModeBanner({ status, compact = false, showLink = false }) {
  if (!status) return null;
  if (status.mode === 'MOCK' || status.simulated) {
    return (
      <div className="alert alert-warning d-flex gap-3 align-items-start border border-2 border-warning" role="status">
        <Icon className="bi bi-cone-striped fs-3 flex-shrink-0" aria-hidden="true" />
        <div className="min-w-0">
          <div className="fw-bold">Development mock mode — messages are SIMULATED and are not sent to WhatsApp</div>
          {!compact && (
            <div className="small">
              {status.note} Every simulated message is labelled <span className="badge hh-badge hh-badge-simulated">SIMULATED</span>.
            </div>
          )}
          {showLink && <Link to="/settings" className="small">How to connect the WhatsApp Cloud API</Link>}
        </div>
      </div>
    );
  }
  return (
    <div className={`alert ${status.configured ? 'alert-success' : 'alert-danger'} d-flex gap-3 align-items-start`} role="status">
      <Icon className={`bi ${status.configured ? 'bi-whatsapp' : 'bi-exclamation-octagon'} fs-3 flex-shrink-0`} aria-hidden="true" />
      <div className="min-w-0">
        <div className="fw-bold">
          WhatsApp Cloud API (Meta) · {status.configured ? 'Configured — messages are sent for real' : 'Not configured — messages will fail'}
        </div>
        {!compact && <div className="small">{status.note}</div>}
        {showLink && !status.configured && <Link to="/settings" className="small">Setup instructions</Link>}
      </div>
    </div>
  );
}
