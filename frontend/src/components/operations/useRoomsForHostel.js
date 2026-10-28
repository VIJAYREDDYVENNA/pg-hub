import { useEffect, useState } from 'react';
import { roomApi } from '../../services/endpoints';

/** Loads rooms (with their beds) for one hostel, for pickers. */
export default function useRoomsForHostel(hostelId) {
  const [state, setState] = useState({ rooms: [], loading: false, error: null });
  useEffect(() => {
    if (!hostelId) {
      setState({ rooms: [], loading: false, error: null });
      return undefined;
    }
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    roomApi.list({ hostelId, size: 200 })
      .then((r) => { if (alive) setState({ rooms: r.content || [], loading: false, error: null }); })
      .catch((e) => { if (alive) setState({ rooms: [], loading: false, error: e }); });
    return () => { alive = false; };
  }, [hostelId]);
  return state;
}
