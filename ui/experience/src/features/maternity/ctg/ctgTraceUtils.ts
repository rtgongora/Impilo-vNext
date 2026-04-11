import type { CtgTraceChunk } from "@/hooks/queries/useFetalMonitoring";

function chunkStartMs(c: CtgTraceChunk): number | null {
  const t = Date.parse(String(c.started_at ?? ""));
  return Number.isFinite(t) ? t : null;
}

/** Earliest chunk start among all channels — aligns strips on one timeline. */
export function ctgTraceOriginMs(chunks: CtgTraceChunk[]): number | null {
  const times = chunks.map(chunkStartMs).filter((t): t is number => t != null);
  if (times.length === 0) return null;
  return Math.min(...times);
}

/** Flatten BFF chunks for one channel into { tSec, value } sorted by time (seconds from origin). */
export function flattenCtgChannelSamples(
  chunks: CtgTraceChunk[],
  channel: string,
  originMs: number | null,
): { tSec: number; value: number }[] {
  const want = channel.trim().toUpperCase();
  const origin = originMs ?? 0;
  const out: { tSec: number; value: number }[] = [];

  for (const c of chunks) {
    if (String(c.channel ?? "").toUpperCase() !== want) continue;
    const t0 = chunkStartMs(c);
    const hz = Number(c.sample_rate_hz);
    if (t0 == null || !Number.isFinite(hz) || hz <= 0) continue;
    const rel0 = (t0 - origin) / 1000;
    const samples = Array.isArray(c.samples) ? c.samples : [];
    for (let i = 0; i < samples.length; i++) {
      const v = Number(samples[i]);
      if (!Number.isFinite(v)) continue;
      out.push({ tSec: rel0 + i / hz, value: v });
    }
  }
  return out.sort((a, b) => a.tSec - b.tSec);
}

export function ctgTimeWindowSec(pointsList: { tSec: number }[][]): [number, number] {
  const flat = pointsList.flat();
  if (flat.length === 0) return [0, 60];
  const tMin = Math.min(...flat.map((p) => p.tSec));
  const tMax = Math.max(...flat.map((p) => p.tSec));
  const pad = Math.max(2, (tMax - tMin) * 0.04);
  return [tMin - pad * 0.2, Math.max(tMax + pad, tMin + 15)];
}
