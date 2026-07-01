import type { CanonicalServiceSlug, MobileServiceWiring, MobileServiceWiringStatus } from "./types";

const PREVIEW_WEB_BASE = "http://41.57.127.235";

function wire(
  slug: CanonicalServiceSlug,
  name: string,
  wiringStatus: MobileServiceWiringStatus,
  mobileStatus: MobileServiceWiring["mobileStatus"],
  opts: Partial<Omit<MobileServiceWiring, "slug" | "name" | "wiringStatus" | "mobileStatus">> &
    Pick<MobileServiceWiring, "apiClients" | "backendServices" | "requiredAuthContext">
): MobileServiceWiring {
  return {
    slug,
    name,
    wiringStatus,
    mobileStatus,
    supportsLoadingState: true,
    supportsEmptyState: true,
    supportsErrorState: true,
    supportsUnauthorizedState: true,
    supportsOfflineState: true,
    ...opts,
  };
}

export const MOBILE_SERVICE_WIRING: Record<CanonicalServiceSlug, MobileServiceWiring> = {
  vito: wire("vito", "Vito", "partiallyWired", "native", {
    apiClients: ["clientRegistryService", "healthIdService", "profileService", "citizenRegistrationService"],
    backendServices: ["vito-service", "experience-bff"],
    requiredAuthContext: ["client"],
    citizenRoutes: ["personal/health-id", "auth/sign-up", "auth/assurance"],
    knownGaps: ["Household linking UI partial; dedup score only on assisted registration"],
  }),
  varapi: wire("varapi", "Varapi", "partiallyWired", "native", {
    apiClients: ["identityRegistryService"],
    backendServices: ["varapi-service", "experience-bff"],
    requiredAuthContext: ["provider"],
    providerRoutes: ["professional/profile", "auth/activation"],
    knownGaps: ["Council/licence fields depend on upstream VARAPI payload completeness"],
  }),
  tuso: wire("tuso", "Tuso", "partiallyWired", "native", {
    apiClients: ["facilityService", "registryOperationsService"],
    backendServices: ["tuso-service", "experience-bff"],
    requiredAuthContext: ["provider", "facility"],
    citizenRoutes: ["home/facilities"],
    providerRoutes: ["auth/select-facility", "auth/select-workspace"],
  }),
  tshepo: wire("tshepo", "Tshepo", "partiallyWired", "native", {
    apiClients: ["mobile-trust/headerBuilder", "consentService"],
    backendServices: ["tshepo-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/consent"],
    providerRoutes: ["tools/clinical"],
    knownGaps: ["No direct Tshepo API client — authz enforced at BFF/Envoy"],
  }),
  butano: wire("butano", "Butano", "partiallyWired", "native", {
    apiClients: ["recordsService", "labResultService", "personalHealthService", "healthTimelineService"],
    backendServices: ["butano-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/records", "personal/results", "personal/conditions"],
    providerRoutes: ["patients/summary", "results"],
    knownGaps: ["No direct FHIR client — reads via BFF mobile routes"],
  }),
  ubomi: wire("ubomi", "Ubomi", "fullyWired", "native", {
    apiClients: ["ubomiService"],
    backendServices: ["ubomi-service", "experience-bff"],
    requiredAuthContext: ["client"],
    citizenRoutes: ["marketplace/ubomi-crvs"],
  }),
  zibo: wire("zibo", "Zibo", "partiallyWired", "native", {
    apiClients: ["registryOperationsService"],
    backendServices: ["zibo-service", "experience-bff"],
    requiredAuthContext: ["provider", "admin"],
    providerRoutes: ["tools/admin-registry"],
    knownGaps: ["Citizen-facing terminology lookup not surfaced"],
  }),
  msika: wire("msika", "Msika", "fullyWired", "native", {
    apiClients: ["marketplaceService", "healthOsLauncherService", "cartService"],
    backendServices: ["msika-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["marketplace", "marketplace/health-os-apps"],
    providerRoutes: ["apps/health-os"],
  }),
  indawo: wire("indawo", "Indawo", "fullyWired", "native", {
    apiClients: ["facilityService", "mobile-ndila/ndilaClient"],
    backendServices: ["indawo-service", "experience-bff"],
    requiredAuthContext: ["client", "provider", "publicHealth"],
    citizenRoutes: ["home/facilities"],
    providerRoutes: ["outreach/field-tasks"],
  }),
  pct: wire("pct", "PCT", "partiallyWired", "native", {
    apiClients: ["telehealthService", "appointmentService", "encounterService", "clinicalWorklistService"],
    backendServices: ["pct-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["telehealth", "personal/appointments", "personal/bookings"],
    providerRoutes: ["queue", "patients", "encounter", "tools/telehealth"],
  }),
  costa: wire("costa", "Costa", "partiallyWired", "native", {
    apiClients: ["financeService.fetchPendingCharges", "queueService.fetchCharges"],
    backendServices: ["costa-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/finance"],
    providerRoutes: ["tools/billing", "tools/finance"],
    knownGaps: [
      "Citizen pending charges now live: GET /internal/v1/mobile/citizen/costa/charges/pending (CitizenCostaController → COSTA outstanding bills). Bill-level only — no per-service line/date breakdown yet; quotes/receipts routes still pending (see mobile-costa-bff-contract.md)",
      "Provider reads /internal/v1/mobile/provider/billing/charges via queueService.fetchCharges",
    ],
  }),
  mushex: wire("mushex", "MusheX", "partiallyWired", "native", {
    apiClients: ["walletService", "financeService", "coverageService"],
    backendServices: ["mushex-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/finance", "personal/wallet"],
    providerRoutes: ["tools/finance"],
    knownGaps: ["Claims/coverage detail screens partial"],
  }),
  oros: wire("oros", "Oros", "partiallyWired", "native", {
    apiClients: ["labService", "prescriptionService", "madiService"],
    backendServices: ["oros-service", "experience-bff"],
    requiredAuthContext: ["provider"],
    providerRoutes: ["tools/lab", "tools/pharmacy", "tools/madi-orders"],
    knownGaps: ["No standalone OROS module — orders via lab/pharmacy/MADI BFF routes"],
  }),
  simba: wire("simba", "Simba", "partiallyWired", "native", {
    apiClients: ["wellnessService", "inventoryService"],
    backendServices: ["simba-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/wellness"],
    providerRoutes: ["supervisor/stock", "supervisor/inventory"],
    knownGaps: ["Provider commodity requisition entry partial"],
  }),
  ndila: wire("ndila", "Ndila", "fullyWired", "native", {
    apiClients: ["mobile-ndila/ndilaClient"],
    backendServices: ["ndila-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["home/facilities"],
    providerRoutes: ["outreach/field-tasks"],
  }),
  nhume: wire("nhume", "Nhume", "fullyWired", "native", {
    apiClients: ["nhumeService", "deliveryService", "citizenDeliveryService"],
    backendServices: ["nhume-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["home/track-delivery"],
    providerRoutes: ["courier/assignments"],
  }),
  fundo: wire("fundo", "Fundo", "fullyWired", "native", {
    apiClients: ["fundoLearningService", "learningService"],
    backendServices: ["fundo-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["marketplace/fundo", "personal/fundo"],
    providerRoutes: ["apps/fundo", "tools/fundo-learning"],
  }),
  nompilo: wire("nompilo", "Nompilo", "partiallyWired", "native", {
    apiClients: ["mobile-nompilo/nompiloClient"],
    backendServices: ["llm-orchestration-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["global/nompilo-assistant"],
    providerRoutes: ["global/nompilo-assistant"],
    knownGaps: ["Deterministic offline fallback when LLM gateway unavailable"],
  }),
  madi: wire("madi", "Madi", "fullyWired", "native", {
    apiClients: ["madiService"],
    backendServices: ["madi-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/madi-donor", "madi/drives"],
    providerRoutes: ["tools/madi", "madi/central-bank"],
  }),
  pacs: wire("pacs", "PACS", "partiallyWired", "native", {
    apiClients: ["PACSViewerScreen inline fetch"],
    backendServices: ["pacs-adapter", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/results"],
    providerRoutes: ["tools/pacs"],
    deepLinks: [`${PREVIEW_WEB_BASE}/clinical/imaging`],
    knownGaps: ["Native DICOM viewer not implemented — deep-link handoff for full viewer"],
  }),
  live: wire("live", "Impilo Live", "fullyWired", "native", {
    apiClients: ["impiloLiveService"],
    backendServices: ["live-service", "experience-bff"],
    requiredAuthContext: ["client", "provider"],
    citizenRoutes: ["personal/live", "live/discover"],
    providerRoutes: ["tools/live", "live/hub"],
  }),
};

export function getServiceWiring(slug: CanonicalServiceSlug): MobileServiceWiring {
  return MOBILE_SERVICE_WIRING[slug];
}

export function listWiringForContext(
  context: "citizen" | "provider"
): MobileServiceWiring[] {
  return (Object.keys(MOBILE_SERVICE_WIRING) as CanonicalServiceSlug[])
    .map((slug) => MOBILE_SERVICE_WIRING[slug])
    .filter((w) => {
      const routes = context === "citizen" ? w.citizenRoutes : w.providerRoutes;
      return routes && routes.length > 0;
    });
}
