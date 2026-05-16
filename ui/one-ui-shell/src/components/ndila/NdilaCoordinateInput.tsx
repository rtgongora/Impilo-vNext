"use client";

/**
 * NdilaCoordinateInput — manual + device-GPS coordinate entry.
 *
 * Calls `ndilaValidateLocation` for live range / plausibility checks against
 * the user's country setting. Surfaces accuracy + Ndila warnings (e.g.
 * OUTSIDE_ZIMBABWE_BOUNDING_BOX, ZERO_COORDINATES_SUSPECT, LOW_ACCURACY) so
 * field workers correct bad captures before submission.
 */

import { useEffect, useState } from "react";
import { Crosshair, Loader2 } from "lucide-react";
import { ndilaValidateLocation, type NdilaCoordinate } from "@/lib/ndila/ndila-client";

interface Props {
  value?: NdilaCoordinate | null;
  onChange?: (next: NdilaCoordinate | null, accuracyMeters?: number) => void;
  country?: string;
  allowCurrentDeviceLocation?: boolean;
  disabled?: boolean;
}

export function NdilaCoordinateInput({
  value, onChange, country = "ZWE",
  allowCurrentDeviceLocation = true, disabled,
}: Props) {
  const [lat, setLat] = useState(value?.latitude?.toString() ?? "");
  const [lng, setLng] = useState(value?.longitude?.toString() ?? "");
  const [accuracy, setAccuracy] = useState<number | undefined>(undefined);
  const [acquiring, setAcquiring] = useState(false);
  const [validating, setValidating] = useState(false);
  const [feedback, setFeedback] = useState<{ valid: boolean; plausible: boolean; warnings: string[]; errors: string[] } | null>(null);

  useEffect(() => {
    const n = (s: string) => Number.isFinite(parseFloat(s)) ? parseFloat(s) : NaN;
    const la = n(lat), ln = n(lng);
    if (!Number.isFinite(la) || !Number.isFinite(ln)) {
      onChange?.(null, accuracy);
      setFeedback(null);
      return;
    }
    onChange?.({ latitude: la, longitude: ln }, accuracy);
    setValidating(true);
    let cancelled = false;
    ndilaValidateLocation({ latitude: la, longitude: ln }, accuracy, country)
      .then((r) => { if (!cancelled) setFeedback({ valid: r.valid, plausible: r.plausible, warnings: r.warnings ?? [], errors: r.errors ?? [] }); })
      .catch(() => { if (!cancelled) setFeedback(null); })
      .finally(() => { if (!cancelled) setValidating(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lat, lng, accuracy, country]);

  const captureGps = () => {
    if (!navigator.geolocation) { setFeedback({ valid: false, plausible: false, warnings: [], errors: ["GEOLOCATION_UNSUPPORTED"] }); return; }
    setAcquiring(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude.toFixed(6));
        setLng(pos.coords.longitude.toFixed(6));
        setAccuracy(pos.coords.accuracy);
        setAcquiring(false);
      },
      () => { setFeedback({ valid: false, plausible: false, warnings: [], errors: ["GEOLOCATION_DENIED"] }); setAcquiring(false); },
      { enableHighAccuracy: true, timeout: 10_000 }
    );
  };

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2">
        <label className="block text-xs text-gray-700">
          Latitude
          <input
            type="number" step="any" value={lat} disabled={disabled}
            onChange={(e) => setLat(e.target.value)}
            className="mt-0.5 w-full rounded border border-gray-300 px-2 py-1 text-sm tabular-nums"
            placeholder="-17.8292"
          />
        </label>
        <label className="block text-xs text-gray-700">
          Longitude
          <input
            type="number" step="any" value={lng} disabled={disabled}
            onChange={(e) => setLng(e.target.value)}
            className="mt-0.5 w-full rounded border border-gray-300 px-2 py-1 text-sm tabular-nums"
            placeholder="31.0522"
          />
        </label>
      </div>
      <div className="flex items-center justify-between gap-2">
        {allowCurrentDeviceLocation && (
          <button
            type="button" disabled={disabled || acquiring}
            onClick={captureGps}
            className="inline-flex items-center gap-1 rounded bg-indigo-600 px-2 py-1 text-xs text-white disabled:opacity-50"
          >
            {acquiring ? <Loader2 className="h-3 w-3 animate-spin" /> : <Crosshair className="h-3 w-3" />}
            Use device GPS
          </button>
        )}
        <span className="text-[10px] text-gray-500">
          {accuracy !== undefined ? `±${Math.round(accuracy)}m accuracy` : ""}
        </span>
      </div>
      {validating ? (
        <p className="text-[10px] text-gray-500">Validating with Ndila…</p>
      ) : feedback ? (
        <div className="text-[10px] space-y-0.5">
          {feedback.errors.map((e) => (
            <p key={e} className="text-red-600">✗ {e}</p>
          ))}
          {feedback.warnings.map((w) => (
            <p key={w} className="text-amber-600">⚠ {w}</p>
          ))}
          {feedback.valid && feedback.plausible && feedback.warnings.length === 0 && feedback.errors.length === 0 && (
            <p className="text-emerald-600">✓ Coordinate looks plausible.</p>
          )}
        </div>
      ) : null}
    </div>
  );
}
