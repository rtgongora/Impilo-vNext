/**
 * Canonical domain API client entry — BFF-first HTTP + hook file map.
 *
 * Feature screens should import hooks from `@/hooks/queries/*` directly.
 * This module documents the sweep client-name → hook mapping and exports only
 * the shared HTTP client.
 *
 * @see docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md
 */

export { apiClient, type ApiResponse, type ApiError } from "@/lib/api-client";

/** Domain hook locations (import from these paths in feature code). */
export const DOMAIN_CLIENT_MAP = {
  tshepoClient: "@/hooks/queries/useTrustAdmin.ts",
  vitoClient: "@/hooks/queries/useIdentity.ts",
  varapiClient: "@/hooks/queries/useRegistry.ts",
  tusoClient: "@/hooks/queries/useFacilities.ts",
  butanoClient: "@/hooks/queries/useSummary.ts",
  ziboClient: "ui/zibo-web (sovereign /v1/*)",
  ubomiClient: "@/hooks/queries/useUbomiRegistry.ts",
  msikaClient: "@/hooks/queries/useMsikaGovernance.ts",
  msikaFlowClient: "@/hooks/queries/useCommerceFlow.ts",
  mushexClient: "@/hooks/queries/useMusheWallet.ts",
  costaClient: "@/hooks/queries/useCostaIntel.ts",
  nompiloClient: "@/hooks/queries/useGuidance.ts",
  ndilaClient: "@/lib/ndila/ndila-client.ts",
  nhumeClient: "@/lib/nhume.ts",
  integrationClient: "@/hooks/queries/useIntegrationHub.ts",
  commsHubClient: "@/hooks/queries/useOmnichannel.ts",
  telemedicineClient: "@/hooks/queries/useTelemedicine.ts",
  publicHealthClient: "@/hooks/queries/usePublicHealth.ts",
  intelligenceClient: "@/hooks/useIntelligence.ts",
  fundoClient: "@/hooks/queries/useFundoLms.ts",
  notificationClient: "@/hooks/queries/useNotifications.ts",
  workflowClient: "@/hooks/queries/useDispatchOps.ts",
  adminClient: "@/hooks/queries/useAdminUsers.ts",
  socialClient: "@/hooks/queries/useSocial.ts",
} as const;
