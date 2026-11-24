/* 计件通 Service Worker：优先联网拿新版本，断网时回退缓存 */
const VERSION = '2.0.9';
const CACHE = 'jitong-v16';
const ASSETS = [
  './index.html',
  `./styles.css?v=${VERSION}`,
  `./core.js?v=${VERSION}`,
  `./pages-worker.js?v=${VERSION}`,
  `./pages-main.js?v=${VERSION}`,
  `./app.js?v=${VERSION}`,
  `./vendor/qrcode.js?v=${VERSION}`,
  `./vendor/jsQR.js?v=${VERSION}`,
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

async function networkFirst(request, fallbackUrl) {
  const cache = await caches.open(CACHE);
  try {
    const response = await fetch(request);
    if (response && response.status === 200) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    const cached = await cache.match(request, { ignoreSearch: true });
    if (cached) return cached;
    if (fallbackUrl) {
      const fallback = await cache.match(fallbackUrl, { ignoreSearch: true });
      if (fallback) return fallback;
    }
    throw err;
  }
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  // API 必须实时访问服务器，绝不能走缓存
  if (url.pathname.startsWith('/api/')) return;

  // 页面导航：必须优先拿最新的 index.html，避免旧页面没有新按钮逻辑
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request, './index.html'));
    return;
  }

  // 代码与样式：网络优先，断网回退缓存
  if (/\.(?:js|css|html)$/i.test(url.pathname)) {
    event.respondWith(networkFirst(request));
    return;
  }

  // 图片、图标：缓存优先
  event.respondWith(
    caches.match(request, { ignoreSearch: true }).then((cached) => {
      if (cached) return cached;
      return fetch(request).then((response) => {
        if (response && response.status === 200) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(request, copy));
        }
        return response;
      });
    })
  );
});
