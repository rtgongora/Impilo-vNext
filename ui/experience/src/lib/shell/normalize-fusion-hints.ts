import type { ShellFusionHint } from "@/lib/intelligence/types";
import { resolveIndexHitHref, type IndexSearchHitRef } from "@/lib/shell/search-hit-routing";

function titleFromIndexHit(hit: Record<string, unknown>): string {
  const cj = hit.contentJson as Record<string, unknown> | undefined;
  const t =
    (cj && typeof cj.title === "string" && cj.title) ||
    (cj && typeof cj.name === "string" && cj.name) ||
    (typeof hit.searchableText === "string" && hit.searchableText.slice(0, 120)) ||
    (typeof hit.entityType === "string" ? hit.entityType : "entity");
  return String(t);
}

/**
 * Normalizes BFF `GET /internal/v1/intelligence-plane/shell-suggest` JSON into palette rows.
 * Response shape: `{ data: { hints: [{ source, hit: { entityType, entityId, contentJson, ... } }] } }`.
 */
export function normalizeFusionHints(raw: unknown): ShellFusionHint[] {
  if (!raw || typeof raw !== "object") return [];
  const root = raw as Record<string, unknown>;
  const data = root.data as Record<string, unknown> | undefined;
  const hints = data?.hints;
  if (!Array.isArray(hints)) return [];
  return hints
    .map((h, index) => {
      if (!h || typeof h !== "object") return null;
      const row = h as Record<string, unknown>;
      const source = typeof row.source === "string" ? row.source : "fusion";
      const hit = row.hit as Record<string, unknown> | undefined;
      if (!hit) return null;
      let title = titleFromIndexHit(hit);
      if (source === "guidance_knowledge") {
        title =
          (typeof hit.title === "string" && hit.title) ||
          (typeof hit.headline === "string" && hit.headline) ||
          title;
      }
      const subtitle =
        typeof hit.searchableText === "string"
          ? hit.searchableText.slice(0, 120)
          : typeof hit.summary === "string"
            ? hit.summary.slice(0, 120)
            : source.replace(/_/g, " ");
      const ref: IndexSearchHitRef = {
        entityType: typeof hit.entityType === "string" ? hit.entityType : "entity",
        entityId: typeof hit.entityId === "string" ? hit.entityId : "",
        contentJson: hit.contentJson as Record<string, unknown> | undefined,
      };
      const href = resolveIndexHitHref(ref) ?? undefined;
      return {
        id: `fusion-${source}-${index}`,
        source,
        title: String(title),
        subtitle: String(subtitle),
        href: href ?? undefined,
      };
    })
    .filter(Boolean) as ShellFusionHint[];
}
