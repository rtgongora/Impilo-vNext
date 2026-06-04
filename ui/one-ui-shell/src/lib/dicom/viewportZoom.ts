/** DICOM viewport zoom limits (dedicated viewer + DicomCanvas). */
export const DICOM_ZOOM_MIN = 25;
export const DICOM_ZOOM_MAX = 1200;
export const DICOM_ZOOM_DEFAULT = 100;

export function clampDicomZoom(zoom: number): number {
  return Math.min(DICOM_ZOOM_MAX, Math.max(DICOM_ZOOM_MIN, zoom));
}

/** Wheel / step zoom with larger steps at higher magnification. */
export function stepDicomZoom(current: number, deltaY: number): number {
  const step = current >= 400 ? 40 : current >= 200 ? 25 : current >= 100 ? 15 : 10;
  return clampDicomZoom(current + (deltaY > 0 ? -step : step));
}
