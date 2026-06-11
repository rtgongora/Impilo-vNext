export type MobileSurface =
  | "citizen"
  | "provider"
  | "facility"
  | "admin"
  | "publicHealth"
  | "enterprise";

export type MobileAuthContext =
  | "client"
  | "provider"
  | "facility"
  | "aboveSite"
  | "admin"
  | "publicHealth"
  | "enterprise";

export type MobileServiceStatus = "native" | "deepLink" | "planned" | "blocked";

export type MobileServiceWiringStatus =
  | "fullyWired"
  | "partiallyWired"
  | "deepLinked"
  | "backendMissing"
  | "blocked";

export type CanonicalServiceSlug =
  | "vito"
  | "varapi"
  | "tuso"
  | "tshepo"
  | "butano"
  | "ubomi"
  | "zibo"
  | "msika"
  | "indawo"
  | "pct"
  | "costa"
  | "mushex"
  | "oros"
  | "simba"
  | "ndila"
  | "nhume"
  | "fundo"
  | "nompilo"
  | "madi"
  | "pacs"
  | "live";

export interface MobileSovereignService {
  slug: CanonicalServiceSlug;
  name: string;
  domain: string;
  description: string;
  icon?: string;
  logoPath?: string;
  aliases?: string[];
  surfaces: MobileSurface[];
  mobileStatus: MobileServiceStatus;
  requiredAuthContext?: MobileAuthContext[];
}

export interface MobileServiceWiring {
  slug: CanonicalServiceSlug;
  name: string;
  mobileStatus: MobileServiceStatus;
  wiringStatus: MobileServiceWiringStatus;
  apiClients: string[];
  backendServices: string[];
  requiredAuthContext: MobileAuthContext[];
  citizenRoutes?: string[];
  providerRoutes?: string[];
  deepLinks?: string[];
  supportsLoadingState: boolean;
  supportsEmptyState: boolean;
  supportsErrorState: boolean;
  supportsUnauthorizedState: boolean;
  supportsOfflineState: boolean;
  knownGaps?: string[];
}
