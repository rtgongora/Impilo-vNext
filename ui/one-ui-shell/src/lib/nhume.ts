/**
 * Nhume — Dispatch, Delivery, Fleet Tracking & Last-Mile Logistics
 *
 * Frontend API surface for services/nhume-service. All requests go through
 * `apiClient` so that mandatory trust headers, correlation IDs, idempotency
 * keys and step-up handling are applied uniformly. The Nhume backend mounts
 * its public surface at /api/v1/nhume and its internal surface at
 * /internal/v1/nhume; the BFF can proxy either depending on the surface.
 *
 * Domain types here mirror the service's response shape. They are kept loose
 * (Record<string, unknown>) on collections so that incremental fields added on
 * the backend do not break the UI before TypeScript is updated.
 */

import { apiClient } from "./api-client";

// ============================================================================
// Domain types (shared with services/nhume-service responses)
// ============================================================================

export type NhumeDeliveryStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "VALIDATION_REQUIRED"
  | "AWAITING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "AWAITING_PAYMENT"
  | "AWAITING_STOCK"
  | "AWAITING_PICKUP"
  | "DISPATCH_PENDING"
  | "ASSIGNED"
  | "ACCEPTED"
  | "EN_ROUTE_TO_PICKUP"
  | "PICKED_UP"
  | "IN_TRANSIT"
  | "AT_DESTINATION"
  | "ATTEMPTED"
  | "DELIVERED"
  | "PARTIALLY_DELIVERED"
  | "FAILED"
  | "RETURNED"
  | "CANCELLED"
  | "DISPUTED"
  | "CLOSED";

export type NhumePriority = "ROUTINE" | "STANDARD" | "URGENT" | "EMERGENCY";

export type NhumeDeliveryMode =
  | "WALKING_COURIER"
  | "BICYCLE"
  | "MOTORCYCLE"
  | "CAR"
  | "VAN"
  | "TRUCK"
  | "AMBULANCE"
  | "BOAT"
  | "DRONE"
  | "AUTONOMOUS_VEHICLE"
  | "ROBOT"
  | "SMART_LOCKER"
  | "PARTNER_COURIER"
  | "THIRD_PARTY_FLEET"
  | "PUBLIC_SECTOR_VEHICLE"
  | "COMMUNITY_HEALTH_WORKER"
  | "FACILITY_TRANSPORT"
  | "OTHER";

export interface DeliverySummary {
  delivery_id: string;
  reference: string;
  delivery_type?: string | null;
  priority?: NhumePriority | string;
  status: NhumeDeliveryStatus | string;
  created_at?: string;
  updated_at?: string;
  request_source?: string | null;
  origin_label?: string | null;
  destination_label?: string | null;
  recipient_label?: string | null;
  assigned_courier?: string | null;
  sla_due_at?: string | null;
  cold_chain_required?: boolean;
  chain_of_custody_required?: boolean;
  is_demo?: boolean;
}

export interface DashboardSnapshot {
  generated_at: string;
  tenant_id: string;
  counters: Record<string, number>;
  fleet: { total: number; available: number; in_use: number; maintenance: number; offline: number };
  couriers: { total: number; available: number; on_duty: number; off_duty: number; suspended: number };
  recent_events: Array<Record<string, unknown>>;
  sla_at_risk: Array<DeliverySummary>;
  cold_chain_alerts: Array<Record<string, unknown>>;
  custody_exceptions: Array<Record<string, unknown>>;
  map_provider: { provider: string; simulated: boolean; metadata?: Record<string, unknown> };
}

export interface DispatcherConsole {
  generated_at: string;
  pending_queue: Array<DeliverySummary>;
  suggested_assignments: Array<{
    delivery_id: string;
    reference: string;
    suggested_courier_id?: string;
    suggested_asset_id?: string;
    rationale: string;
    score: number;
  }>;
  available_couriers: Array<Record<string, unknown>>;
  available_assets: Array<Record<string, unknown>>;
  sla_breaches: Array<DeliverySummary>;
  active_routes: Array<Record<string, unknown>>;
}

// ============================================================================
// Request payloads
// ============================================================================

