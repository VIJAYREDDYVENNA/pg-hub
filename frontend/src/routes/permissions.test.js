import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import Sidebar from '../components/layout/Sidebar';
import { setAuth } from '../test/authMock';
import { renderApp } from '../test/renderUtils';

jest.mock('../context/AuthContext', () => require('../test/authMock').factory());

function renderRoute(element, route = '/secret') {
  return renderApp(
    <Routes>
      <Route path="/secret" element={element} />
      <Route path="/login" element={<div>Login page</div>} />
      <Route path="/super-admin/login" element={<div>Super admin login page</div>} />
      <Route path="/super-admin/dashboard" element={<div>Super admin home</div>} />
      <Route path="/" element={<div>Owner home</div>} />
    </Routes>,
    { route }
  );
}

describe('ProtectedRoute', () => {
  test('waits while the session is being checked', () => {
    setAuth({ user: null, initializing: true });
    renderRoute(<ProtectedRoute><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText('Checking your session…')).toBeInTheDocument();
  });

  test('sends a signed-out visitor to the login page', () => {
    setAuth({ user: null });
    renderRoute(<ProtectedRoute><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
  });

  test('shows the page to a user who holds the required permission', () => {
    setAuth({ permissions: ['PAYMENT_VIEW'] });
    renderRoute(<ProtectedRoute perm="PAYMENT_VIEW"><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText('Secret content')).toBeInTheDocument();
  });

  test('explains the missing permission instead of showing the page', () => {
    setAuth({ permissions: ['HOSTEL_VIEW'] });
    renderRoute(<ProtectedRoute perm="PAYMENT_VIEW"><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText("You don't have access to this page")).toBeInTheDocument();
    expect(screen.getByText('Ask the owner to grant you the required permission.')).toBeInTheDocument();
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
  });

  test('owners pass every permission check', () => {
    setAuth({ owner: true });
    renderRoute(<ProtectedRoute perm="STAFF_MANAGE"><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText('Secret content')).toBeInTheDocument();
  });

  test('owner-only pages are closed to managers, whatever permissions they hold', () => {
    setAuth({ permissions: ['STAFF_MANAGE', 'SETTINGS_MANAGE'] });
    renderRoute(<ProtectedRoute ownerOnly><div>Secret content</div></ProtectedRoute>);
    expect(screen.getByText('Only the account owner can open this page.')).toBeInTheDocument();
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
  });

  test('a Super Admin is kept out of the owner portal and an owner out of the Super Admin portal', () => {
    setAuth({ superAdmin: true });
    const first = renderRoute(<ProtectedRoute><div>Owner page</div></ProtectedRoute>);
    expect(screen.getByText('Super admin home')).toBeInTheDocument();
    first.unmount();

    setAuth({ owner: true });
    renderRoute(<ProtectedRoute superAdmin><div>Platform page</div></ProtectedRoute>);
    expect(screen.getByText('Owner home')).toBeInTheDocument();
    expect(screen.queryByText('Platform page')).not.toBeInTheDocument();
  });

  test('a signed-out visitor to a platform page goes to the Super Admin login', () => {
    setAuth({ user: null });
    renderRoute(<ProtectedRoute superAdmin><div>Platform page</div></ProtectedRoute>);
    expect(screen.getByText('Super admin login page')).toBeInTheDocument();
  });
});

describe('Sidebar navigation follows permissions', () => {
  const links = () => screen.getAllByRole('link').map((a) => a.textContent);

  test('a front-desk user with residents and payments access sees only those areas', () => {
    setAuth({ permissions: ['RESIDENT_VIEW', 'PAYMENT_VIEW'] });
    renderApp(<Sidebar open onClose={() => {}} />);

    const names = links();
    expect(names).toEqual(expect.arrayContaining(['Dashboard', 'Residents', 'Payments', 'Rooms & Floors', 'Notifications']));
    expect(names).not.toContain('Hostels');
    expect(names).not.toContain('Expenses');
    expect(names).not.toContain('Staff');
    expect(names).not.toContain('Maintenance');
  });

  test('a user with no special permissions sees only the general pages', () => {
    setAuth({ permissions: [] });
    renderApp(<Sidebar open onClose={() => {}} />);

    expect(links()).toEqual(['Dashboard', 'Rooms & Floors', 'Upcoming vacancies', 'Notifications', 'Notices']);
  });

  test('an owner sees everything', () => {
    setAuth({ owner: true });
    renderApp(<Sidebar open onClose={() => {}} />);

    expect(links()).toEqual(expect.arrayContaining(['Hostels', 'Residents', 'Payments', 'Expenses', 'Maintenance', 'Staff']));
  });
});
