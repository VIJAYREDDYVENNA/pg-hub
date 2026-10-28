/**
 * Controllable stand-in for AuthContext. In a test file:
 *
 *   jest.mock('../../context/AuthContext', () => require('../../test/authMock').factory());
 *   import { setAuth } from '../../test/authMock';
 *   setAuth({ permissions: ['PAYMENT_VIEW'] });          // manager with one permission
 *   setAuth({ owner: true });                             // owner (every permission)
 */
let state = { user: { id: 1, fullName: 'Test User' }, permissions: new Set(), owner: false, superAdmin: false, initializing: false };

export function setAuth({ permissions = [], owner = false, superAdmin = false, user = { id: 1, fullName: 'Test User' }, initializing = false } = {}) {
  state = { user, permissions: new Set(permissions), owner, superAdmin, initializing };
}

export const current = () => ({
  user: state.user,
  initializing: state.initializing,
  logout: () => {},
  isOwner: state.owner,
  isSuperAdmin: state.superAdmin,
  can: (p) => state.owner || state.permissions.has(p),
});

export function factory() {
  return { useAuth: () => current(), AuthProvider: ({ children }) => children };
}
