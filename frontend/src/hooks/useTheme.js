import { useCallback, useEffect, useState } from 'react';

const KEY = 'hh.theme';

export function readTheme() {
  try { return window.localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light'; } catch (e) { return 'light'; }
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  document.documentElement.setAttribute('data-bs-theme', theme);
}

/** Light/dark theme preference (per browser; purely visual). */
export default function useTheme() {
  const [theme, setTheme] = useState(readTheme);
  useEffect(() => {
    applyTheme(theme);
    try { window.localStorage.setItem(KEY, theme); } catch (e) { /* storage unavailable */ }
  }, [theme]);
  const toggle = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), []);
  return { theme, toggle };
}
