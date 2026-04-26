/**
 * Human-readable local timestamp for ISO-8601 instants (long-tail + facility UX).
 */
export function formatWhen(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
