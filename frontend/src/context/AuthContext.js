import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../services/endpoints';
import { refreshSession, setAccessToken, setSessionExpiredHandler } from '../services/apiClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initializing, setInitializing] = useState(true);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    setSessionExpiredHandler(() => { setUser(null); setExpired(true); });
    // Restore the session from the HttpOnly refresh cookie (if any) on page load.
    refreshSession()
      .then((data) => setUser(data.user))
      .catch(() => setUser(null))
      .finally(() => setInitializing(false));
  }, []);

  /**
   * The portal argument keeps the two sign-in pages separate: 'super-admin' accepts only super admins, 'tenant'
   * (owners and managers) rejects them. A wrong-portal login is signed straight back out.
   */
  const login = useCallback(async (email, password, portal) => {
    const data = await authApi.login(email, password);
    setAccessToken(data.accessToken);
    const isSuperAdmin = !!data.user?.roles?.includes('SUPER_ADMIN');
    if ((portal === 'super-admin' && !isSuperAdmin) || (portal === 'tenant' && isSuperAdmin)) {
      try { await authApi.logout(); } catch (e) { /* ignore */ }
      setAccessToken(null);
      const err = new Error(isSuperAdmin
        ? 'This is a platform administrator account. Please use the Super Admin sign-in page.'
        : 'This account cannot sign in to the Super Admin portal.');
      err.friendly = true;
      throw err;
    }
    setExpired(false);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(async () => {
    try { await authApi.logout(); } catch (e) { /* ignore */ }
    setAccessToken(null);
    setUser(null);
  }, []);

  const reloadUser = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
    return me;
  }, []);

  const value = useMemo(() => ({
    user,
    initializing,
    expired,
    login,
    logout,
    reloadUser,
    isOwner: !!user?.roles?.includes('OWNER'),
    isManager: !!user?.roles?.includes('MANAGER'),
    isSuperAdmin: !!user?.roles?.includes('SUPER_ADMIN'),
    can: (perm) => !!user && (user.roles?.includes('OWNER') || user.permissions?.includes(perm)),
  }), [user, initializing, expired, login, logout, reloadUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
