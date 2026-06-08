/**
 * @impilo/mobile-ndila — Mobile Ndila SDK
 *
 * Provider App + Citizen App + Field workflows share this SDK to:
 *   - capture GPS coordinates with offline fallback
 *   - request Ndila tile configuration (provider chosen server-side)
 *   - search nearby services / facilities / public health sites
 *   - validate captured coordinates against Ndila plausibility rules
 *   - request routes and ETAs
 *   - submit tracking events for delivery riders, mobile teams or
 *     emergency response assets
 *
 * All Ndila SDK calls go through @impilo/mobile-api-client and therefore
 * carry the canonical trust headers (tenant, actor, purpose-of-use, etc.).
 */

export type {
  NdilaCoordinate,
  NdilaCapturedLocation,
  NdilaNearbyResult,
  NdilaTileConfig,
  NdilaRoute,
  NdilaTravelMode,
  NdilaTrackingEvent,
} from "./types";

export { ndilaMobile } from "./ndilaClient";
export type { NdilaClient } from "./ndilaClient";

export {
  captureCurrentLocation,
  configureGeolocation,
  haversineMeters,
} from "./gpsCapture";
export { configureMobileGeolocationFromExpo } from "./configureMobileGeolocation";
export type { GpsCaptureResult, GeolocationLike } from "./gpsCapture";

export {
  queueLocationCapture,
  queueTrackingEvent,
  flushQueue,
  pendingCount,
  subscribeQueue,
} from "./offlineQueue";

export {
  useNdilaTileConfig,
  useNearbyServices,
  useDeviceLocation,
  useNdilaOfflineQueue,
  haversine,
} from "./hooks";

export { MobileNdilaMapView } from "./MobileNdilaMapView";
export type { MobileNdilaMapMarker, MobileNdilaMapViewProps } from "./MobileNdilaMapView";

export { MobileNearbyMapView } from "./MobileNearbyMapView";
export type { MobileNearbyMapViewProps } from "./MobileNearbyMapView";
