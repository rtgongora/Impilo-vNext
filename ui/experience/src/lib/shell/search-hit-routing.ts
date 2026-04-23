/**
 * Map search-service index hits to in-app routes when the payload allows it.
 * Conservative: only navigates when we have a stable id + known entity family,
 * or explicit BFF hints in contentJson (href, deep links, nested routing).
 */

export interface IndexSearchHitRef {
  entityType: string;
  entityId: string;
  contentJson?: Record<string, unknown>;
}

function asTrimmedString(v: unknown): string | null {
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length ? t : null;
}

/** Accepts root-relative app paths only (no protocol-relative or off-origin). */
function safeAppPath(v: unknown): string | null {
  const t = asTrimmedString(v);
  if (!t) return null;
  if (t.startsWith("/") && !t.startsWith("//")) return t;
  return null;
}

function hrefFromNested(obj: Record<string, unknown> | undefined, keys: string[]): string | null {
  if (!obj) return null;
  for (const k of keys) {
    const h = safeAppPath(obj[k]);
    if (h) return h;
  }
  return null;
}

function hrefFromContentJson(cj: Record<string, unknown> | undefined): string | null {
  if (!cj) return null;

  const directKeys = [
    "href",
    "url",
    "path",
    "deepLink",
    "deep_link",
    "route",
    "navigationHref",
    "navigation_href",
    "experienceHref",
    "experience_href",
    "appPath",
    "app_path",
    "link",
    "canonicalPath",
    "canonical_path",
  ];
  for (const k of directKeys) {
    const h = safeAppPath(cj[k]);
    if (h) return h;
  }

  const nestedSources = [
    cj.routing as Record<string, unknown> | undefined,
    cj.meta as Record<string, unknown> | undefined,
    cj.bff as Record<string, unknown> | undefined,
    cj.navigation as Record<string, unknown> | undefined,
    cj.experience as Record<string, unknown> | undefined,
    cj.attributes as Record<string, unknown> | undefined,
  ];
  for (const nest of nestedSources) {
    const h = hrefFromNested(nest, ["href", "path", "route", "url", "deepLink", "deep_link"]);
    if (h) return h;
  }

  return null;
}

function patientIdFromContentJson(cj: Record<string, unknown> | undefined): string | null {
  if (!cj) return null;
  return (
    asTrimmedString(cj.patientId) ??
    asTrimmedString(cj.patient_id) ??
    asTrimmedString(cj.cpid) ??
    asTrimmedString(cj.healthId) ??
    asTrimmedString(cj.health_id) ??
    null
  );
}

function encounterIdFromContentJson(cj: Record<string, unknown> | undefined): string | null {
  if (!cj) return null;
  return (
    asTrimmedString(cj.encounterId) ??
    asTrimmedString(cj.encounter_id) ??
    asTrimmedString(cj.visitId) ??
    asTrimmedString(cj.visit_id) ??
    null
  );
}

function resourceTypeHints(cj: Record<string, unknown> | undefined, entityType: string): string {
  const rt =
    (cj && (asTrimmedString(cj.resourceType) ?? asTrimmedString(cj.resource_type))) ?? "";
  return `${entityType} ${rt}`.toLowerCase();
}

/**
 * Returns a relative path for the Experience app, or null when navigation is unknown/unsafe.
 */
export function resolveIndexHitHref(hit: IndexSearchHitRef): string | null {
  const direct = hrefFromContentJson(hit.contentJson);
  if (direct) return direct;

  const cj = hit.contentJson;
  const pid = patientIdFromContentJson(cj);
  const eid = encounterIdFromContentJson(cj);
  if (pid && eid) {
    return `/ehr/${encodeURIComponent(pid)}/encounter/${encodeURIComponent(eid)}`;
  }

  const id = hit.entityId?.trim();
  const hints = resourceTypeHints(cj, hit.entityType);

  if (pid && (!id || id === pid)) {
    if (hints.includes("encounter") || hints.includes("visit")) {
      return `/ehr/${encodeURIComponent(pid)}/encounters`;
    }
    return `/ehr/${encodeURIComponent(pid)}/summary`;
  }

  if (!id) return null;

  const type = hit.entityType.toLowerCase();

  if (type.includes("patient") || type === "cpid" || type.includes("health_id") || type.includes("client")) {
    return `/ehr/${encodeURIComponent(id)}/summary`;
  }
  if (hints.includes("patient") || hints.includes("cpid")) {
    return `/ehr/${encodeURIComponent(id)}/summary`;
  }
  if (type.includes("encounter") || type.includes("visit")) {
    if (pid) return `/ehr/${encodeURIComponent(pid)}/encounter/${encodeURIComponent(id)}`;
    return `/ehr/${encodeURIComponent(id)}/summary`;
  }
  if (hints.includes("encounter") || hints.includes("visit")) {
    if (pid) return `/ehr/${encodeURIComponent(pid)}/encounter/${encodeURIComponent(id)}`;
  }
  if (type.includes("facility") || type.includes("site") || type.includes("tuso")) {
    return `/registry/facilities/${encodeURIComponent(id)}`;
  }
  if (hints.includes("facility") || hints.includes("site")) {
    return `/registry/facilities/${encodeURIComponent(id)}`;
  }
  if (type.includes("provider") || type.includes("varapi")) {
    return `/registry/providers/${encodeURIComponent(id)}`;
  }
  if (hints.includes("provider")) {
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
  if (
    type.includes("imaging") ||
    type.includes("imaging_study") ||
    type.includes("dicom") ||
    hints.includes("imaging study") ||
    hints.includes("pacs")
  ) {
    if (pid) {
      const study = asTrimmedString(cj?.studyUid) ?? asTrimmedString(cj?.study_uid);
      if (study) {
        return `/ehr/${encodeURIComponent(pid)}/imaging/viewer?studyUid=${encodeURIComponent(study)}`;
      }
      return `/ehr/${encodeURIComponent(pid)}/imaging`;
    }
    return null;
  }

  if (type.includes("learning_resource") || type.includes("learning")) {
    const exp =
      asTrimmedString(cj?.experienceHref) ??
      asTrimmedString(cj?.experience_href) ??
      asTrimmedString(cj?.href);
    if (exp) return exp;
    if (id) return `/learning?focus=${encodeURIComponent(id)}`;
  }

  return null;
}
