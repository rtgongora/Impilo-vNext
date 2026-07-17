"use client";

/**
 * Public facility profile (disclosure-limited).
 *
 * Consumes GET /internal/v1/public/gateway/find-care/facilities/{id} — the TUSO
 * registry's redacted view: name/type/level/location, the capabilities the
 * facility actually offers (with operating hours when held), coordinates for
 * directions, and any "report to" service-point hint carried in capability
 * metadata. Operating status is shown honestly as unverified; the verified-
 * provider layer (Varapi) is a later wave and is surfaced only as a real
 * "verify a professional" next step, never a fabricated roster.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  MapPin,
  Navigation,
  Clock,
  Stethoscope,
  ShieldCheck,
  AlertTriangle,
  Building2,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { FacilityCapability, FacilityProfile } from "@/lib/find-care/types";
import { directionsUrl, humanizeEnum } from "@/lib/find-care/format";
import { serviceTokenLabel } from "@/lib/find-care/service-chips";

const SERVICE_POINT_KEYS = ["servicePoint", "service_point", "reportTo", "report_to", "entryPoint", "location", "department"];

/** Pull a human "report to X" hint from free-form capability metadata, if present. */
function servicePointHint(metadata: Record<string, unknown> | null): string | null {
  if (!metadata) return null;
  for (const key of SERVICE_POINT_KEYS) {
    const value = metadata[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return null;
}

/** Render a free-form operating-hours map as "Label: value" lines, when present. */
function operatingHoursLines(hours: Record<string, unknown> | null): { label: string; value: string }[] {
  if (!hours) return [];
  return Object.entries(hours)
    .filter(([, v]) => v != null && (typeof v === "string" || typeof v === "number" || typeof v === "boolean"))
    .map(([k, v]) => ({ label: humanizeEnum(k) ?? k, value: String(v) }));
}

interface FindCareFacilityDetailProps {
  facilityId: string;
}

export function FindCareFacilityDetail({ facilityId }: FindCareFacilityDetailProps) {
  const [profile, setProfile] = useState<FacilityProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    setNotFound(false);
    apiClient
      .get<FacilityProfile>(`/internal/v1/public/gateway/find-care/facilities/${facilityId}`)
      .then((res) => {
        if (!cancelled) setProfile(res);
      })
      .catch((err) => {
        if (cancelled) return;
        if ((err as { status?: number })?.status === 404) setNotFound(true);
        else setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [facilityId]);

  if (loading) {
    return (
      <div className="grid place-items-center py-16 text-slate-500" role="status">
        <span className="flex items-center gap-2 text-sm">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Loading facility…
        </span>
      </div>
    );
  }

  if (notFound || !profile) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
        <p className="font-medium text-slate-900">
          {notFound ? "We couldn't find that facility." : "This facility profile is temporarily unavailable."}
        </p>
        {error && !notFound && (
          <p className="mt-1 text-sm text-slate-500">Please try again shortly.</p>
        )}
        <Link
          href="/welcome/find-care"
          className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Back to search
        </Link>
      </div>
    );
  }

  const meta = [
    humanizeEnum(profile.facilityType),
    humanizeEnum(profile.level),
    humanizeEnum(profile.facilityCategory),
  ]
    .filter(Boolean)
    .join(" · ");
  const place = [profile.district, profile.province].filter(Boolean).join(", ");
  const directions = directionsUrl(profile.latitude, profile.longitude);
  const capabilities: FacilityCapability[] = (profile.capabilities ?? []).filter((c) => c.active);

  return (
    <div className="space-y-6">
      <Link
        href="/welcome/find-care"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Back to search
      </Link>

      {/* Header */}
      <header className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-start gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-700">
            <Building2 className="h-6 w-6" aria-hidden />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-slate-900">{profile.name ?? "Health facility"}</h1>
            {meta && <p className="mt-1 text-sm text-slate-600">{meta}</p>}
            {place && (
              <p className="mt-1 flex items-center gap-1.5 text-sm text-slate-500">
                <MapPin className="h-3.5 w-3.5" aria-hidden />
                {place}
              </p>
            )}
          </div>
        </div>

        {/* Honest status + directions */}
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-md bg-amber-50 px-2.5 py-1 text-xs text-amber-700">
            <Clock className="h-3.5 w-3.5" aria-hidden />
            {profile.status ? `${humanizeEnum(profile.status)} · hours not verified` : "Opening hours not verified"}
          </span>
          {directions && (
            <a
              href={directions}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
            >
              <Navigation className="h-4 w-4" aria-hidden />
              Get directions
            </a>
          )}
        </div>
        {(profile.latitude == null || profile.longitude == null) && (
          <p className="mt-2 text-xs text-slate-500">This facility does not have mapped coordinates yet.</p>
        )}
      </header>

      {/* Services offered (registry truth) */}
      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900">
          <Stethoscope className="h-4 w-4 text-slate-400" aria-hidden />
          Services offered
        </h2>
        {capabilities.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">
            No published services are listed for this facility yet.
          </p>
        ) : (
          <ul className="mt-3 divide-y divide-slate-100">
            {capabilities.map((cap, i) => {
              const hint = servicePointHint(cap.metadata);
              const hours = operatingHoursLines(cap.operatingHours);
              const name = cap.name ?? serviceTokenLabel(cap.capabilityCode) ?? cap.capabilityCode ?? "Service";
              return (
                <li key={cap.id ?? `${cap.capabilityCode}-${i}`} className="py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-slate-900">{name}</span>
                    {cap.ziboValidated && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs text-emerald-700">
                        <ShieldCheck className="h-3 w-3" aria-hidden />
                        Validated
                      </span>
                    )}
                    {cap.capabilityType && (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                        {humanizeEnum(cap.capabilityType)}
                      </span>
                    )}
                  </div>
                  {hint && (
                    <p className="mt-1 text-sm text-slate-600">
                      When you arrive, report to <span className="font-medium">{hint}</span>.
                    </p>
                  )}
                  {hours.length > 0 && (
                    <dl className="mt-1.5 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500">
                      {hours.map((h) => (
                        <div key={h.label} className="flex gap-1">
                          <dt className="font-medium text-slate-600">{h.label}:</dt>
                          <dd>{h.value}</dd>
                        </div>
                      ))}
                    </dl>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        <p className="mt-3 flex items-start gap-1.5 text-xs text-slate-500">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" aria-hidden />
          Service availability can change. Where operating hours aren&apos;t shown, they are not yet
          verified in the register — please call ahead or confirm on arrival.
        </p>
      </section>

      {/* Verified-provider layer — deferred (Varapi wiring is a later wave). Real next step only. */}
      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900">
          <ShieldCheck className="h-4 w-4 text-slate-400" aria-hidden />
          Health professionals
        </h2>
        <p className="mt-2 text-sm text-slate-600">
          Individual verified providers for this facility aren&apos;t listed here yet. You can independently
          confirm whether a specific practitioner is registered.
        </p>
        <Link
          href="/verify/practitioner"
          className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
        >
          Verify a health professional
        </Link>
      </section>

      {/* Sign-in continuity */}
      <p className="text-xs text-slate-500">
        To book an appointment or save this facility,{" "}
        <Link
          href={`/auth/login?returnTo=${encodeURIComponent(`/welcome/find-care/${facilityId}`)}`}
          className="font-medium text-emerald-700 hover:text-emerald-800"
        >
          sign in
        </Link>{" "}
        or{" "}
        <Link href="/auth/register/contact" className="font-medium text-emerald-700 hover:text-emerald-800">
          create an account
        </Link>
        .
      </p>
    </div>
  );
}
