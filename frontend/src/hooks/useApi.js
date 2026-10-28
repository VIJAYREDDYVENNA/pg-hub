import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async loader and tracks loading / error / data. Re-runs when deps change.
 * Ignores results of stale requests.
 */
export default function useApi(loader, deps = [], { enabled = true } = {}) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);
  const counter = useRef(0);

  const run = useCallback(async () => {
    const id = ++counter.current;
    setLoading(true);
    setError(null);
    try {
      const result = await loader();
      if (id === counter.current) setData(result);
      return result;
    } catch (e) {
      if (id === counter.current) setError(e);
      return null;
    } finally {
      if (id === counter.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (enabled) run();
  }, [run, enabled]);

  return { data, setData, loading, error, reload: run };
}
