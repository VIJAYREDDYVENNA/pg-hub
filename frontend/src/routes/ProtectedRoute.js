import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoadingState } from '../components/common/States';
import Icon from '../components/common/Icon';

/**
 * Requires a signed-in user; optionally a permission (`perm`), the owner role (`ownerOnly`) or the platform
 * super-admin area (`superAdmin`). Super admins and owner/manager users live in separate portals: each is redirected
 * to its own home when it opens the other's URL. The backend enforces the same rules on every API call.
 */
export default function ProtectedRoute({ children, perm, ownerOnly = false, superAdmin = false }) {
  const { user, initializing, can, isOwner, isSuperAdmin } = useAuth();
  const location = useLocation();
  if (initializing) return <LoadingState label="Checking your session…" />;
  if (!user) {
    return <Navigate to={superAdmin ? '/super-admin/login' : '/login'} replace
      state={{ from: location.pathname + location.search }} />;
  }
  if (superAdmin && !isSuperAdmin) return <Navigate to="/" replace />;
  if (!superAdmin && isSuperAdmin) return <Navigate to="/super-admin/dashboard" replace />;
  if ((perm && !can(perm)) || (ownerOnly && !isOwner)) {
    return (
      <div className="hh-card hh-state py-5 m-3">
        <Icon className="bi bi-shield-lock fs-1 text-muted-hh" />
        <h2 className="h5 mt-2">You don't have access to this page</h2>
        <p className="text-muted-hh mb-0">
          {ownerOnly ? 'Only the account owner can open this page.' : 'Ask the owner to grant you the required permission.'}
        </p>
      </div>
    );
  }
  return children;
}
