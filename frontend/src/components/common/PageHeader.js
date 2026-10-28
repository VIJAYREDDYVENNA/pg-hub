import { Link } from 'react-router-dom';
import Icon from './Icon';

/** CRM-style page header: breadcrumb, title with icon, actions on the right. */
export default function PageHeader({ title, subtitle, icon, actions, children, breadcrumb }) {
  const crumbs = breadcrumb || [{ label: 'Dashboard', to: '/' }, { label: title }];
  return (
    <div className="hh-page-header">
      <div className="min-w-0">
        <nav className="hh-breadcrumb" aria-label="Breadcrumb">
          {crumbs.map((c, i) => (
            <span key={`${c.label}-${i}`}>
              {i > 0 && <Icon className="bi bi-chevron-right sep" />}
              {c.to && i < crumbs.length - 1 ? <Link to={c.to}>{c.label}</Link> : <span className={i === crumbs.length - 1 ? 'active' : ''}>{c.label}</span>}
            </span>
          ))}
        </nav>
        <h1 className="hh-page-title">{icon && <span className="hh-title-icon"><Icon className={`bi ${icon}`} aria-hidden="true" /></span>}{title}</h1>
        {subtitle && <p className="text-muted-hh mb-0 small">{subtitle}</p>}
        {children}
      </div>
      {actions && <div className="hh-page-actions">{actions}</div>}
    </div>
  );
}
