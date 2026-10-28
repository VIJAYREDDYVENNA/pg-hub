import { Link } from 'react-router-dom';
import { dateTime, label } from '../../utils/format';
import Icon from '../../components/common/Icon';

/** Status display metadata for owner accounts (icon + text, never colour alone). */
export const OWNER_STATUS = {
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' },
  SUSPENDED: { label: 'Suspended', cls: 'warning', icon: 'bi-pause-circle-fill' },
  INACTIVE: { label: 'Inactive', cls: 'secondary', icon: 'bi-slash-circle' },
};

export const HOSTEL_STATUS = {
  ACTIVE: { label: 'Active', cls: 'success', icon: 'bi-check-circle-fill' },
  INACTIVE: { label: 'Inactive', cls: 'secondary', icon: 'bi-slash-circle' },
};

/** Breadcrumb trail rooted at the platform dashboard (the shared PageHeader defaults to the owner dashboard). */
export const saCrumbs = (...rest) => [{ label: 'Platform', to: '/super-admin/dashboard' }, ...rest];

export const percentOf = (part, whole) => (whole > 0 ? Math.round((part / whole) * 100) : 0);

/** One line in an activity feed. */
export function ActivityItem({ a }) {
  return (
    <li className="d-flex gap-2 py-2 border-bottom">
      <span className="text-primary-hh mt-1"><Icon className="bi bi-activity" /></span>
      <span className="min-w-0 flex-grow-1">
        <span className="d-block fw-medium">{label(a.action)}{a.ownerName && <span className="text-muted-hh fw-normal"> · {a.ownerName}</span>}</span>
        {a.details && <span className="d-block small text-muted-hh text-truncate">{a.details}</span>}
        <span className="d-block small text-muted-hh">{a.actorName ? `${a.actorName} · ` : ''}{dateTime(a.createdAt)}</span>
      </span>
    </li>
  );
}

export function OwnerLink({ id, children }) {
  return <Link to={`/super-admin/owners/${id}`} className="hh-cell-primary" onClick={(e) => e.stopPropagation()}>{children}</Link>;
}