export interface CreateDeliveryRequestPayload {
  delivery_type: string;
  priority?: NhumePriority | string;
  request_source: string;
  requesting_person_ref?: string;
  requesting_organisation_ref?: string;
  requesting_facility_ref?: string;
  origin: NhumeLocationPayload;
  destination: NhumeLocationPayload;
  recipient: NhumeRecipientPayload;
  items: Array<NhumeItemPayload>;
  packages?: Array<NhumePackagePayload>;
  required_by?: string;
  pickup_window_start?: string;
  pickup_window_end?: string;
  delivery_window_start?: string;
  delivery_window_end?: string;
  clinical_context_ref?: string;
  telehealth_context_ref?: string;
  marketplace_order_ref?: string;
  payment_reference?: string;
  consent_required?: boolean;
  identity_verification_required?: string; // "NONE" | "OTP" | "BIOMETRIC"
  cold_chain_required?: boolean;
  controlled_item?: boolean;
  fragile?: boolean;
  hazardous?: boolean;
  specimen?: boolean;
  biohazard?: boolean;
  chain_of_custody_required?: boolean;
  return_required?: boolean;
  declared_value?: number;
  notes?: string;
  attachments?: Array<Record<string, unknown>>;
  allowed_modes?: Array<NhumeDeliveryMode | string>;
  policy_code?: string;
  metadata?: Record<string, unknown>;
  submit_immediately?: boolean;
}

export interface NhumeLocationPayload {
  label?: string;
  facility_ref?: string;
  public_health_site_ref?: string;
  address_line?: string;
  city?: string;
  district?: string;
  province?: string;
  country?: string;
  latitude?: number;
  longitude?: number;
  geofence_radius_m?: number;
  privacy_class?: "PUBLIC" | "ZONE_ONLY" | "RESTRICTED";
  notes?: string;
}

export interface NhumeRecipientPayload {
  vito_client_ref?: string;
  facility_ref?: string;
  display_name?: string;
  phone?: string;
  email?: string;
  preferred_channel?: "SMS" | "PUSH" | "WHATSAPP" | "EMAIL" | "VOICE" | "IN_APP";
}

export interface NhumeItemPayload {
  description: string;
  quantity?: number;
  unit?: string;
  msika_product_ref?: string;
  product_code?: string;
  controlled?: boolean;
  cold_chain?: boolean;
  hazardous?: boolean;
  weight_kg?: number;
  volume_l?: number;
  notes?: string;
}

export interface NhumePackagePayload {
  package_kind?: string; // BOX, COOLER, ENVELOPE, DRUM, BLOOD_PACK, etc.
  seal_id?: string;
  weight_kg?: number;
  volume_l?: number;
  temperature_target_min_c?: number;
  temperature_target_max_c?: number;
}

export interface AssignDeliveryPayload {
  courier_id?: string;
  asset_id?: string;
  mode?: NhumeDeliveryMode | string;
  reason?: string;
  notes?: string;
}

export interface StatusChangePayload {
  reason?: string;
  notes?: string;
}

export interface TrackingUpdatePayload {
  latitude: number;
  longitude: number;
  accuracy_m?: number;
  heading?: number;
  speed_mps?: number;
  battery_pct?: number;
  device_id?: string;
  source?: string; // GPS, NETWORK, MANUAL, TELEMATICS, DRONE_TELEMETRY
  recorded_at?: string;
  metadata?: Record<string, unknown>;
}

export interface ProofPayload {
  method: string; // OTP, QR, SIGNATURE, FACILITY_STAMP, PHOTO, GEOFENCE, etc.
  /** PICKUP = collection sign-off, DELIVERY = drop-off sign-off. Defaults DELIVERY server-side. */
  proof_stage?: "PICKUP" | "DELIVERY";
  /** True only for the drop-off sign-off — marks the mission delivered. */
  mark_delivered?: boolean;
  captured_by?: string;
  signature_uri?: string;
  evidence_ref?: string;
  otp_code?: string;
  recipient_name?: string;
  notes?: string;
  captured_at?: string;
  captured_lat?: number;
  captured_lng?: number;
  metadata?: Record<string, unknown>;
}

export interface ChainOfCustodyPayload {
  event_kind: string; // PREPARED, PACKED, SEALED, RELEASED, PICKED_UP, TRANSFERRED, ARRIVED_HUB, DEPARTED_HUB, DELIVERED, RETURNED, DAMAGED, LOST, TEMP_BREACH, DISPUTED, RECONCILED
  custody_holder_ref?: string;
  custody_location_label?: string;
  seal_id?: string;
  temperature_c?: number;
  evidence_ref?: string;
  verification_method?: string;
  notes?: string;
  exception?: boolean;
  metadata?: Record<string, unknown>;
}

// ============================================================================
// Client surface
// ============================================================================

const BASE = "/internal/v1/nhume";

function headersForIdempotency(key?: string) {
  return key ? { extraHeaders: { "Idempotency-Key": key } } : undefined;
}

