/* Minimal offline shell: caches the app shell, always fetches API data from the network. */
const CACHE = 'hostelhub-shell-v1';
self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(['/', '/manifest.json', '/favicon.svg'])));
  self.skipWaiting();
});
self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.pathname.startsWith('/api')) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(fetch(event.request).catch(() => caches.match('/')));
    return;
  }
  event.respondWith(
    caches.match(event.request).then((hit) => hit || fetch(event.request).then((res) => {
      if (res.ok && url.origin === self.location.origin) {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(event.request, copy));
      }
      return res;
    }))
  );
});
