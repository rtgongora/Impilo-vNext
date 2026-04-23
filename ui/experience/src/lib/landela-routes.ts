/**
 * Deep links into Experience “Landela Documents” workspace ({@code /access} tab).
 */

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isLikelyLandelaDocumentUuid(value: string): boolean {
  return UUID_RE.test(value.trim());
}

/** Opens Access → Landela tab with a focused document id (metadata fetch in UI). */
export function landelaDocumentAccessHref(documentId: string): string {
  const id = documentId.trim();
  const q = new URLSearchParams();
  q.set("tab", "landela");
  if (id) {
    q.set("documentId", id);
  }
  return `/access?${q.toString()}`;
}

/** Registry intake with Landela source pre-selected and UUID prefilled. */
export function registryIntakeLandelaPrefillHref(documentId: string): string {
  const id = documentId.trim();
  const q = new URLSearchParams();
  q.set("landelaDocumentId", id);
  q.set("importSource", "landela");
  return `/registry/intake?${q.toString()}`;
}
