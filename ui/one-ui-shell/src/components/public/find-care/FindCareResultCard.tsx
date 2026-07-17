"use client";

/**
 * Unified find-care result card.
 *
 * Answers, for one facility, the questions a person actually has:
 *  - Does it offer what I asked for?      (service-match badge)
 *  - Is it open?                          (honest: "hours not verified" — never a fake "open now")
 *  - How far is it?                       (distance/ETA when known, else an honest prompt)
 *  - Can I get remote care?               (only when truly known — otherwise silent)
 *  - What can I do next?                  (only actions that actually work)
 *
 * Every action either performs a real navigation (View details, Get directions)
 * or routes to a clearly-labelled next step behind sign-in (Save / Book). No
 * action ever shows a fabricated success.
 */

import Link from "next/link";
import { MapPin, Navigation, CheckCircle2, HelpCircle, Video, Building2 } from "lucide-react";
import type { CareResult } from "@/lib/find-care/types";
import { serviceTokenLabel } from "@/lib/find-care/service-chips";
import { directionsUrl, formatDistanceLine, humanizeEnum } from "@/lib/find-care/format";
import { FindCareAccessActions } from "./FindCareAccessActions";

interface FindCareResultCardProps {
  result: CareResult;
  /** Whether the person shared a location this search (drives the distance prompt). */
  locationShared: boolean;
  /** True when the last search confirmed a live ACTIVE virtual service (drives "Start virtual care"). */
  virtualCareAvailable?: boolean;
  /** Called when the card is opened, so the journey store can remember the selection. */
  onOpen?: (facilityId: number) => void;
  /** Highlight state (e.g. selected on the map). */
  active?: boolean;
}

export function FindCareResultCard({
  result,
  locationShared,
  virtualCareAvailable = false,
  onOpen,
  active = false,
}: FindCareResultCardProps) {
  const id = result.facilityId;
  const detailHref = id != null ? `/welcome/find-care/${id}` : null;
  const directions = directionsUrl(result.latitude, result.longitude);
  const distanceLine = formatDistanceLine(result);
  const matchedLabel = serviceTokenLabel(result.matchedService);

  const meta = [
    humanizeEnum(result.facilityType),
    humanizeEnum(result.level),
    result.district,
    result.province,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <article
      className={`rounded-xl border bg-white p-4 transition-colors ${
        active ? "border-emerald-500 ring-1 ring-emerald-500" : "border-slate-200"
      }`}
      aria-label={result.name ?? "Health facility"}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="flex items-center gap-2 font-semibold text-slate-900">
            <Building2 className="h-4 w-4 shrink-0 text-slate-400" aria-hidden />
            <span className="truncate">{result.name ?? "Unnamed facility"}</span>
          </h3>
          {meta && <p className="mt-0.5 text-sm text-slate-500">{meta}</p>}
        </div>

        {/* Service match — the primary answer. */}
        {result.serviceMatch ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700">
            <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
            Offers {matchedLabel ?? "this service"}
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
            <HelpCircle className="h-3.5 w-3.5" aria-hidden />
            Directory match
          </span>
        )}
      </div>

      {/* Fact row: distance, open-status, telemedicine — each honest about the unknown. */}
      <dl className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm">
        <div className="flex items-center gap-1.5 text-slate-700">
          <Navigation className="h-3.5 w-3.5 text-slate-400" aria-hidden />
          <dt className="sr-only">Distance</dt>
          <dd>
            {distanceLine ? (
              distanceLine
            ) : locationShared ? (
              <span className="text-slate-500">Distance unavailable — no map coordinates</span>
            ) : (
              <span className="text-slate-500">Distance unavailable — share your location</span>
            )}
          </dd>
        </div>

        <div className="flex items-center gap-1.5">
          <dt className="sr-only">Open status</dt>
          {/* Register status is NOT a live signal — say so plainly, never "open now". */}
          <dd className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2 py-0.5 text-xs text-amber-700">
            {result.operationalStatus
              ? `${humanizeEnum(result.operationalStatus)} · hours not verified`
              : "Opening hours not verified"}
          </dd>
        </div>

        {/* Only surface telemedicine when the register truly knows it (currently never true). */}
        {result.telemedicineAvailable === true && (
          <div className="flex items-center gap-1.5 text-slate-700">
            <Video className="h-3.5 w-3.5 text-slate-400" aria-hidden />
            <dt className="sr-only">Telemedicine</dt>
            <dd>Remote consultations available</dd>
          </div>
        )}
      </dl>

      {/* Actions — only ones that actually work. */}
      <div className="mt-4 flex flex-wrap gap-2">
        {detailHref && (
          <Link
            href={detailHref}
            onClick={() => id != null && onOpen?.(id)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            View details
          </Link>
        )}
        {directions && (
          <a
            href={directions}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            <MapPin className="h-4 w-4" aria-hidden />
            Get directions
          </a>
        )}
        {/* Access-to-care actions — each routes to a real next step (sign-in continuity or the
            facility's request panel), never a fabricated confirmation. */}
        {id != null && (
          <FindCareAccessActions
            facilityId={id}
            facilityName={result.name}
            serviceToken={result.matchedService}
            virtualCareAvailable={virtualCareAvailable}
            variant="compact"
          />
        )}
      </div>
    </article>
  );
}
