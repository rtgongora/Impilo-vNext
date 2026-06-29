/**
 * Sovereign service branding registry — canonical logos, names, and domains
 * for native Health OS services surfaced through the unified Experience shell.
 *
 * Logo assets live at /brand/services/<slug>-logo.png (Next.js public folder).
 */

export type ServiceBrandingSlug =
  | "butano"
  | "fundo"
  | "indawo"
  | "madi"
  | "msika"
  | "mushex"
  | "mvumo"
  | "ndila"
  | "nhume"
  | "nompilo"
  | "patient-safety"
  | "rito"
  | "simba"
  | "tshepo"
  | "tuso"
  | "ubomi"
  | "varapi"
  | "vito"
  | "zibo";

export interface ServiceBrandingEntry {
  slug: ServiceBrandingSlug;
  name: string;
  logo: string;
  description: string;
  domain: string;
  /** Lucide icon name used when logo fails to load */
  fallbackIcon: string;
  /** Alternate keys for slug resolution (app codes, tile ids, paths) */
  aliases: string[];
}

export const SERVICE_BRANDING: Record<ServiceBrandingSlug, ServiceBrandingEntry> = {
  butano: {
    slug: "butano",
    name: "Butano",
    logo: "/brand/services/butano-logo.png",
    description: "Shared Health Record / Clinical Data Exchange",
    domain: "Clinical",
    fallbackIcon: "FileHeart",
    aliases: ["butano", "butano-service", "shr", "shared-health-record", "app-butano"],
  },
  fundo: {
    slug: "fundo",
    name: "Fundo",
    logo: "/brand/services/fundo-logo.png",
    description: "Learning and Training Hub",
    domain: "Learning & Workforce",
    fallbackIcon: "GraduationCap",
    aliases: ["fundo", "learning", "impilo-fundo", "impilo fundo", "lms", "training"],
  },
  indawo: {
    slug: "indawo",
    name: "Indawo",
    logo: "/brand/services/indawo-logo.png",
    description: "Public Health Sites and Places",
    domain: "Public Health",
    fallbackIcon: "MapPin",
    aliases: ["indawo", "indawo-service", "site-registry", "public-health-sites"],
  },
  madi: {
    slug: "madi",
    name: "Madi",
    logo: "/brand/services/madi-logo.png",
    description: "Blood Transfusion and Blood Bank Service",
    domain: "Clinical",
    fallbackIcon: "Droplet",
    aliases: ["madi", "madi-service", "blood", "transfusion", "app-madi", "app-madi-donor"],
  },
  "patient-safety": {
    slug: "patient-safety",
    name: "Patient Safety",
    logo: "/brand/services/patient-safety-logo.png",
    description: "Pharmacovigilance — adverse drug reactions, AEFI, and serious adverse event reporting to MCAZ",
    domain: "Public Health",
    fallbackIcon: "ShieldAlert",
    aliases: [
      "patient-safety",
      "patient safety",
      "patient-safety-service",
      "pharmacovigilance",
      "adverse-event",
      "adverse event",
      "adr",
      "aefi",
      "sae",
      "side-effect",
      "side effect",
      "mcaz",
    ],
  },
  rito: {
    slug: "rito",
    name: "Rito",
    logo: "/brand/services/rito-logo.png",
    description: "Client voice, service quality, client safety, assurance and accountable improvement",
    domain: "Quality, Safety & Client Voice",
    fallbackIcon: "ShieldCheck",
    aliases: [
      "rito",
      "rito-service",
      "rito-quality-safety-service",
      "quality",
      "safety",
      "client-voice",
      "client voice",
      "feedback",
      "complaint",
      "complaints",
      "compliment",
      "suggestion",
      "grievance",
      "satisfaction",
      "survey",
      "incident",
      "near-miss",
      "capa",
      "qi",
      "audit-quality",
      "supervision",
      "assurance",
      "app-rito",
    ],
  },
  msika: {
    slug: "msika",
    name: "Msika",
    logo: "/brand/services/msika-logo.png",
    description: "Product and Service Catalogue",
    domain: "Enterprise & Commerce",
    fallbackIcon: "ShoppingBag",
    aliases: ["msika", "msika-service", "marketplace", "app-marketplace", "commerce"],
  },
  mushex: {
    slug: "mushex",
    name: "MusheX",
    logo: "/brand/services/mushex-logo.png",
    description: "Payments and Remittances",
    domain: "Finance",
    fallbackIcon: "Wallet",
    aliases: ["mushex", "mushex-service", "mushex-platform", "payments", "remittance"],
  },
  mvumo: {
    slug: "mvumo",
    name: "Mvumo",
    logo: "/brand/services/mvumo-logo.png",
    description: "Consent, Approvals and Authorisation",
    domain: "Trust",
    fallbackIcon: "ShieldCheck",
    aliases: ["mvumo", "consent", "authorisation", "authorization", "digital-consent"],
  },
  ndila: {
    slug: "ndila",
    name: "Ndila",
    logo: "/brand/services/ndila-logo.png",
    description: "Maps and Geospatial Intelligence",
    domain: "Data & Intelligence",
    fallbackIcon: "Map",
    aliases: ["ndila", "geospatial", "maps", "app-ndila"],
  },
  nhume: {
    slug: "nhume",
    name: "Nhume",
    logo: "/brand/services/nhume-logo.png",
    description: "Dispatch, Delivery and Messenger Service",
    domain: "Enterprise & Logistics",
    fallbackIcon: "Truck",
    aliases: ["nhume", "nhume-service", "dispatch", "logistics", "courier", "app-nhume"],
  },
  nompilo: {
    slug: "nompilo",
    name: "Nompilo",
    logo: "/brand/services/nompilo-logo.png",
    description: "Intelligent Interactive Persona",
    domain: "Experience",
    fallbackIcon: "Sparkles",
    aliases: ["nompilo", "ask", "ask-impilo", "ask_impilo", "app-ask", "guidance", "assistant"],
  },
  simba: {
    slug: "simba",
    name: "Simba",
    logo: "/brand/services/simba-logo.png",
    description: "Wellness",
    domain: "Wellness & Prevention",
    fallbackIcon: "Heart",
    aliases: ["simba", "wellness", "wellness-service", "prevention"],
  },
  tshepo: {
    slug: "tshepo",
    name: "Tshepo",
    logo: "/brand/services/tshepo-logo.png",
    description: "Trust, Identity, Consent and Access",
    domain: "Trust",
    fallbackIcon: "Shield",
    aliases: ["tshepo", "tshepo-service", "trust", "authz", "policy", "audit"],
  },
  tuso: {
    slug: "tuso",
    name: "Tuso",
    logo: "/brand/services/tuso-logo.png",
    description: "Facility Registry",
    domain: "Registry",
    fallbackIcon: "Building2",
    aliases: ["tuso", "tuso-service", "facility-registry", "facilities"],
  },
  ubomi: {
    slug: "ubomi",
    name: "Ubomi",
    logo: "/brand/services/ubomi-logo.png",
    description: "Civil Registration and Vital Events",
    domain: "Registry",
    fallbackIcon: "Baby",
    aliases: ["ubomi", "ubomi-service", "crvs", "civil-registration", "vital-events"],
  },
  varapi: {
    slug: "varapi",
    name: "Varapi",
    logo: "/brand/services/varapi-logo.png",
    description: "Provider Registry",
    domain: "Registry",
    fallbackIcon: "UserCheck",
    aliases: ["varapi", "varapi-service", "provider-registry", "providers"],
  },
  vito: {
    slug: "vito",
    name: "Vito",
    logo: "/brand/services/vito-logo.png",
    description: "Client Registry",
    domain: "Registry",
    fallbackIcon: "Users",
    aliases: ["vito", "vito-service", "client-registry", "mpi", "health-id", "ops_vito", "app-operations-vito"],
  },
  zibo: {
    slug: "zibo",
    name: "Zibo",
    logo: "/brand/services/zibo-logo.png",
    description: "Terminology Service",
    domain: "Registry",
    fallbackIcon: "BookOpen",
    aliases: ["zibo", "zibo-service", "terminology", "code-systems"],
  },
};

