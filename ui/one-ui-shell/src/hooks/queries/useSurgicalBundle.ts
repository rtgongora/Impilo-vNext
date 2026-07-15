/**
 * COSTA surgical-case bundle for an encounter.
 *
 * When theatre completes a case, COSTA posts an itemised surgical bundle onto
 * the encounter's bill (source_event = `theatre.case.completed`, grouped by the
 * procedure episode via source_ref). This hook composes the two read proxies the
 * BFF already exposes — encounter bills → bill lines — and surfaces just the
 * bundle lines, grouped per surgical case. Read-only; no new backend.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** msika codes the theatre-case bundle composer emits (fallback discriminator). */
const BUNDLE_CODES = new Set([
  "THEATRE-TIME",
  "ANAESTHESIA-GA",
  "THEATRE-IMPLANT",
  "THEATRE-CONSUMABLE",
  "THEATRE-BLOOD",
]);

const BUNDLE_SOURCE_EVENT = "theatre.case.completed";

export interface SurgicalBundleLine {
  lineId: string;
  msikaCode: string | null;
  description: string | null;
  kind: string | null;
  qty: number | null;
  unitPrice: number | null;
  amount: number | null;
  sourceRef: string | null;
  postedAt: string | null;
}

export interface SurgicalBundleCase {
  episodeRef: string;
  lines: SurgicalBundleLine[];
  subtotal: number;
}

export interface SurgicalBundle {
  cases: SurgicalBundleCase[];
  lineCount: number;
}

function pick(row: Record<string, unknown>, ...keys: string[]): unknown {
  for (const key of keys) {
    if (row[key] != null) return row[key];
  }
  return undefined;
}

function num(value: unknown): number | null {
  if (value == null) return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

function str(value: unknown): string | null {
  if (value == null) return null;
  return typeof value === "string" ? value : String(value);
}

/** Is this bill line part of a theatre-case bundle? source_event is authoritative; msika code is a fallback. */
function isBundleLine(row: Record<string, unknown>): boolean {
  if (pick(row, "voided") === true) return false;
  const sourceEvent = str(pick(row, "sourceEvent", "source_event"));
  if (sourceEvent === BUNDLE_SOURCE_EVENT) return true;
  const code = str(pick(row, "msikaCode", "msika_code"));
  return code != null && BUNDLE_CODES.has(code);
}

function toLine(row: Record<string, unknown>): SurgicalBundleLine {
  return {
    lineId: str(pick(row, "lineId", "line_id")) ?? "",
    msikaCode: str(pick(row, "msikaCode", "msika_code")),
    description: str(pick(row, "description")),
    kind: str(pick(row, "kind")),
    qty: num(pick(row, "qty")),
    unitPrice: num(pick(row, "unitPrice", "unit_price")),
    amount: num(pick(row, "amount")),
    sourceRef: str(pick(row, "sourceRef", "source_ref")),
    postedAt: str(pick(row, "postedAt", "posted_at")),
  };
}

function extractLineItems(billDetail: unknown): Record<string, unknown>[] {
  if (!billDetail || typeof billDetail !== "object") return [];
  const data = (billDetail as Record<string, unknown>).data ?? billDetail;
  if (!data || typeof data !== "object") return [];
  const attributes = (data as Record<string, unknown>).attributes;
  const source = attributes && typeof attributes === "object"
    ? (attributes as Record<string, unknown>).lineItems
    : (data as Record<string, unknown>).lineItems ?? (data as Record<string, unknown>).lines;
  return Array.isArray(source) ? (source as Record<string, unknown>[]) : [];
}

function extractBillIds(billing: unknown): string[] {
  if (!billing || typeof billing !== "object") return [];
  const data = (billing as Record<string, unknown>).data ?? billing;
  const bills = data && typeof data === "object" ? (data as Record<string, unknown>).bills : undefined;
  if (!Array.isArray(bills)) return [];
  return bills
    .map((b) => (b && typeof b === "object" ? str((b as Record<string, unknown>).billId) : null))
    .filter((v): v is string => Boolean(v && v.trim().length > 0));
}

export function useSurgicalBundle(encounterId: string, enabled = true) {
  return useQuery<SurgicalBundle>({
    queryKey: ["finance", "surgical-bundle", encounterId],
    enabled: enabled && encounterId.trim().length > 0,
    staleTime: 30_000,
    queryFn: async () => {
      const billing = await apiClient.get<unknown>(
        `/internal/v1/encounters/${encodeURIComponent(encounterId)}/billing`,
      );
      const billIds = extractBillIds(billing);
      const details = await Promise.all(
        billIds.map((billId) =>
          apiClient
            .get<unknown>(`/internal/v1/finance/billing/${encodeURIComponent(billId)}`)
            .catch(() => null),
        ),
      );

      const byEpisode = new Map<string, SurgicalBundleLine[]>();
      let lineCount = 0;
      for (const detail of details) {
        for (const raw of extractLineItems(detail)) {
          if (!isBundleLine(raw)) continue;
          const line = toLine(raw);
          const key = line.sourceRef ?? "unlinked";
          const bucket = byEpisode.get(key) ?? [];
          bucket.push(line);
          byEpisode.set(key, bucket);
          lineCount += 1;
        }
      }

      const cases: SurgicalBundleCase[] = Array.from(byEpisode.entries()).map(([episodeRef, lines]) => ({
        episodeRef,
        lines,
        subtotal: lines.reduce((sum, l) => sum + (l.amount ?? 0), 0),
      }));

      return { cases, lineCount };
    },
  });
}
