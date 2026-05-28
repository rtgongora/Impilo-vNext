export type TileIntegrationHealth =
  | "checking"
  | "hub_down"
  | "routed"
  | "not_in_hub"
  | "not_applicable";

export function normalizeIntegrationRouteRows(payload: unknown): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) {
    return payload.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
  }
  if (typeof payload === "object") {
    const root = payload as Record<string, unknown>;
    if (Array.isArray(root.items)) {
      return root.items.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
    }
    if (Array.isArray(root.content)) {
      return root.content.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
    }
  }
  return [];
}

export function rowMatchesKeywords(row: Record<string, unknown>, keywords: string[]): boolean {
  const blob = JSON.stringify(row).toLowerCase();
  return keywords.some((keyword) => blob.includes(keyword.toLowerCase()));
}

export function resolveTileIntegrationHealth(
  keywords: string[] | undefined,
  routes: Record<string, unknown>[],
  hubAvailable: boolean,
  isLoading: boolean,
): TileIntegrationHealth {
  if (!keywords?.length) return "not_applicable";
  if (isLoading) return "checking";
  if (!hubAvailable) return "hub_down";
  if (routes.some((row) => rowMatchesKeywords(row, keywords))) return "routed";
  return "not_in_hub";
}

export function tileIntegrationHealthLabel(state: TileIntegrationHealth): string {
  switch (state) {
    case "checking":
      return "Hub probe…";
    case "hub_down":
      return "Hub unavailable";
    case "routed":
      return "Hub route live";
    case "not_in_hub":
      return "Not in hub registry";
    default:
      return "";
  }
}
