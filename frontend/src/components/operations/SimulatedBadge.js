import Icon from '../common/Icon';
/**
 * Marks WhatsApp messages produced by the development mock provider. Real messages can get an explicit
 * "Live" marker so the two are never confused.
 */
export default function SimulatedBadge({ simulated, showLive = false, className = '' }) {
  if (simulated) {
    return (
      <span className={`badge hh-badge hh-badge-simulated ${className}`} title="Simulated in development mock mode – not sent to WhatsApp">
        <Icon className="bi bi-cone-striped me-1" aria-hidden="true" />SIMULATED
      </span>
    );
  }
  if (!showLive) return null;
  return (
    <span className={`badge hh-badge hh-badge-success ${className}`} title="Sent through the WhatsApp Cloud API">
      <Icon className="bi bi-whatsapp me-1" aria-hidden="true" />Live
    </span>
  );
}
