import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Icon from '../common/Icon';

export default function BottomNav({ onMore }) {
  const { can } = useAuth();
  return (
    <nav className="hh-bottom-nav" aria-label="Quick navigation">
      <NavLink to="/" end><Icon className="bi bi-grid-1x2" />Home</NavLink>
      <NavLink to="/rooms"><Icon className="bi bi-building" />Rooms</NavLink>
      {can('RESIDENT_VIEW') ? <NavLink to="/residents"><Icon className="bi bi-people" />Residents</NavLink>
        : <NavLink to="/vacancies"><Icon className="bi bi-calendar2-week" />Vacancies</NavLink>}
      {can('PAYMENT_VIEW') ? <NavLink to="/payments"><Icon className="bi bi-wallet2" />Payments</NavLink>
        : <NavLink to="/notifications"><Icon className="bi bi-bell" />Alerts</NavLink>}
      <a href="#menu" onClick={(e) => { e.preventDefault(); onMore(); }}><Icon className="bi bi-list" />More</a>
    </nav>
  );
}
