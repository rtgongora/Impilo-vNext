"use client";

/**
 * Vitals Trend Chart — Lightweight SVG sparkline visualization
 * for vital sign trends over time. No external charting library needed.
 */

interface DataPoint {
  value: number;
  timestamp: string;
}

interface VitalsTrendChartProps {
  label: string;
  unit: string;
  data: DataPoint[];
  normalMin?: number;
  normalMax?: number;
  color?: string;
  height?: number;
}

export function VitalsTrendChart({
  label, unit, data, normalMin, normalMax,
  color = "#3b82f6", height = 80,
}: VitalsTrendChartProps) {
  if (data.length === 0) return null;

  const values = data.map((d) => d.value);
  const min = Math.min(...values) * 0.9;
  const max = Math.max(...values) * 1.1;
  const range = max - min || 1;
  const width = 280;
  const padding = 4;

  const points = data.map((d, i) => {
    const x = padding + (i / Math.max(data.length - 1, 1)) * (width - padding * 2);
    const y = padding + (1 - (d.value - min) / range) * (height - padding * 2);
    return { x, y, value: d.value, time: d.timestamp };
  });

  const pathD = points.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
  const latest = data[data.length - 1];

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-3">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-medium text-gray-700">{label}</span>
        <span className="text-sm font-bold" style={{ color }}>
          {latest.value} <span className="text-xs font-normal text-gray-500">{unit}</span>
        </span>
      </div>
      <svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} className="overflow-visible">
        {/* Normal range band */}
        {normalMin != null && normalMax != null && (
          <rect
            x={padding} y={padding + (1 - (normalMax - min) / range) * (height - padding * 2)}
            width={width - padding * 2}
            height={((normalMax - normalMin) / range) * (height - padding * 2)}
            fill="#dcfce7" rx="2"
          />
        )}
        {/* Trend line */}
        <path d={pathD} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        {/* Data points */}
        {points.map((p, i) => (
          <circle key={i} cx={p.x} cy={p.y} r="3" fill="white" stroke={color} strokeWidth="2" />
        ))}
      </svg>
      <div className="flex justify-between mt-1 text-[9px] text-gray-400">
        {data.length > 1 && (
          <>
            <span>{new Date(data[0].timestamp).toLocaleDateString()}</span>
            <span>{new Date(data[data.length - 1].timestamp).toLocaleDateString()}</span>
          </>
        )}
      </div>
    </div>
  );
}

/** Convenience wrapper for multiple vital trends */
export function VitalsTrendPanel({ vitals }: {
  vitals: Array<{ attributes: Record<string, unknown> }>;
}) {
  if (vitals.length < 2) return null;

  const toPoints = (field: string): DataPoint[] =>
    vitals.filter((v) => v.attributes[field] != null).map((v) => ({
      value: v.attributes[field] as number,
      timestamp: (v.attributes.recorded_at ?? v.attributes.created_at ?? "") as string,
    }));

  const bp = toPoints("systolic");
  const hr = toPoints("heart_rate");
  const spo2 = toPoints("oxygen_saturation");
  const temp = toPoints("temperature");

  return (
    <div className="grid grid-cols-2 gap-3">
      {bp.length >= 2 && <VitalsTrendChart label="Systolic BP" unit="mmHg" data={bp} normalMin={90} normalMax={140} color="#ef4444" />}
      {hr.length >= 2 && <VitalsTrendChart label="Heart Rate" unit="bpm" data={hr} normalMin={60} normalMax={100} color="#f59e0b" />}
      {spo2.length >= 2 && <VitalsTrendChart label="SpO₂" unit="%" data={spo2} normalMin={95} normalMax={100} color="#3b82f6" />}
      {temp.length >= 2 && <VitalsTrendChart label="Temperature" unit="°C" data={temp} normalMin={36.1} normalMax={37.5} color="#8b5cf6" />}
    </div>
  );
}
