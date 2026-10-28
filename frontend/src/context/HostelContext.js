import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { hostelApi } from '../services/endpoints';
import { useAuth } from './AuthContext';

const HostelContext = createContext(null);
const KEY = 'hh.selectedHostel';

function readStored() {
  try { return window.localStorage.getItem(KEY); } catch (e) { return null; }
}

/** Hostels visible to the user and the globally selected hostel ("" = all hostels). */
export function HostelProvider({ children }) {
  const { user } = useAuth();
  const [hostels, setHostels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(() => readStored() || '');

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await hostelApi.list();
      setHostels(list);
      setSelected((cur) => (cur && !list.some((h) => String(h.id) === String(cur)) ? '' : cur));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { if (user) reload(); }, [user, reload]);

  const select = useCallback((id) => {
    const v = id ? String(id) : '';
    setSelected(v);
    try { window.localStorage.setItem(KEY, v); } catch (e) { /* storage unavailable */ }
  }, []);

  const value = useMemo(() => ({
    hostels,
    loading,
    error,
    reload,
    selectedHostelId: selected ? Number(selected) : null,
    selectedHostel: hostels.find((h) => String(h.id) === String(selected)) || null,
    selectHostel: select,
  }), [hostels, loading, error, reload, selected, select]);

  return <HostelContext.Provider value={value}>{children}</HostelContext.Provider>;
}

export const useHostels = () => useContext(HostelContext);
