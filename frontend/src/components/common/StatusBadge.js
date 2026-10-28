import { label as fmtLabel } from '../../utils/format';
import Icon from './Icon';

/** Badge with icon + text so status is never conveyed by colour alone. */
export default function StatusBadge({ map, value, className = '' }) {
  const meta = (map && map[value]) || { label: fmtLabel(value), cls: 'secondary' };
  return (
    <span className={`badge hh-badge hh-badge-${meta.cls} ${className}`}>
      {meta.icon && <Icon className={`bi ${meta.icon} me-1`} aria-hidden="true" />}
      {meta.label}
    </span>
  );
}
