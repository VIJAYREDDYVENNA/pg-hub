import { useEffect, useState } from 'react';
import api from '../../services/apiClient';

/** Displays an uploaded file that requires the Authorization header (e.g. /api/files/12). */
export default function AuthImage({ src, alt = '', className, fallback = null }) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    let objectUrl;
    let alive = true;
    setUrl(null);
    if (src) {
      api.get(src.replace(/^\/api/, ''), { responseType: 'blob' })
        .then((r) => { if (alive) { objectUrl = URL.createObjectURL(r.data); setUrl(objectUrl); } })
        .catch(() => {});
    }
    return () => { alive = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [src]);
  return url ? <img src={url} alt={alt} className={className} /> : fallback;
}