/** Command centre tile id → sovereign service slug (when tile represents a branded service). */
export const COMMAND_CENTRE_TILE_SLUGS: Record<string, ServiceBrandingSlug> = {
  vito: "vito",
  varapi: "varapi",
  tuso: "tuso",
  indawo: "indawo",
  tshepo: "tshepo",
  butano: "butano",
  zibo: "zibo",
  ubomi: "ubomi",
  "nompilo-ask": "nompilo",
  fundo: "fundo",
  marketplace: "msika",
  mushex: "mushex",
  ndila: "ndila",
  nhume: "nhume",
  "wellness-hub": "simba",
};

/** Longest-prefix route → sovereign service slug (PageShell auto-branding). */
export const ROUTE_SERVICE_SLUG_PREFIXES: ReadonlyArray<{
  prefix: string;
  slug: ServiceBrandingSlug;
}> = [
  { prefix: "/work/system-admin/tshepo", slug: "tshepo" },
  { prefix: "/public-health/site-registry", slug: "indawo" },
  { prefix: "/admin/consent", slug: "tshepo" },
  { prefix: "/finance/mushex-platform", slug: "mushex" },
  { prefix: "/operations/butano", slug: "butano" },
  { prefix: "/operations/vito", slug: "vito" },
  { prefix: "/registry/trust", slug: "tshepo" },
  { prefix: "/registry/mvumo", slug: "mvumo" },
  { prefix: "/registry/providers", slug: "varapi" },
  { prefix: "/registry/facilities", slug: "tuso" },
  { prefix: "/registry/terminology", slug: "zibo" },
  { prefix: "/learning", slug: "fundo" },
  { prefix: "/wellness", slug: "simba" },
  { prefix: "/marketplace", slug: "msika" },
  { prefix: "/nhume", slug: "nhume" },
  { prefix: "/ndila", slug: "ndila" },
  { prefix: "/madi", slug: "madi" },
  { prefix: "/rito", slug: "rito" },
  { prefix: "/my-life/feedback", slug: "rito" },
  { prefix: "/work/facility/rito", slug: "rito" },
  { prefix: "/work/above-site/rito", slug: "rito" },
  { prefix: "/ubomi", slug: "ubomi" },
  { prefix: "/ask", slug: "nompilo" },
];

