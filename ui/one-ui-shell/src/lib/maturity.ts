import registryMaturity from "@/generated/registry-maturity.json";

export type RegistryWiringStatus = "wired" | "partial" | "unknown-or-partial" | "not-wired" | string;
export type UiMaturityLabel = "live" | "partial" | "not_wired" | "blocked";

export function mapRegistryWiringToUiMaturity(status: RegistryWiringStatus | undefined): UiMaturityLabel {
  switch (status) {
    case "wired":
      return "live";
    case "partial":
      return "partial";
    case "not-wired":
      return "not_wired";
    case "unknown-or-partial":
    default:
      return "partial";
  }
}

export function registryMaturityForService(serviceId: string): RegistryWiringStatus | undefined {
  const services = registryMaturity.services as Record<string, RegistryWiringStatus>;
  return services[serviceId];
}

export function uiMaturityForService(serviceId: string): UiMaturityLabel {
  return mapRegistryWiringToUiMaturity(registryMaturityForService(serviceId));
}

/** Keyword → registry service id hints for command-centre tile overlays. */
export const TILE_SERVICE_HINTS: Record<string, string> = {
  varapi: "varapi-service",
  tuso: "tuso-service",
  ndila: "ndila-service",
  nhume: "nhume-service",
  dispatch: "dispatch-service",
  pharmacy: "pharmacy-service",
  inpatient: "inpatient-service",
  wellness: "wellness-service",
  learning: "learning-service",
  fundo: "learning-service",
};

export function serviceHintForTile(tileId: string, keywords: string[] = []): string | undefined {
  const haystack = `${tileId} ${keywords.join(" ")}`.toLowerCase();
  for (const [keyword, serviceId] of Object.entries(TILE_SERVICE_HINTS)) {
    if (haystack.includes(keyword)) return serviceId;
  }
  return undefined;
}
