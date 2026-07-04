"use client";

/**
 * Virtual hospital directory — /work/telemedicine/virtual-hospitals
 *
 * Directory of the strategic virtual service-delivery institutions (12
 * strategic + one per province). These are governed pools of clinical
 * capability — never duplicate copies of physical facilities. Every card
 * shows an honest substrate status; no fake queue counts or live metrics.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Building2, Globe2, Search } from "lucide-react";
import {
  ALL_VIRTUAL_HOSPITALS,
  OPERATING_MODEL_LABELS,
  SUBSTRATE_STATUS_LABELS,
  type VirtualHospitalLevel,
} from "@/lib/telemedicine/virtual-hospitals";

const LEVEL_FILTERS: Array<{ value: VirtualHospitalLevel | "ALL"; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "NATIONAL", label: "National" },
  { value: "PROVINCIAL", label: "Provincial" },
  { value: "SPECIALIST", label: "Specialist" },
  { value: "PROGRAMME", label: "Programme" },
  { value: "COMMUNITY", label: "Community" },
  { value: "EMERGENCY", label: "Emergency" },
  { value: "LEARNING", label: "Learning" },
];

const STATUS_BADGE: Record<string, string> = {
  ROUTABLE_VIA_EXISTING_SEAMS: "bg-emerald-50 text-emerald-700 border-emerald-200",
  CONFIGURED_AWAITING_SUBSTRATE: "bg-amber-50 text-amber-700 border-amber-200",
  OPERATIONAL: "bg-teal-50 text-teal-700 border-teal-200",
};

export default function VirtualHospitalDirectoryPage() {
  const [level, setLevel] = useState<VirtualHospitalLevel | "ALL">("ALL");
  const [query, setQuery] = useState("");

  const hospitals = useMemo(() => {
    const q = query.trim().toLowerCase();
    return ALL_VIRTUAL_HOSPITALS.filter((vh) => {
      if (level !== "ALL" && vh.level !== level) return false;
      if (!q) return true;
      return (
        vh.name.toLowerCase().includes(q) ||
        vh.serviceLines.some((s) => s.toLowerCase().includes(q)) ||
        vh.catchment.toLowerCase().includes(q)
      );
    });
  }, [level, query]);

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Telemedicine operating model
        </Link>
        <div className="flex items-center gap-3">
          <Building2 className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Virtual hospitals</h1>
            <p className="text-sm text-slate-500">
              Virtual service-delivery institutions with their own identity class — deliberately
              selective (never one per physical facility). Physical facilities connect as access
              points, requesters, referral destinations and explicit staff contributors.
            </p>
          </div>
        </div>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-72 max-w-full">
          <Search className="pointer-events-none absolute left-2 top-2.5 h-4 w-4 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search name, service line, catchment…"
            className="w-full rounded-md border border-slate-300 py-2 pl-8 pr-3 text-sm focus:border-teal-500 focus:outline-none"
          />
        </div>
        <div className="flex flex-wrap gap-1">
          {LEVEL_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setLevel(f.value)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
                level === f.value
                  ? "border-teal-600 bg-teal-600 text-white"
                  : "border-slate-200 bg-white text-slate-600 hover:border-teal-300"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {hospitals.length === 0 ? (
        <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
          No virtual hospitals match this filter.
        </p>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {hospitals.map((vh) => (
            <Link
              key={vh.id}
              href={`/work/telemedicine/virtual-hospitals/${vh.id}`}
              className="group rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:border-teal-300 hover:shadow"
            >
              <div className="mb-2 flex items-start justify-between gap-3">
                <h2 className="text-base font-semibold text-slate-800 group-hover:text-teal-700">
                  {vh.name}
                </h2>
                <span
                  className={`shrink-0 rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${STATUS_BADGE[vh.substrateStatus]}`}
                >
                  {SUBSTRATE_STATUS_LABELS[vh.substrateStatus]}
                </span>
              </div>
              <p className="mb-3 line-clamp-2 text-sm text-slate-500">{vh.purpose}</p>
              <div className="flex flex-wrap gap-2 text-[11px]">
                <span className="rounded bg-slate-100 px-2 py-0.5 text-slate-600">{vh.level}</span>
                <span className="rounded bg-slate-100 px-2 py-0.5 text-slate-600">
                  {OPERATING_MODEL_LABELS[vh.operatingModel]}
                </span>
                <span className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-slate-600">
                  <Globe2 className="h-3 w-3" /> {vh.catchment}
                </span>
              </div>
              <p className="mt-3 text-xs text-slate-400">
                {vh.serviceLines.length} service lines · {vh.queues.length} queue specs ·{" "}
                {vh.crossBorderParticipation
                  ? "diaspora participation policy-gated"
                  : "no cross-border participation"}
              </p>
            </Link>
          ))}
        </div>
      )}

      <footer className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
        Queue counts, rosters and live workload require the virtual-hospital backend substrate
        (handoff HO-2 in the operating-model capability map). Institutions marked{" "}
        <span className="font-medium">routable today</span> are reachable through the real
        teleconsult routing seams (specialty-pool targets); the rest are configuration awaiting
        substrate — nothing here fakes runtime capability.
      </footer>
    </div>
  );
}
