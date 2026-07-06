/**
 * Four-block doctrine trust profile (IATG Wave-1, WS-D → E2 UI surfacing).
 *
 * GET /api/v1/trust/profile/{healthId} composes four blocks — identity,
 * professional, employment, operational — each citing its own
 * {@code sourceOfRecord} and degrading INDEPENDENTLY. One broken downstream
 * never poisons the other blocks, and nothing is ever fabricated.
 *
 * IMPORTANT: this endpoint returns a RAW ordered map (healthId, generatedAt +
 * the four blocks) — NOT the `{ data, meta }` envelope — so the response is
 * consumed as-is, never `.data`-unwrapped.
 *
 * Contract source: TrustProfileBffController.java:48 +
 * TrustProfileComposer.java:73-205.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

/** Per-block degradation states — the discriminant across every block. */
export type TrustBlockStatus = "OK" | "UNAVAILABLE" | "NO_PROVIDER_LINKED";

interface SourcedBlock {
  /** System of record this block cites — the BFF is never itself the source. */
  sourceOfRecord: string;
}

// ── Identity block (identity-assurance-service) ─────────────────────────────

export type IdentityBlock =
  | (SourcedBlock & { status: "OK"; assurance?: unknown })
  | (SourcedBlock & { status: "UNAVAILABLE" });

// ── Professional block (VARAPI provider registry trust ledger) ──────────────

export type ProfessionalBlock =
  | (SourcedBlock & {
      status: "OK";
      providerId: string;
      trustLevel?: string;
      registryStatus?: string;
      onboardingChannel?: string;
      attempts?: unknown;
    })
  | (SourcedBlock & { status: "UNAVAILABLE"; providerId?: string })
  | (SourcedBlock & { status: "NO_PROVIDER_LINKED"; providerCreationRequired?: boolean });

// ── Employment block (workforce-governance HSC employment) ──────────────────

export interface EmploymentSummary {
  employmentStatus?: string;
  cadre?: string;
  grade?: string;
  postTitle?: string;
  currentPostingProvince?: string;
  currentPostingDistrict?: string;
  currentPostingFacility?: string;
  disciplinaryEmploymentStatus?: string;
  verificationStatus?: string;
  /** First-2-chars + "***" — the raw EC number never leaves governance. */
  maskedEcNumber?: string;
}

export type EmploymentBlock =
  | (SourcedBlock & { status: "OK"; recordCount?: number; records?: EmploymentSummary[] })
  | (SourcedBlock & { status: "UNAVAILABLE" });

// ── Operational block (vashandi work-context read model) ────────────────────

export type OperationalBlock =
  | (SourcedBlock & {
      status: "OK";
      activeAssignmentCount?: number;
      assignments?: unknown[];
      requiresContextChooser?: boolean;
      checkIn?: unknown;
    })
  | (SourcedBlock & { status: "UNAVAILABLE" });

export interface TrustProfile {
  healthId: string;
  generatedAt: string;
  identity: IdentityBlock;
  professional: ProfessionalBlock;
  employment: EmploymentBlock;
  operational: OperationalBlock;
}

const qk = {
  profile: (healthId: string) => ["trust-profile", healthId] as const,
};

/**
 * Load the signed-in person's four-block trust profile.
 *
 * The Health ID is the session person anchor (useAuthStore().user.id); the
 * query stays disabled until authenticated so no request fires without one.
 */
export function useTrustProfile() {
  const { user, isAuthenticated } = useAuthStore();
  const healthId = user?.id ?? "";
  return useQuery<TrustProfile>({
    queryKey: qk.profile(healthId),
    // Raw map — consumed as-is, NEVER `.data`-unwrapped.
    queryFn: async () =>
      apiClient.get<TrustProfile>(`/api/v1/trust/profile/${encodeURIComponent(healthId)}`),
    enabled: isAuthenticated && !!healthId,
    staleTime: 60 * 1000,
    refetchOnWindowFocus: false,
  });
}
