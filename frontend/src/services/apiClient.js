import axios from 'axios';

/**
 * Central API client. The short-lived access token is kept in memory only (never in localStorage);
 * the refresh token lives in an HttpOnly cookie managed by the backend. A 401 triggers one refresh attempt.
 */
let accessToken = null;
let refreshPromise = null;
let onSessionExpired = () => {};

export const setAccessToken = (token) => { accessToken = token; };
export const getAccessToken = () => accessToken;
export const setSessionExpiredHandler = (fn) => { onSessionExpired = fn; };

const api = axios.create({ baseURL: '/api', withCredentials: true, timeout: 30000 });

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/auth/refresh', null, { withCredentials: true })
      .then((res) => { accessToken = res.data.accessToken; return res.data; })
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      try {
        await refreshSession();
        return api(original);
      } catch (e) {
        accessToken = null;
        onSessionExpired();
      }
    }
    return Promise.reject(error);
  }
);

/** Extracts a friendly message (and field errors) from an API error. */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback;
  if (error.friendly) return error.message;
  if (error.response?.data?.message) return error.response.data.message;
  if (error.code === 'ECONNABORTED') return 'The server took too long to respond.';
  if (!error.response) return 'Cannot reach the server. Check your connection or that the backend is running.';
  return fallback;
}

export function fieldErrors(error) {
  return error?.response?.data?.fieldErrors || {};
}

/** Downloads a binary/CSV endpoint using the authenticated client. */
export async function downloadFile(url, params, filename) {
  const res = await api.get(url, { params, responseType: 'blob' });
  const blobUrl = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
}

/** Opens an authenticated file (PDF, image) in a new tab. */
export async function openFile(url) {
  const win = window.open('', '_blank');
  const res = await api.get(url, { responseType: 'blob' });
  const blobUrl = URL.createObjectURL(res.data);
  if (win) win.location.href = blobUrl; else window.location.href = blobUrl;
}

export default api;
