/**
 * Health OS Launcher + Capability Marketplace hooks.
 *
 * Launcher list/state/access uses {@code experience-bff /internal/v1/launcher/apps/**}
 * (HealthOsLauncherController). Marketplace catalogue/activation paths remain on
 * {@code /internal/v1/marketplace/*}.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type LauncherAppState =
  | "INSTALLED"
  | "AVAILABLE"
  | "REQUEST_ACCESS"
  | "PENDING_APPROVAL"
  | "NOT_AVAILABLE_AT_FACILITY"
  | "REQUIRES_CONFIGURATION"
  | "TEMPORARILY_UNAVAILABLE"
  | "SUSPENDED"
  | "DEPRECATED";

export interface LauncherApp {
  id: string;
  itemCode: string;
  name: string;
  description: string;
  type: "APP" | "AI_SKILL";
  category: string;
  iconRef?: string | null;
  state: LauncherAppState;
  reason?: string | null;
  launchUrl?: string | null;
  rolesAllowed: string[];
  installationId?: string | null;
}

interface LauncherAppsBffResponse {
  apps?: Array<Record<string, unknown>>;
  actor?: Record<string, unknown>;
  generatedAt?: string;
}

export type MarketplaceItemType =
  | "APP"
  | "EXTENSION"
  | "PLUGIN"
  | "CONNECTOR"
  | "ADAPTER"
  | "WORKFLOW_PACK"
  | "CONTENT_PACK"
  | "AI_SKILL"
  | "DEVICE_INTEGRATION";

export interface MarketplaceCapability {
  id: string;
  itemCode: string;
  name: string;
  description: string;
  type: MarketplaceItemType;
  category: string;
  publisherId: string;
  publisherName: string;
  version: string;
  compatibilityVersion: string;
  supportedEnvironments: string[];
  visibility: string;
  iconRef?: string | null;
  documentationUrl?: string | null;
  manifestRef: string;
  securityClassification: string;
  dataProtectionClassification: string;
  clinicalSafetyClassification?: string | null;
  approvalStatus: string;
  certificationStatus?: string | null;
  regulatoryStatus?: string | null;
  pricingModel?: string | null;
  licensingModel?: string | null;
  requiredScopes: string[];
  requiredEvents: string[];
  requiredApis: string[];
  requiredDataCategories: string[];
  requiredPermissions: string[];
  facilityTypeWhitelist: string[];
  rolesAllowed: string[];
  consentRequirements: string[];
  healthStatus: string;
  lastUpdated: string;
  deprecationDate?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ActivationRequest {
  id: string;
  itemId: string;
  requesterActorId: string;
  requesterTenantId: string;
  requesterFacilityId?: string | null;
  requesterWorkspaceId?: string | null;
  requesterProgrammeId?: string | null;
  purposeOfUse: string;
  justification: string;
  status: "REQUESTED" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "CHANGES_REQUESTED";
  decidedAt?: string | null;
  decisionReason?: string | null;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Installation {
  id: string;
  itemId: string;
  itemVersion: string;
  tenantId: string;
  facilityIds: string[];
  workspaceIds: string[];
  programmeIds: string[];
  rolesEnabled: string[];
  configurationJson?: string | null;
  integrationContractId?: string | null;
  externalAppId?: string | null;
  lifecycle: "INSTALLED" | "ACTIVE" | "SUSPENDED" | "DEPRECATED";
  installedBy: string;
  installedAt: string;
  configuredAt?: string | null;
  activatedAt?: string | null;
  suspendedAt?: string | null;
  suspendedReason?: string | null;
  lastHealthCheckAt?: string | null;
  healthStatus: string;
  createdAt: string;
  updatedAt: string;
}

export interface EventCatalogueEntry {
  id: string;
  topic: string;
  description?: string | null;
  classification: "INTERNAL_PLATFORM" | "EXTERNALLY_PUBLISHABLE";
  sensitivityTier: string;
  ownerServiceId: string;
  schemaRef?: string | null;
  schemaVersion?: number | null;
  partnerPublishable: boolean;
  containsPii: boolean;
  containsPhi: boolean;
  deprecated: boolean;
  introducedAt?: string | null;
  updatedAt: string;
}

export interface ExternalApplication {
  id: string;
  appCode: string;
  name: string;
  publisherName: string;
  status: string;
  environment: string;
  integrationCategory: string;
  riskClassification: string;
  authenticationMethod: string;
  oauthClientId?: string | null;
  allowedTenants: string[];
  allowedFacilities: string[];
  rateLimitRpm?: number | null;
  registeredAt: string;
  approvedAt?: string | null;
  suspendedAt?: string | null;
  suspendedReason?: string | null;
}

// ── Hooks ────────────────────────────────────────────────────────────────────

export function useHealthOsLauncher(opts: { facilityId?: string; roles?: string[] }) {
  const facilityId = opts.facilityId ?? "";
  const rolesParam = (opts.roles ?? []).join(",");
  return useQuery<LauncherApp[]>({
    queryKey: ["health-os-launcher", { facilityId, rolesParam }],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (facilityId) params.set("facilityId", facilityId);
      if (rolesParam) params.set("roles", rolesParam);
      const query = params.toString();
      const response = await apiClient.get<ApiResponse<LauncherAppsBffResponse> | LauncherAppsBffResponse>(
        `/internal/v1/launcher/apps${query ? `?${query}` : ""}`,
      );
      const body = unwrap(response);
      const rawApps = Array.isArray(body)
        ? body
        : (body as LauncherAppsBffResponse).apps ?? [];
      return rawApps
        .filter((raw) => raw.systemAppFlag !== true)
        .map(mapLauncherAppFromBff);
    },
  });
}

export function useMarketplaceCapabilities(filter?: {
  type?: MarketplaceItemType;
  category?: string;
  publisherId?: string;
}) {
  return useQuery<MarketplaceCapability[]>({
    queryKey: ["marketplace-capabilities", filter ?? {}],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filter?.type) params.set("type", filter.type);
      if (filter?.category) params.set("category", filter.category);
      if (filter?.publisherId) params.set("publisherId", filter.publisherId);
      const query = params.toString();
      const response = await apiClient.get<ApiResponse<MarketplaceCapability[]> | MarketplaceCapability[]>(
        `/internal/v1/marketplace/items${query ? `?${query}` : ""}`,
      );
      return unwrap(response);
    },
  });
}

export function useMarketplaceCapability(id: string) {
  return useQuery<MarketplaceCapability>({
    queryKey: ["marketplace-capability", id],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<MarketplaceCapability> | MarketplaceCapability>(
        `/internal/v1/marketplace/items/${encodeURIComponent(id)}`,
      );
      return unwrap(response);
    },
    enabled: !!id,
  });
}

export function useActivationRequests(status?: string) {
  return useQuery<ActivationRequest[]>({
    queryKey: ["marketplace-activation-requests", status ?? "all"],
    queryFn: async () => {
      const query = status ? `?status=${encodeURIComponent(status)}` : "";
      const response = await apiClient.get<ApiResponse<ActivationRequest[]> | ActivationRequest[]>(
        `/internal/v1/marketplace/activation-requests${query}`,
      );
      return unwrap(response);
    },
  });
}

export function useRequestActivation() {
  const queryClient = useQueryClient();
  return useMutation<
    ActivationRequest,
    unknown,
    {
      itemId: string;
      purposeOfUse: string;
      justification: string;
      facilityId?: string;
      workspaceId?: string;
      programmeId?: string;
      requestedConfigurationJson?: string;
    }
  >({
    mutationFn: async (payload) => {
      const response = await apiClient.post<ApiResponse<ActivationRequest> | ActivationRequest>(
        `/internal/v1/marketplace/activation-requests`,
        payload,
      );
      return unwrap(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-activation-requests"] });
      queryClient.invalidateQueries({ queryKey: ["health-os-launcher"] });
    },
  });
}

export function useDecideActivation() {
  const queryClient = useQueryClient();
  return useMutation<
    ActivationRequest,
    unknown,
    { requestId: string; decision: "APPROVED" | "REJECTED" | "CHANGES_REQUESTED"; reason?: string }
  >({
    mutationFn: async ({ requestId, ...body }) => {
      const response = await apiClient.post<ApiResponse<ActivationRequest> | ActivationRequest>(
        `/internal/v1/marketplace/activation-requests/${encodeURIComponent(requestId)}/decision`,
        body,
      );
      return unwrap(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-activation-requests"] });
      queryClient.invalidateQueries({ queryKey: ["marketplace-installations"] });
      queryClient.invalidateQueries({ queryKey: ["health-os-launcher"] });
    },
  });
}

export function useInstallations(lifecycle?: Installation["lifecycle"]) {
  return useQuery<Installation[]>({
    queryKey: ["marketplace-installations", lifecycle ?? "all"],
    queryFn: async () => {
      const query = lifecycle ? `?lifecycle=${encodeURIComponent(lifecycle)}` : "";
      const response = await apiClient.get<ApiResponse<Installation[]> | Installation[]>(
        `/internal/v1/marketplace/installations${query}`,
      );
      return unwrap(response);
    },
  });
}

export function useConfigureInstallation() {
  const queryClient = useQueryClient();
  return useMutation<
    Installation,
    unknown,
    {
      installationId: string;
      facilityIds?: string[];
      workspaceIds?: string[];
      programmeIds?: string[];
      rolesEnabled?: string[];
      configurationJson?: string;
      integrationContractId?: string;
      externalAppId?: string;
    }
  >({
    mutationFn: async ({ installationId, ...body }) => {
      const response = await apiClient.post<ApiResponse<Installation> | Installation>(
        `/internal/v1/marketplace/installations/${encodeURIComponent(installationId)}/configure`,
        body,
      );
      return unwrap(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-installations"] });
      queryClient.invalidateQueries({ queryKey: ["health-os-launcher"] });
    },
  });
}

export function useActivateInstallation() {
  const queryClient = useQueryClient();
  return useMutation<Installation, unknown, { installationId: string }>({
    mutationFn: async ({ installationId }) => {
      const response = await apiClient.post<ApiResponse<Installation> | Installation>(
        `/internal/v1/marketplace/installations/${encodeURIComponent(installationId)}/activate`,
      );
      return unwrap(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-installations"] });
      queryClient.invalidateQueries({ queryKey: ["health-os-launcher"] });
    },
  });
}

export function useSuspendInstallation() {
  const queryClient = useQueryClient();
  return useMutation<Installation, unknown, { installationId: string; reason: string }>({
    mutationFn: async ({ installationId, reason }) => {
      const response = await apiClient.post<ApiResponse<Installation> | Installation>(
        `/internal/v1/marketplace/installations/${encodeURIComponent(installationId)}/suspend`,
        { reason },
      );
      return unwrap(response);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["marketplace-installations"] });
      queryClient.invalidateQueries({ queryKey: ["health-os-launcher"] });
    },
  });
}

export function useEventCatalogue(opts?: { classification?: string; ownerServiceId?: string }) {
  return useQuery<EventCatalogueEntry[]>({
    queryKey: ["integration-event-catalogue", opts ?? {}],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (opts?.classification) params.set("classification", opts.classification);
      if (opts?.ownerServiceId) params.set("ownerServiceId", opts.ownerServiceId);
      const query = params.toString();
      const response = await apiClient.get<ApiResponse<EventCatalogueEntry[]> | EventCatalogueEntry[]>(
        `/internal/v1/marketplace/integration/event-catalogue${query ? `?${query}` : ""}`,
      );
      return unwrap(response);
    },
  });
}

export function useExternalApplications() {
  return useQuery<ExternalApplication[]>({
    queryKey: ["integration-external-apps"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<ExternalApplication[]> | ExternalApplication[]>(
        `/internal/v1/marketplace/integration/external-apps`,
      );
      return unwrap(response);
    },
  });
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function mapLauncherAppFromBff(raw: Record<string, unknown>): LauncherApp {
  const appCode = String(raw.appCode ?? raw.itemCode ?? raw.id ?? "");
  const href =
    raw.href != null ? String(raw.href) : raw.launchUrl != null ? String(raw.launchUrl) : null;
  const rawState = String(raw.state ?? "AVAILABLE");
  let state = rawState as LauncherAppState;
  if (rawState === "AVAILABLE" && href) {
    state = "INSTALLED";
  }
  return {
    id: String(raw.id ?? appCode),
    itemCode: appCode,
    name: String(raw.name ?? appCode),
    description: String(raw.description ?? ""),
    type: raw.type === "AI_SKILL" ? "AI_SKILL" : "APP",
    category: String(raw.category ?? "marketplace"),
    iconRef:
      raw.icon != null
        ? String(raw.icon)
        : raw.iconRef != null
          ? String(raw.iconRef)
          : null,
    state,
    reason:
      raw.stateExplanation != null
        ? String(raw.stateExplanation)
        : raw.reason != null
          ? String(raw.reason)
          : null,
    launchUrl: href,
    rolesAllowed: Array.isArray(raw.rolesAllowed)
      ? raw.rolesAllowed.map(String)
      : [],
    installationId: raw.installationId != null ? String(raw.installationId) : null,
  };
}

function unwrap<T>(response: ApiResponse<T> | T): T {
  if (response && typeof response === "object" && "data" in (response as Record<string, unknown>)) {
    return (response as ApiResponse<T>).data;
  }
  return response as T;
}
