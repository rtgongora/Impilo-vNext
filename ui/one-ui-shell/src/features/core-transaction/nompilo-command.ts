import type {
  NompiloCommandIntent,
  NompiloCommandResult,
  NompiloSearchSource,
  NompiloToolPermission,
} from "./types";

export function classifyNompiloCommandIntent(query: string): NompiloCommandIntent {
  const normalized = query.toLowerCase();
  if (normalized.includes("service")) return "FIND_SERVICE";
  if (normalized.includes("provider") || normalized.includes("doctor")) return "FIND_PROVIDER";
  if (
    normalized.includes("commodity") ||
    normalized.includes("stock") ||
    normalized.includes("supplement") ||
    normalized.includes("availability")
  ) {
    return "FIND_COMMODITY";
  }
  if (normalized.includes("wellness") || normalized.includes("marathon") || normalized.includes("campaign")) {
    return "FIND_WELLNESS_EVENT";
  }
  if (normalized.includes("claim") || normalized.includes("payment")) return "ASK_PAYMENT_OR_CLAIM_HELP";
  if (normalized.includes("fundo") || normalized.includes("module") || normalized.includes("quiz")) {
    return normalized.includes("quiz") ? "REQUEST_QUIZ" : "ASK_FUNDO_HELP";
  }
  if (normalized.includes("dashboard")) return "REQUEST_DASHBOARD";
  if (normalized.includes("report")) return "REQUEST_REPORT";
  if (normalized.includes("briefing")) return "REQUEST_LOGIN_BRIEFING";
  if (
    normalized.includes("help") ||
    normalized.includes("support") ||
    normalized.includes("ticket") ||
    normalized.includes("escalat") ||
    normalized.includes("assistance")
  ) {
    return "REQUEST_SUPPORT";
  }
  if (normalized.includes("dictate") || normalized.includes("voice")) return "DICTATE_NEED";
  return "ASK_WORKFLOW_HELP";
}

export function filterSearchSourcesByPermissions(
  sources: NompiloSearchSource[],
  permissions: NompiloToolPermission[],
): NompiloSearchSource[] {
  const allowed = new Set(
    permissions.filter((permission) => permission.allowed).map((permission) => permission.source),
  );
  return sources.filter((source) => allowed.has(source));
}

export function isNompiloSourceOfTruth(result: NompiloCommandResult): boolean {
  return result.generatedExplanation?.toLowerCase().includes("authoritative source is nompilo") ?? false;
}