export type ServiceSurfaceStatus = "surfaced" | "partial" | "no_dedicated_route";

export interface ServiceSurfaceCoverage {
  status: ServiceSurfaceStatus;
  primaryRoutes?: string[];
  notes?: string;
}

/** Where each sovereign service appears in the UI today (or why not). */
export const SERVICE_SURFACE_COVERAGE: Record<ServiceBrandingSlug, ServiceSurfaceCoverage> = {
  butano: {
    status: "surfaced",
    primaryRoutes: ["/operations/butano", "/queue/search"],
    notes: "SHR clinical chart flows use Butano via queue search; ops hub at /operations/butano.",
  },
  fundo: {
    status: "surfaced",
    primaryRoutes: ["/learning", "/learning/studio", "/learning/admin"],
  },
  indawo: {
    status: "surfaced",
    primaryRoutes: ["/public-health/site-registry", "/public-health?tab=sites"],
    notes: "Premises registry; also probed from /registry/intake.",
  },
  madi: { status: "surfaced", primaryRoutes: ["/madi", "/madi/donor"] },
  msika: { status: "surfaced", primaryRoutes: ["/marketplace", "/registry/products"] },
  mushex: { status: "surfaced", primaryRoutes: ["/finance/mushex-platform", "/finance"] },
  mvumo: { status: "surfaced", primaryRoutes: ["/registry/mvumo", "/consent"] },
  ndila: { status: "surfaced", primaryRoutes: ["/ndila"] },
  nhume: { status: "surfaced", primaryRoutes: ["/nhume", "/operations/dispatch"] },
  nompilo: { status: "surfaced", primaryRoutes: ["/ask", "/intelligence"] },
  "patient-safety": {
    status: "surfaced",
    primaryRoutes: ["/work/patient-safety", "/work/patient-safety/new"],
    notes: "Pharmacovigilance / patient-safety reporting surface.",
  },
  rito: {
    status: "surfaced",
    primaryRoutes: ["/rito", "/my-life/feedback", "/work/facility/rito", "/work/above-site/rito"],
    notes: "Quality, Safety & Client Voice — client feedback, safety incidents, audits, CAPA/QI, dashboards.",
  },
  simba: { status: "surfaced", primaryRoutes: ["/wellness"] },
  tshepo: {
    status: "surfaced",
    primaryRoutes: ["/registry/trust", "/work/system-admin/tshepo", "/admin/consent"],
    notes: "Trust layer spans registry trust hub, admin consent, and system-admin Tshepo section.",
  },
  tuso: { status: "surfaced", primaryRoutes: ["/registry/facilities"] },
  ubomi: { status: "surfaced", primaryRoutes: ["/ubomi"] },
  varapi: { status: "surfaced", primaryRoutes: ["/registry/providers"] },
  vito: { status: "surfaced", primaryRoutes: ["/operations/vito", "/registry/clients", "/id-services"] },
  zibo: { status: "surfaced", primaryRoutes: ["/registry/terminology"] },
};

