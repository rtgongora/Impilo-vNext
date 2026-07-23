"use client";

import { useEffect, useState } from "react";
import { Droplet, MapPin } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public blood-donation appeals (PF-W2). Current donation drives from the anonymous gateway
 * lane GET /internal/v1/public/gateway/blood/drives — allow-listed facts only, no donor PII.
 * Renders nothing when there are no active appeals (never an empty box). Reachable without
 * sign-in; registering as a donor requires sign-in.
 */

interface Drive {
  title?: string;
  description?: string;
  driveType?: string;
  locationRef?: string;
  jurisdiction?: string;
  startAt?: string;
  endAt?: string;
}

function formatWindow(startAt?: string, endAt?: string): string | null {
  const fmt = (iso?: string) => {
    if (!iso) return null;
    const d = new Date(iso);
    return Number.isNaN(d.getTime())
      ? null
      : d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  };
  const s = fmt(startAt);
  const e = fmt(endAt);
  if (s && e) return `${s} – ${e}`;
  return s ?? e;
}

export function BloodDonationAppeals() {
  const [drives, setDrives] = useState<Drive[]>([]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const res = await apiClient.get<Drive[]>("/internal/v1/public/gateway/blood/drives");
        if (!cancelled) setDrives(Array.isArray(res) ? res : []);
      } catch {
        /* blood appeals are supplementary — silent on failure, never blocks emergency */
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (drives.length === 0) return null;

  return (
    <section className="mt-8" aria-labelledby="blood-appeals-h" data-testid="blood-appeals">
      <h2 id="blood-appeals-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
        <Droplet className="h-5 w-5 text-red-600" aria-hidden /> Blood donation appeals
      </h2>
      <p className="mt-1 text-sm text-slate-600">
        Current appeals for blood donors. No sign-in needed to see them; sign in to register as a
        donor.
      </p>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        {drives.map((d, i) => {
          const win = formatWindow(d.startAt, d.endAt);
          return (
            <div key={`${d.title}-${i}`} className="rounded-xl border border-red-200 bg-white p-4">
              <p className="font-semibold text-slate-900">{d.title}</p>
              {d.description && <p className="mt-1 text-[13px] leading-relaxed text-slate-600">{d.description}</p>}
              <div className="mt-2 flex flex-wrap gap-3 text-xs text-slate-500">
                {(d.locationRef || d.jurisdiction) && (
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="h-3.5 w-3.5" aria-hidden />
                    {[d.locationRef, d.jurisdiction].filter(Boolean).join(", ")}
                  </span>
                )}
                {win && <span>{win}</span>}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
