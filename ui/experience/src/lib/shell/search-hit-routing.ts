/**
 * Map search-service index hits to in-app routes when the payload allows it.
 * Conservative: only navigates when we have a stable id + known entity family.
 */

export interface IndexSearchHitRef {
  entityType: string;
  entityId: string;
  contentJson?: Record<string, unknown>;
}

function hrefFromContentJson(cj: Record<string, unknown> | undefined): string | null {
  if (!cj) return null;
  const href = cj.href ?? cj.url ?? cj.path;
  if (typeof href === "string" && href.startsWith("/") && !href.startsWith("//")) {
    return href;
  }
  return null;
}

/**
 * Returns a relative path for the Experience app, or null when navigation is unknown/unsafe.
 */
export function resolveIndexHitHref(hit: IndexSearchHitRef): string | null {
  const direct = hrefFromContentJson(hit.contentJson);
  if (direct) return direct;

  const id = hit.entityId?.trim();
  if (!id) return null;

  const type = hit.entityType.toLowerCase();

  if (type.includes("patient") || type === "cpid" || type.includes("health_id") || type.includes("client")) {
    return `/ehr/${encodeURIComponent(id)}/summary`;
  }
  if (type.includes("facility") || type.includes("site") || type.includes("tuso")) {
    return `/registry/facilities/${encodeURIComponent(id)}`;
  }
  if (type.includes("provider") || type.includes("varapi")) {
    return `/registry/providers/${encodeURIComponent(id)}`;
  }
  if (type.includes("document") || type.includes("vault")) {
    return "/home/documents";
  }
  if (type.includes("order") && type.includes("market")) {
    return `/marketplace/orders/${encodeURIComponent(id)}`;
  }
  if (type.includes("product") || type.includes("catalog") || type.includes("msika")) {
    return `/marketplace/catalog`;
  }
  if (type.includes("service") || type.includes("discover")) {
    return "/discover/services";
  }
  if (type.includes("report")) {
    return `/reports/${encodeURIComponent(id)}`;
  }

  return null;
}
