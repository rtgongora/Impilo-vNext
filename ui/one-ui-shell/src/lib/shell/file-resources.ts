import type { RecentItem, FileResource } from "@/lib/shell/types";
import type { PersonalDocumentResource } from "@/hooks/queries/usePersonalDocuments";

export function personalDocumentsToFileResources(docs: PersonalDocumentResource[]): FileResource[] {
  return docs.map((doc) => ({
    id: doc.id ? `personal:${doc.id}` : `personal:title:${doc.title}`,
    resourceType: "personal_document" as const,
    title: doc.title,
    associatedApp: "my_documents",
    associatedEntityType: "PersonalDocument",
    associatedEntityId: doc.id ?? undefined,
    createdAt: doc.createdAt,
    mimeType: doc.mimeType,
    category: doc.documentTypeCode,
    href: doc.id ? "/home/documents" : undefined,
    downloadAction: doc.id ? "prepared_url" : "none",
  }));
}

/** Map shell recent items that look like documents or downloads into explorer rows. */
export function recentItemsToFileResources(items: RecentItem[]): FileResource[] {
  const out: FileResource[] = [];
  for (const r of items) {
    const blob = `${r.refKey} ${r.title} ${r.subtitle ?? ""} ${r.href}`.toLowerCase();
    const docLike =
      r.kind === "resource" ||
      blob.includes("document") ||
      blob.includes("download") ||
      blob.includes("fusion:") ||
      blob.includes("search-hit:") ||
      blob.includes("/home/documents");
    if (!docLike) continue;
    out.push({
      id: `recent:${r.id}`,
      resourceType: r.refKey.startsWith("download:") ? "clinical_document" : "link",
      title: r.title,
      associatedApp: "shell_recent",
      href: r.href,
      category: r.subtitle,
      downloadAction: "navigate",
    });
  }
  return out;
}

function resourceKey(r: FileResource): string {
  return `${r.resourceType}:${r.id}`;
}

/** Merge lists with stable de-duplication (personal wins over recent for same id). */
export function mergeFileResources(primary: FileResource[], secondary: FileResource[]): FileResource[] {
  const seen = new Set<string>();
  const merged: FileResource[] = [];
  for (const r of [...primary, ...secondary]) {
    const k = resourceKey(r);
    if (seen.has(k)) continue;
    seen.add(k);
    merged.push(r);
  }
  return merged;
}
