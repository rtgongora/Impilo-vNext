/**
 * Register the Impilo feature-scoped service worker (W16b).
 *
 * Kill switch: any page load with `?sw=off` unregisters the worker and skips registration.
 */

export async function registerImpiloServiceWorker(): Promise<"registered" | "skipped" | "unsupported"> {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) {
    return "unsupported";
  }

  const params = new URLSearchParams(window.location.search);
  if (params.get("sw") === "off") {
    const regs = await navigator.serviceWorker.getRegistrations();
    await Promise.all(regs.map((r) => r.unregister()));
    return "skipped";
  }

  try {
    await navigator.serviceWorker.register("/impilo-sw.js", { scope: "/" });
    return "registered";
  } catch {
    return "skipped";
  }
}
