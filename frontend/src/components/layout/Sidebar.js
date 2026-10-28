import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Icon from '../common/Icon';

const NAV = [
  { to: '/', icon: 'bi-house-door-fill', label: 'Dashboard', end: true },
  { to: '/hostels', icon: 'bi-houses', label: 'Hostels', perm: 'HOSTEL_VIEW' },
  { to: '/residents', icon: 'bi-people-fill', label: 'Residents', perm: 'RESIDENT_VIEW' },
  { to: '/enquiries', icon: 'bi-chat-dots', label: 'Enquiries', perm: 'ENQUIRY_MANAGE' },
  { to: '/rooms', icon: 'bi-building', label: 'Rooms & Floors' },
  { to: '/payments', icon: 'bi-wallet2', label: 'Payments', perm: 'PAYMENT_VIEW' },
  { to: '/expenses', icon: 'bi-credit-card-2-front', label: 'Expenses', perm: 'EXPENSE_MANAGE' },
  { to: '/maintenance', icon: 'bi-tools', label: 'Maintenance', perm: 'MAINTENANCE_MANAGE' },
  { to: '/staff', icon: 'bi-person-badge', label: 'Staff', perm: 'STAFF_MANAGE' },
  { to: '/vacancies', icon: 'bi-calendar2-week', label: 'Upcoming vacancies' },
  { to: '/notifications', icon: 'bi-bell', label: 'Notifications' },
  { to: '/announcements', icon: 'bi-megaphone', label: 'Notices' },
];

export default function Sidebar({ open, onClose }) {
  const { can, isOwner } = useAuth();
  const link = (i) => (
    <NavLink key={i.to} to={i.to} end={i.end}>
      <Icon className={`bi ${i.icon}`} aria-hidden="true" />{i.label}
    </NavLink>
  );
  return (
    <aside className={`hh-sidebar ${open ? 'open' : ''}`} aria-label="Main navigation">
      <div className="hh-brand">
        <span className="hh-brand-logo"><Icon className="bi bi-house-heart-fill" /></span>
        <span className="min-w-0">
          <span className="hh-brand-name d-block">HostelHub</span>
          <span className="hh-brand-tag d-block">Stay Together, Grow Together</span>
        </span>
        <button type="button" className="hh-close light ms-auto d-lg-none position-relative" style={{ zIndex: 2 }} aria-label="Close menu" onClick={onClose}><Icon className="bi bi-x-lg" /></button>
      </div>
      <nav className="hh-nav">
        {NAV.filter((i) => (!i.perm || can(i.perm)) && (!i.ownerOnly || isOwner)).map(link)}
      </nav>
    </aside>
  );
}
