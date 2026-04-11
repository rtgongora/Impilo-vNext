"use client";

import { ctgTimeWindowSec } from "./ctgTraceUtils";

export type CtgSamplePoint = { tSec: number; value: number };

export interface CtgAnnotationMark {
  tSec: number;
  category?: string;
  severity?: string;
}

const VB_W = 720;
const STRIP_H = 76;
const ML = 48;
const MR = 12;
const MT = 8;
const MB = 22;

function buildPath(
  pts: CtgSamplePoint[],
  tMin: number,
  tMax: number,
  vMin: number,
  vMax: number,
  x0: number,
  y0: number,
  plotW: number,
  plotH: number,
): string {
  if (pts.length === 0) return "";
  const tSpan = Math.max(tMax - tMin, 1e-6);
  const vSpan = Math.max(vMax - vMin, 1e-6);
  return pts
    .map((p, i) => {
      const x = x0 + ((p.tSec - tMin) / tSpan) * plotW;
      const y = y0 + plotH - ((p.value - vMin) / vSpan) * plotH;
      return `${i === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

function Strip({
  label,
  pts,
  tMin,
  tMax,
  vMin,
  vMax,
  yTop,
  color,
  normalBand,
  marks,
}: {
  label: string;
  pts: CtgSamplePoint[];
  tMin: number;
  tMax: number;
  vMin: number;
  vMax: number;
  yTop: number;
  color: string;
  normalBand?: { min: number; max: number };
  marks: CtgAnnotationMark[];
}) {
  const plotW = VB_W - ML - MR;
  const plotH = STRIP_H - 18;
  const x0 = ML;
  const y0 = yTop + 14;
  const path = buildPath(pts, tMin, tMax, vMin, vMax, x0, y0, plotW, plotH);
  const tSpan = Math.max(tMax - tMin, 1e-6);
  const vSpan = Math.max(vMax - vMin, 1e-6);

  let bandTop: number | null = null;
  let bandH: number | null = null;
  if (normalBand && pts.length > 0) {
    bandTop = y0 + plotH - ((normalBand.max - vMin) / vSpan) * plotH;
    bandH = ((normalBand.max - normalBand.min) / vSpan) * plotH;
  }

  return (
    <g>
      <text x={ML} y={yTop + 10} className="fill-gray-700 text-[11px] font-semibold">
        {label}
      </text>
      <rect x={x0} y={y0} width={plotW} height={plotH} fill="#fff" stroke="#e5e7eb" />
      {bandTop != null && bandH != null && bandH > 0 ? (
        <rect x={x0} y={bandTop} width={plotW} height={bandH} fill="#ecfdf5" opacity={0.9} />
      ) : null}
      {[vMin, (vMin + vMax) / 2, vMax].map((v) => {
        const y = y0 + plotH - ((v - vMin) / vSpan) * plotH;
        return (
          <g key={`${label}-yt-${v}`}>
            <line x1={x0} x2={x0 + plotW} y1={y} y2={y} stroke="#f3f4f6" strokeWidth={1} />
            <text x={ML - 4} y={y + 3} textAnchor="end" className="fill-gray-400 text-[8px]">
              {Math.round(v)}
            </text>
          </g>
        );
      })}
      {path ? <path d={path} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" /> : null}
      {pts.map((p, i) => {
        const x = x0 + ((p.tSec - tMin) / tSpan) * plotW;
        const y = y0 + plotH - ((p.value - vMin) / vSpan) * plotH;
        return <circle key={`${label}-pt-${i}`} cx={x} cy={y} r={2.2} fill="#fff" stroke={color} strokeWidth={1.5} />;
      })}
      {marks.map((m, i) => {
        if (!Number.isFinite(m.tSec) || m.tSec < tMin || m.tSec > tMax) return null;
        const x = x0 + ((m.tSec - tMin) / tSpan) * plotW;
        return (
          <line
            key={`${label}-mark-${i}`}
            x1={x}
            x2={x}
            y1={y0}
            y2={y0 + plotH}
            stroke={m.severity === "HIGH" ? "#dc2626" : "#64748b"}
            strokeWidth={1}
            strokeDasharray="4 3"
            opacity={0.85}
          />
        );
      })}
    </g>
  );
}

export interface CtgTracePlotProps {
  fhr: CtgSamplePoint[];
  toco: CtgSamplePoint[];
  maternalHr: CtgSamplePoint[];
  annotationMarks: CtgAnnotationMark[];
  className?: string;
}

/**
 * Renders stacked FHR / TOCO / maternal HR from polled chunk samples.
 * Does not simulate device streaming — data is whatever the BFF returns on each poll.
 */
export function CtgTracePlot({ fhr, toco, maternalHr, annotationMarks, className }: CtgTracePlotProps) {
  const [tMin, tMax] = ctgTimeWindowSec([fhr, toco, maternalHr]);
  const totalH = MT + STRIP_H * 3 + MB + 8;
  const aria = `CTG trace preview: ${fhr.length} FHR samples, ${toco.length} TOCO samples, ${maternalHr.length} maternal HR samples. Updates via HTTP polling, not a live websocket.`;

  return (
    <div className={className}>
      <svg role="img" aria-label={aria} viewBox={`0 0 ${VB_W} ${totalH}`} className="h-auto w-full max-w-4xl">
        <title>{aria}</title>
        <rect width={VB_W} height={totalH} fill="#fafafa" rx="6" />
        <Strip
          label="FHR (bpm)"
          pts={fhr}
          tMin={tMin}
          tMax={tMax}
          vMin={60}
          vMax={200}
          yTop={MT}
          color="#15803d"
          normalBand={{ min: 110, max: 160 }}
          marks={annotationMarks}
        />
        <Strip
          label="TOCO"
          pts={toco}
          tMin={tMin}
          tMax={tMax}
          vMin={0}
          vMax={100}
          yTop={MT + STRIP_H}
          color="#7c3aed"
          marks={annotationMarks}
        />
        <Strip
          label="Maternal HR (bpm)"
          pts={maternalHr}
          tMin={tMin}
          tMax={tMax}
          vMin={40}
          vMax={140}
          yTop={MT + STRIP_H * 2}
          color="#b45309"
          marks={annotationMarks}
        />
        <text x={VB_W / 2} y={totalH - 6} textAnchor="middle" className="fill-gray-500 text-[9px]">
          Time (s) from first trace chunk · polled strips
        </text>
        {fhr.length === 0 && toco.length === 0 && maternalHr.length === 0 ? (
          <text x={VB_W / 2} y={MT + STRIP_H * 1.2} textAnchor="middle" className="fill-gray-400 text-[12px]">
            No trace chunks yet — ingest samples via POST …/chunks
          </text>
        ) : null}
      </svg>
    </div>
  );
}
