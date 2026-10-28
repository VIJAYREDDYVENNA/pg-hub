import { useEffect, useState } from 'react';

export default function useMediaQuery(query) {
  const get = () => (typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(query).matches : false);
  const [matches, setMatches] = useState(get);
  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mql = window.matchMedia(query);
    const handler = () => setMatches(mql.matches);
    handler();
    mql.addEventListener ? mql.addEventListener('change', handler) : mql.addListener(handler);
    return () => (mql.removeEventListener ? mql.removeEventListener('change', handler) : mql.removeListener(handler));
  }, [query]);
  return matches;
}
