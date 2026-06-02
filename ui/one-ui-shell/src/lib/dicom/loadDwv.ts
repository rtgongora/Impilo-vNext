/**
 * Client-only dynamic import for DWV — avoids SSR and keeps webpack from re-parsing via SWC.
 */
export type DwvModule = typeof import("dwv");

let cached: Promise<DwvModule> | null = null;

export function loadDwvModule(): Promise<DwvModule> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("DWV is only available in the browser"));
  }
  if (!cached) {
    cached = import("dwv");
  }
  return cached;
}
