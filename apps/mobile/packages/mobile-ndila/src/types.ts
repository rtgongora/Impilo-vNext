/**
 * Type contracts for the Ndila mobile SDK. Mirrors the canonical Ndila API
 * payloads so consumer apps don't depend on the server source directly.
 */

export interface NdilaCoordinate {
  latitude: number;
  longitude: number;
}

export interface NdilaCapturedLocation {
  /** Local identifier — stable across offline restarts. */
  localId: string;
  /** Owner service and entity the coordinate belongs to. */
  ownerService: string;
  ownerEntityType?: string;
  ownerEntityId?: string;

  latitude: number;
  longitude: number;
  altitudeMeters?: number;
  accuracyMeters?: number;
  capturedAt: string; // ISO-8601
  source: "DEVICE_GPS" | "USER_ENTERED" | "DELIVERY" | "INSPECTION" | "FIELD_VERIFICATION";
  syncStatus: "PENDING" | "SYNCING" | "SYNCED" | "FAILED";
  sensitivityLevel?: "PUBLIC" | "INTERNAL" | "RESTRICTED" | "HIGHLY_RESTRICTED";
  metadata?: Record<string, unknown>;
}

export interface NdilaNearbyResult {
  id: string;
  name: string;
  latitude?: number;
  longitude?: number;
  locationType?: string;
  distanceMeters?: number;
  district?: string;
}

export interface NdilaTileConfig {
  tileUrlTemplate: string;
  attribution: string;
  minZoom: number;
  maxZoom: number;
  supportsOffline: boolean;
  provider: string;
}

export interface NdilaRoute {
  success: boolean;
  denialReason?: string;
  distanceMeters?: number;
  durationSeconds?: number;
  mode?: string;
  provider?: string;
  fallbackUsed?: boolean;
  servedFromCache?: boolean;
  polyline?: string;
  warnings?: string[];
  confidence?: number;
}

export type NdilaTravelMode =
  | "WALKING" | "CYCLING" | "MOTORBIKE" | "CAR" | "TRUCK"
  | "AMBULANCE" | "PUBLIC_TRANSPORT" | "BOAT" | "OTHER";

export interface NdilaTrackingEvent {
  assetId: string;
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  speedMps?: number;
  headingDegrees?: number;
  batteryLevel?: number;
  eventTimestamp: string;
  telemetrySource: "MOBILE_APP" | "GPS_DEVICE" | "MANUAL_UPDATE";
}