export const nhumeApi = {
  // Dashboards & console
  dashboard: () => apiClient.get<DashboardSnapshot>(`${BASE}/dashboard`),
  dispatcherConsole: () => apiClient.get<DispatcherConsole>(`${BASE}/dispatcher-console`),
  zones: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/zones`),
  mapToken: () => apiClient.get<{ provider: string; simulated: boolean; metadata?: Record<string, unknown> }>(`${BASE}/map-token`),

  // Deliveries — collection
  listDeliveries: (params?: { status?: string; priority?: string; q?: string; assignedTo?: string; size?: number }) => {
    const query = new URLSearchParams();
    if (params?.status) query.set("status", params.status);
    if (params?.priority) query.set("priority", params.priority);
    if (params?.q) query.set("q", params.q);
    if (params?.assignedTo) query.set("assigned_to", params.assignedTo);
    if (params?.size) query.set("size", String(params.size));
    const qs = query.toString();
    return apiClient.get<{ data: DeliverySummary[] }>(`${BASE}/deliveries${qs ? `?${qs}` : ""}`);
  },
  getDelivery: (id: string) => apiClient.get<Record<string, unknown>>(`${BASE}/deliveries/${id}`),
  getTimeline: (id: string) => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/deliveries/${id}/timeline`),
  getItems: (id: string) => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/deliveries/${id}/items`),
  getTracking: (id: string) => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/deliveries/${id}/tracking`),
  getCustody: (id: string) => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/deliveries/${id}/custody`),

  // Deliveries — lifecycle
  createDelivery: (body: CreateDeliveryRequestPayload, idempotencyKey?: string) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/deliveries`, body, headersForIdempotency(idempotencyKey)),
  submit: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/submit`, body ?? {}),
  approve: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/approve`, body ?? {}),
  reject: (id: string, body: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/reject`, body),
  cancel: (id: string, body: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/cancel`, body),
  fail: (id: string, body: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/fail`, body),
  returnDelivery: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/return`, body ?? {}),
  startTransit: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/start`, body ?? {}),
  assign: (id: string, body: AssignDeliveryPayload, idempotencyKey?: string) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/assign`, body, headersForIdempotency(idempotencyKey)),
  reassign: (id: string, body: AssignDeliveryPayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/reassign`, body),
  accept: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/accept`, body ?? {}),
  decline: (id: string, body: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/decline`, body),
  pickup: (id: string, body?: StatusChangePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/pickup`, body ?? {}),
  recordLocation: (id: string, body: TrackingUpdatePayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/location`, body),
  recordProof: (id: string, body: ProofPayload, idempotencyKey?: string) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/proof`, body, headersForIdempotency(idempotencyKey)),
  recordCustody: (id: string, body: ChainOfCustodyPayload) => apiClient.post<Record<string, unknown>>(`${BASE}/deliveries/${id}/custody`, body),

  // Fleet & couriers
  listFleet: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/fleet`),
  getAsset: (id: string) => apiClient.get<Record<string, unknown>>(`${BASE}/fleet/${id}`),
  createAsset: (body: Record<string, unknown>) => apiClient.post<Record<string, unknown>>(`${BASE}/fleet`, body),
  updateAsset: (id: string, body: Record<string, unknown>) => apiClient.patch<Record<string, unknown>>(`${BASE}/fleet/${id}`, body),

  listCouriers: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/couriers`),
  getCourier: (id: string) => apiClient.get<Record<string, unknown>>(`${BASE}/couriers/${id}`),
  createCourier: (body: Record<string, unknown>) => apiClient.post<Record<string, unknown>>(`${BASE}/couriers`, body),
  updateCourier: (id: string, body: Record<string, unknown>) => apiClient.patch<Record<string, unknown>>(`${BASE}/couriers/${id}`, body),

  // Policies & integrations
  listPolicies: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/policies`),
  createPolicy: (body: Record<string, unknown>) => apiClient.post<Record<string, unknown>>(`${BASE}/policies`, body),
  updatePolicy: (code: string, body: Record<string, unknown>) => apiClient.patch<Record<string, unknown>>(`${BASE}/policies/${code}`, body),
  listIntegrations: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/integrations`),
  createIntegration: (body: Record<string, unknown>) => apiClient.post<Record<string, unknown>>(`${BASE}/integrations`, body),

  // Autonomous missions
  listMissions: () => apiClient.get<{ data: Array<Record<string, unknown>> }>(`${BASE}/autonomous-missions`),
  getMission: (id: string) => apiClient.get<Record<string, unknown>>(`${BASE}/autonomous-missions/${id}`),
  createMission: (body: Record<string, unknown>) => apiClient.post<Record<string, unknown>>(`${BASE}/autonomous-missions`, body),
  cancelMission: (id: string, body?: { reason?: string }) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/autonomous-missions/${id}/cancel`, body ?? {}),
};
