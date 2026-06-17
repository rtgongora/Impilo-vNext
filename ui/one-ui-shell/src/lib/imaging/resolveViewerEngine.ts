/** Viewer engines handled entirely on the frontend until PACS policy adds them. */
export const FRONTEND_ONLY_VIEWER_ENGINES = new Set(["DWV_NATIVE"]);

/**
 * Resolves the active imaging viewer engine from governed launch context and request hints.
 * Frontend-only engines (e.g. DWV_NATIVE) take precedence over launch context when explicitly requested,
 * because PACS ViewerEnginePolicy may not yet list them.
 */
export function resolveImagingViewerEngine(
  launchContextEngine: string | null | undefined,
  requestedViewerType: string | null | undefined,
  defaultEngine = "DICOMWEB_STACK",
): string {
  const requested = requestedViewerType?.trim().toUpperCase() ?? "";
  if (requested && FRONTEND_ONLY_VIEWER_ENGINES.has(requested)) {
    return requested;
  }
  const fromLaunch = launchContextEngine?.trim().toUpperCase();
  if (fromLaunch) return fromLaunch;
  if (requested) return requested;
  return defaultEngine;
}
