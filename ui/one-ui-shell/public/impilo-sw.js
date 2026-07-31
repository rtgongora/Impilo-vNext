/**
 * Impilo experience shell — feature-scoped service worker (W16b).
 *
 * Scope: `/` (registered from the client). Behaviour:
 * - `?sw=off` on any navigation unregisters and bypasses caching (kill switch).
 * - Only routes enrolled in `/offline-feature-manifest.json` may hit the cache.
 * - All other routes (~800+) are network-only passthrough — never touched.
 * - Next.js RSC payloads (`_rsc`, `text/x-component`) are never cached.
 * - Cache name is version-stamped from the manifest (`cacheName`).
 *
 * This file is hand-written on purpose: Next's built-in SW tooling would either
 * precache the whole app or fight App Router streaming. Keep it small and honest.
 */
/* eslint-disable no-restricted-globals */

const MANIFEST_URL = "/offline-feature-manifest.json";
const KILL_QUERY = "sw";

/** @type {{ cacheName: string, pagePrefixes: string[], staticAssets: string[] } | null} */
let enrollment = null;

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      await loadEnrollment();
      if (enrollment) {
        const cache = await caches.open(enrollment.cacheName);
        await cache.addAll(enrollment.staticAssets.filter(Boolean));
      }
      self.skipWaiting();
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      await loadEnrollment();
      const keep = enrollment?.cacheName;
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter((k) => k.startsWith("impilo-emergency-") && k !== keep)
          .map((k) => caches.delete(k)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  if (url.searchParams.get(KILL_QUERY) === "off") {
    event.respondWith(killSwitch(req));
    return;
  }

  if (isRscRequest(req, url)) {
    event.respondWith(fetch(req));
    return;
  }

  event.respondWith(handleGet(req, url));
});

async function killSwitch(req) {
  try {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k.startsWith("impilo-")).map((k) => caches.delete(k)));
    const regs = await self.registration.unregister();
    void regs;
  } catch {
    // best-effort
  }
  return fetch(req);
}

async function handleGet(req, url) {
  await loadEnrollment();
  if (!enrollment || !isEnrolledPage(url.pathname)) {
    return fetch(req);
  }

  try {
    const fresh = await fetch(req);
    if (fresh.ok && !isRscResponse(fresh)) {
      const cache = await caches.open(enrollment.cacheName);
      void cache.put(req, fresh.clone());
    }
    return fresh;
  } catch {
    const cached = await caches.match(req);
    if (cached) {
      const headers = new Headers(cached.headers);
      headers.set("X-Impilo-Offline-Cache", "1");
      return new Response(cached.body, {
        status: cached.status,
        statusText: cached.statusText,
        headers,
      });
    }
    return new Response("Offline — emergency shell not cached for this URL.", {
      status: 503,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }
}

function isEnrolledPage(pathname) {
  if (!enrollment) return false;
  return enrollment.pagePrefixes.some(
    (prefix) =>
      pathname === prefix ||
      pathname.startsWith(prefix + "/") ||
      (prefix === "/ehr/" && pathname.includes("/emergency")),
  );
}

function isRscRequest(req, url) {
  if (url.searchParams.has("_rsc")) return true;
  const accept = req.headers.get("Accept") || "";
  return accept.includes("text/x-component");
}

function isRscResponse(res) {
  const ct = res.headers.get("Content-Type") || "";
  return ct.includes("text/x-component") || ct.includes("text/x-ref");
}

async function loadEnrollment() {
  if (enrollment) return;
  try {
    const res = await fetch(MANIFEST_URL, { cache: "no-store" });
    if (!res.ok) return;
    const json = await res.json();
    const emergency = json?.features?.emergency;
    if (!emergency?.enrolled) return;
    enrollment = {
      cacheName: String(json.cacheName || json.version || "impilo-emergency-fallback"),
      pagePrefixes: Array.isArray(emergency.pagePrefixes) ? emergency.pagePrefixes : [],
      staticAssets: Array.isArray(emergency.staticAssets) ? emergency.staticAssets : [MANIFEST_URL],
    };
  } catch {
    enrollment = null;
  }
}
