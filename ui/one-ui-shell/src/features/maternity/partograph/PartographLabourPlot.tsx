"use client";

/**
 * WHO-inspired labour partograph plot (SVG, no chart deps).
 * Lives under features/maternity for promotion to /ehr/[patientId]/maternity.
 */

import type { PartographPoint } from "@/hooks/queries/usePartograph";

function toNumber(v: unknown): number | null {
  if (v == null) return null;
  if (typeof v === "number" && Number.isFinite(v)) return v;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function parseTime(iso: string | undefined): number | null {
  if (!iso) return null;
  const t = Date.parse(iso);
  return Number.isFinite(t) ? t : null;
}

/** Hours since session start for each observation (x-axis). */
export function partographHoursSinceStart(startedAt: string | undefined, observedAt: string | undefined): number | null {
  const t0 = parseTime(startedAt);
  const t1 = parseTime(observedAt);
  if (t0 == null || t1 == null) return null;
  return (t1 - t0) / 3_600_000;
}

const VB_W = 720;
const VB_H = 380;
const ML = 52;
const MR = 48;
const MT = 28;
const MB = 40;
const GAP = 10;
const DIL_H = 168;
const FHR_TOP = MT + DIL_H + GAP;
const FHR_H = 110;
const CTX_TOP = FHR_TOP + FHR_H + GAP;
const CTX_H = 44;

export interface PartographLabourPlotProps {
  startedAt: string | undefined;
  points: PartographPoint[];
  className?: string;
}

/**
 * Renders cervical dilation + fetal descent (upper), FHR (middle), contraction frequency (lower).
 * Continuous CTG traces are not plotted here — only scalar FHR samples per partograph observation (see Fetal monitoring / CTG on Vitals).
 */
export function PartographLabourPlot({ startedAt, points, className }: PartographLabourPlotProps) {
  const ordered = [...points].sort((a, b) => {
    const ta = parseTime(a.observed_at) ?? 0;
    const tb = parseTime(b.observed_at) ?? 0;
    return ta - tb;
  });

  let xMax = 4;
  for (const p of ordered) {
    const h = partographHoursSinceStart(startedAt, p.observed_at ?? undefined);
    if (h != null) xMax = Math.max(xMax, h * 1.08);
  }

  const plotW = VB_W - ML - MR;
  const xScale = (h: number) => ML + (h / xMax) * plotW;

  const dilBottom = MT + DIL_H;
  const dilY = (cm: number) => dilBottom - (cm / 10) * DIL_H;
  const descentY = (fifths: number) => dilBottom - ((fifths + 5) / 10) * DIL_H;

  const fhrBottom = FHR_TOP + FHR_H;
  const fhrMin = 60;
  const fhrMax = 200;
  const fhrY = (bpm: number) => fhrBottom - ((bpm - fhrMin) / (fhrMax - fhrMin)) * FHR_H;

  const ctxBottom = CTX_TOP + CTX_H;
  const ctxBarMax = 12;
  const ctxBarH = (freq: number) => Math.min(freq / ctxBarMax, 1) * (CTX_H - 6);

  const dilPts = ordered
    .map((p) => {
      const h = partographHoursSinceStart(startedAt, p.observed_at ?? undefined);
      const cm = toNumber(p.cervical_dilation_cm);
      if (h == null || cm == null) return null;
      return { x: xScale(h), y: dilY(cm), cm, h, p };
    })
    .filter(Boolean) as Array<{ x: number; y: number; cm: number; h: number; p: PartographPoint }>;

  const desPts = ordered
    .map((p) => {
      const h = partographHoursSinceStart(startedAt, p.observed_at ?? undefined);
      const d = p.fetal_descent_fifths;
      if (h == null || d == null || !Number.isFinite(d)) return null;
      return { x: xScale(h), y: descentY(d), d, p };
    })
    .filter(Boolean) as Array<{ x: number; y: number; d: number; p: PartographPoint }>;

  const fhrPts = ordered
    .map((p) => {
      const h = partographHoursSinceStart(startedAt, p.observed_at ?? undefined);
      const bpm = p.fetal_heart_rate_bpm;
      if (h == null || bpm == null || !Number.isFinite(bpm)) return null;
      const clamped = Math.min(fhrMax, Math.max(fhrMin, bpm));
      return { x: xScale(h), y: fhrY(clamped), bpm, p };
    })
    .filter(Boolean) as Array<{ x: number; y: number; bpm: number; p: PartographPoint }>;

  const ctxPts = ordered
    .map((p) => {
      const h = partographHoursSinceStart(startedAt, p.observed_at ?? undefined);
      const f = p.contraction_frequency_10min;
      if (h == null || f == null || !Number.isFinite(f)) return null;
      return { x: xScale(h), w: Math.max(6, plotW / Math.max(ordered.length * 2, 8)), f, p };
    })
    .filter(Boolean) as Array<{ x: number; w: number; f: number; p: PartographPoint }>;

  const dilPath =
    dilPts.length > 0 ? dilPts.map((pt, i) => `${i === 0 ? "M" : "L"} ${pt.x.toFixed(1)} ${pt.y.toFixed(1)}`).join(" ") : "";
  const desPath =
    desPts.length > 0 ? desPts.map((pt, i) => `${i === 0 ? "M" : "L"} ${pt.x.toFixed(1)} ${pt.y.toFixed(1)}`).join(" ") : "";
  const fhrPath =
    fhrPts.length > 0 ? fhrPts.map((pt, i) => `${i === 0 ? "M" : "L"} ${pt.x.toFixed(1)} ${pt.y.toFixed(1)}`).join(" ") : "";

  const xTicks = [0, Math.ceil(xMax / 4), Math.ceil(xMax / 2), Math.ceil((3 * xMax) / 4), Math.ceil(xMax)].filter(
    (v, i, a) => v >= 0 && a.indexOf(v) === i,
  );

  const ariaLabel = `Partograph: ${ordered.length} observation(s); dilation points ${dilPts.length}, FHR points ${fhrPts.length}. Continuous CTG is on the separate CTG panel, not this plot.`;

  return (
    <div className={className}>
      <svg
        role="img"
        aria-label={ariaLabel}
        viewBox={`0 0 ${VB_W} ${VB_H}`}
        className="h-auto w-full max-w-4xl text-foreground"
      >
        <title>{ariaLabel}</title>
        <rect x="0" y="0" width={VB_W} height={VB_H} fill="#fafafa" rx="6" />

        {/* ——— Dilation + descent ——— */}
        <text x={ML} y={MT - 8} className="fill-gray-700 text-[11px] font-semibold">
          Cervical dilation (cm) & fetal descent (fifths)
        </text>
        {[0, 2, 4, 6, 8, 10].map((cm) => (
          <g key={`dil-grid-${cm}`}>
            <line
              x1={ML}
              x2={VB_W - MR}
              y1={dilY(cm)}
              y2={dilY(cm)}
              stroke="#e5e7eb"
              strokeWidth={cm === 0 ? 1.5 : 1}
            />
            <text x={ML - 6} y={dilY(cm) + 3} textAnchor="end" className="fill-gray-500 text-[9px]">
              {cm}
            </text>
          </g>
        ))}
        {[5, 3, 1, -1, -3, -5].map((f) => (
          <text key={`des-r-${f}`} x={VB_W - MR + 8} y={descentY(f) + 3} className="fill-sky-700 text-[8px]">
            {f > 0 ? `+${f}` : f}
          </text>
        ))}
        {dilPath ? (
          <path d={dilPath} fill="none" stroke="#db2777" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
        ) : null}
        {desPath ? (
          <path
            d={desPath}
            fill="none"
            stroke="#0284c7"
            strokeWidth={2}
            strokeDasharray="6 4"
            strokeLinejoin="round"
            strokeLinecap="round"
            opacity={0.9}
          />
        ) : null}
        {dilPts.map((pt) => (
          <circle key={`dil-${String(pt.p.id ?? pt.h)}`} cx={pt.x} cy={pt.y} r={4} fill="#fff" stroke="#db2777" strokeWidth={2} />
        ))}
        {desPts.map((pt) => (
          <circle
            key={`des-${String(pt.p.id ?? pt.d)}`}
            cx={pt.x}
            cy={pt.y}
            r={3.5}
            fill="#fff"
            stroke="#0284c7"
            strokeWidth={2}
          />
        ))}

        {/* ——— FHR ——— */}
        <text x={ML} y={FHR_TOP - 6} className="fill-gray-700 text-[11px] font-semibold">
          Fetal heart rate (bpm) — scalar samples per observation only (continuous CTG below)
        </text>
        <rect x={ML} y={FHR_TOP} width={plotW} height={FHR_H} fill="#fff" stroke="#e5e7eb" />
        <rect x={ML} y={fhrY(160)} width={plotW} height={fhrY(110) - fhrY(160)} fill="#ecfdf5" opacity={0.85} />
        <line x1={ML} x2={VB_W - MR} y1={fhrY(110)} y2={fhrY(110)} stroke="#86efac" strokeWidth={1} />
        <line x1={ML} x2={VB_W - MR} y1={fhrY(160)} y2={fhrY(160)} stroke="#86efac" strokeWidth={1} />
        {[60, 110, 160, 200].map((b) => (
          <text key={`fhr-t-${b}`} x={ML - 6} y={fhrY(b) + 3} textAnchor="end" className="fill-gray-500 text-[9px]">
            {b}
          </text>
        ))}
        {fhrPath ? (
          <path d={fhrPath} fill="none" stroke="#15803d" strokeWidth={2.2} strokeLinejoin="round" strokeLinecap="round" />
        ) : null}
        {fhrPts.map((pt) => (
          <circle key={`fhr-${String(pt.p.id ?? pt.bpm)}`} cx={pt.x} cy={pt.y} r={3.5} fill="#fff" stroke="#15803d" strokeWidth={2} />
        ))}

        {/* ——— Contractions ——— */}
        <text x={ML} y={CTX_TOP - 6} className="fill-gray-700 text-[11px] font-semibold">
          Contractions (per 10 min)
        </text>
        <rect x={ML} y={CTX_TOP} width={plotW} height={CTX_H} fill="#fff" stroke="#e5e7eb" />
        {ctxPts.map((pt) => (
          <rect
            key={`ctx-${String(pt.p.id ?? pt.f)}`}
            x={pt.x - pt.w / 2}
            y={ctxBottom - ctxBarH(pt.f)}
            width={pt.w}
            height={ctxBarH(pt.f)}
            fill="#c084fc"
            opacity={0.85}
            rx={2}
          />
        ))}

        {/* X axis */}
        <line x1={ML} x2={VB_W - MR} y1={VB_H - MB + 12} y2={VB_H - MB + 12} stroke="#9ca3af" strokeWidth={1.2} />
        {xTicks.map((xv) => (
          <g key={`xt-${xv}`}>
            <line x1={xScale(xv)} x2={xScale(xv)} y1={VB_H - MB + 12} y2={VB_H - MB + 18} stroke="#9ca3af" />
            <text x={xScale(xv)} y={VB_H - MB + 30} textAnchor="middle" className="fill-gray-600 text-[9px]">
              {xv}h
            </text>
          </g>
        ))}
        <text x={VB_W / 2} y={VB_H - 6} textAnchor="middle" className="fill-gray-500 text-[10px]">
          Hours since partograph session started
        </text>

        {ordered.length === 0 ? (
          <text x={VB_W / 2} y={MT + DIL_H / 2} textAnchor="middle" className="fill-gray-400 text-[12px]">
            No observations yet — record a point to plot progress
          </text>
        ) : null}
      </svg>
    </div>
  );
}