/** Infer sovereign service slug from the current pathname (longest prefix wins). */
export function inferServiceSlugFromPath(
  pathname: string | null | undefined,
): ServiceBrandingSlug | undefined {
  if (!pathname) return undefined;
  const normalized = pathname.split("?")[0].replace(/\/$/, "") || "/";
  const sorted = [...ROUTE_SERVICE_SLUG_PREFIXES].sort((a, b) => b.prefix.length - a.prefix.length);
  for (const { prefix, slug } of sorted) {
    if (normalized === prefix || normalized.startsWith(`${prefix}/`)) {
      return slug;
    }
  }
  return undefined;
}

const ALIAS_INDEX: Map<string, ServiceBrandingSlug> = (() => {
  const map = new Map<string, ServiceBrandingSlug>();
  for (const entry of Object.values(SERVICE_BRANDING)) {
    map.set(entry.slug, entry.slug);
    for (const alias of entry.aliases) {
      map.set(alias.toLowerCase(), entry.slug);
    }
  }
  return map;
})();

function normalizeKey(raw: string): string {
  return raw.trim().toLowerCase().replace(/\s+/g, "-");
}

/** Resolve a sovereign service slug from heterogeneous UI identifiers. */
export function resolveServiceSlug(
  hint: string | null | undefined,
): ServiceBrandingSlug | undefined {
  if (!hint) return undefined;
  const key = normalizeKey(hint);
  const direct = ALIAS_INDEX.get(key);
  if (direct) return direct;

  // Path-based hints: /operations/vito/... → vito
  const pathMatch = key.match(/(?:^|\/)((?:operations|registry|finance|learning|wellness|nhume|ndila|madi|ubomi|ask|public-health)[/-]?([a-z0-9-]+)?)/);
  if (pathMatch?.[2]) {
    const segment = ALIAS_INDEX.get(pathMatch[2]);
    if (segment) return segment;
  }

  for (const [alias, slug] of ALIAS_INDEX.entries()) {
    if (key.includes(alias) && alias.length >= 4) return slug;
  }

  return undefined;
}

export function getServiceBranding(
  slug: string | null | undefined,
): ServiceBrandingEntry | undefined {
  const resolved = resolveServiceSlug(slug);
  return resolved ? SERVICE_BRANDING[resolved] : undefined;
}

export function listSovereignServices(): ServiceBrandingEntry[] {
  return Object.values(SERVICE_BRANDING);
}
