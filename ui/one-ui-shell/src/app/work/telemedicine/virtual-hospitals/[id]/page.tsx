"use client";

/**
 * Virtual hospital detail — /work/telemedicine/virtual-hospitals/[id]
 *
 * Answers the operating-model questions explicitly: what this institution is,
 * who operates it, how facilities relate to it (access/referral vs explicit
 * staffing), which session modes it opens, what cross-border privileges are
 * gated, and — honestly — which capabilities await the backend substrate.
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  ArrowUpRight,
  Building2,
  Globe2,
  Landmark,
  ListChecks,
  Pin,
  PinOff,
  ShieldCheck,
  Users,
} from "lucide-react";
import {
  getVirtualHospital,
  LINKED_FACILITY_ROLE_LABELS,
  OPERATING_MODEL_LABELS,
  SUBSTRATE_STATUS_LABELS,
} from "@/lib/telemedicine/virtual-hospitals";
import { getSessionMode, SESSION_MODE_STATUS_LABELS } from "@/lib/telemedicine/session-modes";
import { PrivacyBadge } from "@/components/telemedicine/PrivacyBadge";
import {
  addPin,
  isPinned,
  loadPins,
  removePin,
  type PinnedTarget,
} from "@/lib/telemedicine/pinning";

export default function VirtualHospitalDetailPage() {
  const params = useParams<{ id: string }>();
  const id = typeof params?.id === "string" ? params.id : "";
  const vh = getVirtualHospital(id);
  const [pins, setPins] = useState<PinnedTarget[]>([]);

  useEffect(() => {
    setPins(loadPins());
  }, []);

  if (!vh) {
    return (
      <div className="mx-auto max-w-4xl p-6">
        <Link
          href="/work/telemedicine/virtual-hospitals"
          className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Virtual hospitals
        </Link>
        <div className="rounded-lg border border-slate-200 bg-white p-8 text-center">
          <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-amber-500" />
          <p className="text-sm text-slate-600">
            No virtual hospital with id <span className="font-mono">{id}</span> is configured.
          </p>
        </div>
      </div>
    );
  }

  const pinned = isPinned(pins, "VIRTUAL_HOSPITAL", vh.id);
  const togglePin = () => {
    setPins((current) =>
      pinned
        ? removePin(current, "VIRTUAL_HOSPITAL", vh.id)
        : addPin(current, {
            kind: "VIRTUAL_HOSPITAL",
            id: vh.id,
            label: vh.name,
            detail: vh.level,
          }),
    );
  };

  const routable = vh.substrateStatus === "ROUTABLE_VIA_EXISTING_SEAMS";

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine/virtual-hospitals"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Virtual hospitals
        </Link>
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <Building2 className="h-7 w-7 text-teal-600" />
            <div>
              <h1 className="text-2xl font-semibold text-slate-900">{vh.name}</h1>
              <p className="text-sm text-slate-500">{vh.purpose}</p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button
              onClick={togglePin}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:border-teal-400 hover:text-teal-700"
            >
              {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
              {pinned ? "Unpin" : "Pin"}
            </button>
            {routable ? (
              <Link
                href="/telemedicine/new"
                className="inline-flex items-center gap-1.5 rounded-md bg-teal-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-700"
              >
                <ArrowUpRight className="h-4 w-4" /> Create request
              </Link>
            ) : null}
          </div>
        </div>
      </header>

      {/* Identity & governance */}
      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-800">
            <Landmark className="h-5 w-5 text-teal-600" /> Identity &amp; accountability
          </h2>
          <dl className="space-y-2 text-sm">
            <Row label="Virtual Hospital ID" value={<span className="font-mono">{vh.id}</span>} />
            <Row label="Identity class" value="Virtual service-delivery entity (never a physical facility record)" />
            <Row label="Level" value={vh.level} />
            <Row label="Operating model" value={OPERATING_MODEL_LABELS[vh.operatingModel]} />
            <Row label="Operating authority" value={vh.operatingAuthority} />
            <Row
              label="Regulatory status"
              value="Policy-configurable — pending regulatory determination (telehealth regulation is evolving; no legal status is presumed)"
            />
            <Row
              label="Substrate status"
              value={
                <span className={routable ? "text-emerald-700" : "text-amber-700"}>
                  {SUBSTRATE_STATUS_LABELS[vh.substrateStatus]}
                </span>
              }
            />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-800">
            <Globe2 className="h-5 w-5 text-teal-600" /> Catchment &amp; facility relationships
          </h2>
          <p className="mb-3 text-sm text-slate-600">{vh.catchment}</p>
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">
            Facility link roles (explicit — a link never auto-assigns facility staff)
          </p>
          <ul className="flex flex-wrap gap-2">
            {vh.linkedFacilityRoles.map((role) => (
              <li
                key={role}
                className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs text-slate-600"
              >
                {LINKED_FACILITY_ROLE_LABELS[role]}
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-slate-400">
            Facility-to-institution mappings live in the pending backend substrate; today
            facilities reach this institution through the teleconsult request flow.
          </p>
        </div>
      </section>

      {/* Service lines & staffing */}
      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-800">
            <ListChecks className="h-5 w-5 text-teal-600" /> Service lines
          </h2>
          <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
            {vh.serviceLines.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-800">
            <Users className="h-5 w-5 text-teal-600" /> Staffing doctrine
          </h2>
          <p className="mb-2 text-sm text-slate-600">
            Staffed by real verified people (Varapi identity, home affiliation + explicit virtual
            privilege and roster assignment). Expected pool cadres:
          </p>
          <ul className="flex flex-wrap gap-2">
            {vh.providerPoolCadres.map((cadre) => (
              <li
                key={cadre}
                className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs text-slate-600"
              >
                {cadre}
              </li>
            ))}
          </ul>
          <p className="mt-3 rounded-md bg-amber-50 p-2 text-xs text-amber-800">
            Rosters, availability, duty status and workload dashboards await the roster substrate —
            no provider is auto-assigned virtual duties because their facility is linked.
          </p>
        </div>
      </section>

      {/* Session modes */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-800">
          <ShieldCheck className="h-5 w-5 text-teal-600" /> Session modes this institution opens
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {vh.sessionModeIds.map((modeId) => {
            const mode = getSessionMode(modeId);
            if (!mode) return null;
            return (
              <div key={modeId} className="rounded-md border border-slate-200 p-3">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-slate-800">{mode.name}</span>
                  <span className="text-[11px] text-slate-400">
                    {SESSION_MODE_STATUS_LABELS[mode.status]}
                  </span>
                </div>
                <PrivacyBadge level={mode.defaultIdentityVisibility} />
              </div>
            );
          })}
        </div>
        <p className="mt-3 text-xs text-slate-400">
          Full governance matrix:{" "}
          <Link href="/work/telemedicine/session-modes" className="text-teal-600 hover:underline">
            session modes
          </Link>
          .
        </p>
      </section>

      {/* Queues + routing today */}
      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 text-base font-semibold text-slate-800">Queue specifications</h2>
          <ul className="space-y-2 text-sm">
            {vh.queues.map((q) => (
              <li key={q.id} className="flex items-center justify-between gap-2">
                <span className="text-slate-700">{q.name}</span>
                <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">
                  awaiting backend
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-slate-400">
            The virtual queue engine does not exist yet (routing metadata ≠ queue engine — see the
            capability map). These specs seed that build; no counts are shown because none exist.
          </p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-3 text-base font-semibold text-slate-800">How to reach it today</h2>
          {vh.currentRoutingSeams.length > 0 ? (
            <ul className="space-y-3 text-sm">
              {vh.currentRoutingSeams.map((seam) => (
                <li key={`${seam.routingType}:${seam.targetRef}`} className="rounded-md border border-emerald-200 bg-emerald-50 p-3">
                  <p className="font-medium text-emerald-800">
                    {seam.routingType} → <span className="font-mono">{seam.targetRef}</span>
                  </p>
                  <p className="mt-1 text-xs text-emerald-700">{seam.note}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              Not routable yet: pool/on-call routing types return an honest 501 from the BFF until
              the pool directory backend exists (handoff HO-1). No pretend entry path is offered.
            </p>
          )}
          <div className="mt-3 text-xs text-slate-400">
            Cross-border participation:{" "}
            {vh.crossBorderParticipation ? (
              <>
                policy-gated — privileges limited to{" "}
                <span className="text-slate-600">
                  {vh.allowedCrossBorderPrivileges
                    .map((p) => p.replaceAll("_", " ").toLowerCase())
                    .join("; ")}
                </span>
              </>
            ) : (
              "not in scope for this institution"
            )}
          </div>
        </div>
      </section>

      {/* Billing models */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-3 text-base font-semibold text-slate-800">Billing / business models</h2>
        <ul className="flex flex-wrap gap-2">
          {vh.billingModels.map((model) => (
            <li
              key={model}
              className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs text-slate-600"
            >
              {model.replaceAll("_", " ").toLowerCase()}
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-slate-400">
          Teleconsult completion already triggers real COSTA billing with patient/facility category
          context; per-institution billing configuration awaits the substrate.
        </p>
      </section>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="shrink-0 text-slate-500">{label}</dt>
      <dd className="text-right text-slate-800">{value}</dd>
    </div>
  );
}
