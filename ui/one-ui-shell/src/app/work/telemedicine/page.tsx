"use client";

/**
 * Telemedicine Operating Model hub — /work/telemedicine
 *
 * National telemedicine as a clinical operating capability: live operational
 * signals (RTC health, facility SLA backlog, lifecycle analytics) plus
 * navigation into the virtual-hospital directory, routing directory, clinical
 * groups, session-mode governance and operations surfaces.
 *
 * Live data comes from the real teleconsult/analytics BFF endpoints. Anything
 * without a backend renders an honest deferred state — never fake metrics.
 */

import Link from "next/link";
import {
  Activity,
  Building2,
  Compass,
  Layers,
  MonitorCheck,
  Network,
  Pin,
  Plus,
  ShieldCheck,
  Stethoscope,
  Users,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useTelemedicineOpsSla, useTelemedicineRtcHealth } from "@/hooks/queries/useTelemedicine";
import { useTelemedicineSlaAggregates } from "@/hooks/queries/useTelemedicineAnalytics";
import { ALL_VIRTUAL_HOSPITALS } from "@/lib/telemedicine/virtual-hospitals";
import { CLINICAL_SESSION_MODES } from "@/lib/telemedicine/session-modes";
import { loadPins, type PinnedTarget } from "@/lib/telemedicine/pinning";

const SECTIONS = [
  {
    href: "/work/telemedicine/virtual-hospitals",
    icon: Building2,
    title: "Virtual hospitals",
    description:
      "Governed pools of clinical capability — national, provincial, specialist, programme, emergency, community and learning institutions.",
  },
  {
    href: "/work/telemedicine/routing",
    icon: Compass,
    title: "Routing directory",
    description:
      "Find the right clinical capability: person, facility, workspace or virtual hospital — then open a governed request.",
  },
  {
    href: "/work/telemedicine/groups",
    icon: Users,
    title: "Clinical groups",
    description:
      "Discovery-and-routing group taxonomy with fail-closed governance. Creation stays blocked until the governed backend ships.",
  },
  {
    href: "/work/telemedicine/session-modes",
    icon: Layers,
    title: "Session modes",
    description:
      "Ten clinical governance modes — encounter, advice, MDT, case presentation, case notes, audit, teaching, emergency, diagnostics.",
  },
  {
    href: "/work/telemedicine/operations",
    icon: MonitorCheck,
    title: "Operations & helpdesk",
    description:
      "RTC transport health, facility SLA backlog, lifecycle analytics and specialty workbench.",
  },
];

export default function TelemedicineOperatingModelPage() {
  const facility = useFacilityStore((s) => s.facility);
  const rtcHealth = useTelemedicineRtcHealth();
  const opsSla = useTelemedicineOpsSla(facility?.id ?? null);
  const analytics = useTelemedicineSlaAggregates();
  const [pins, setPins] = useState<PinnedTarget[]>([]);

  useEffect(() => {
    setPins(loadPins());
  }, []);

  const rtc = rtcHealth.data?.data;
  const ops = opsSla.data?.data;
  const agg = analytics.data?.data;
  const routableCount = ALL_VIRTUAL_HOSPITALS.filter(
    (vh) => vh.substrateStatus === "ROUTABLE_VIA_EXISTING_SEAMS",
  ).length;
  const templatedModes = CLINICAL_SESSION_MODES.filter((m) => m.status !== "PLANNED").length;

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <Stethoscope className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Telemedicine operating model</h1>
            <p className="text-sm text-slate-500">
              Telemedicine is a full clinical encounter — equivalent to a physical encounter,
              delivered virtually or hybrid. Virtual hospitals are service architecture, not
              duplicate building architecture.
            </p>
          </div>
        </div>
        <Link
          href="/telemedicine/new"
          className="inline-flex shrink-0 items-center gap-2 rounded-md bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
        >
          <Plus className="h-4 w-4" /> New teleconsult request
        </Link>
      </header>

      {/* Live operational signals — real endpoints only */}
      <section className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <Activity className="h-4 w-4 text-teal-600" /> RTC transport
          </div>
          {rtcHealth.isLoading ? (
            <p className="text-sm text-slate-400">Checking rtc-gateway…</p>
          ) : rtc ? (
            <ul className="space-y-1 text-sm text-slate-600">
              <li>
                Provider: <span className="font-medium text-slate-800">{rtc.provider}</span>
              </li>
              <li>
                LiveKit configured:{" "}
                <span className={rtc.livekitConfigured ? "text-emerald-600" : "text-amber-600"}>
                  {rtc.livekitConfigured ? "yes" : "no"}
                </span>
              </li>
              <li>
                Production ready:{" "}
                <span className={rtc.productionReady ? "text-emerald-600" : "text-amber-600"}>
                  {rtc.productionReady ? "yes" : "no"}
                </span>
              </li>
              {typeof rtc.activeSessions === "number" ? (
                <li>
                  Active sessions:{" "}
                  <span className="font-medium text-slate-800">{rtc.activeSessions}</span>
                </li>
              ) : null}
            </ul>
          ) : (
            <p className="text-sm text-slate-400">RTC health unavailable from the gateway.</p>
          )}
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <Network className="h-4 w-4 text-teal-600" /> Facility telemedicine backlog
          </div>
          {!facility ? (
            <p className="text-sm text-slate-400">
              Select a facility context to see its teleconsult backlog.
            </p>
          ) : opsSla.isLoading ? (
            <p className="text-sm text-slate-400">Loading ops snapshot…</p>
          ) : ops ? (
            <ul className="space-y-1 text-sm text-slate-600">
              <li>
                Submitted referral backlog:{" "}
                <span className="font-medium text-slate-800">{ops.submittedReferralBacklog}</span>
              </li>
              <li>
                In-progress sessions:{" "}
                <span className="font-medium text-slate-800">{ops.inProgressSessions}</span>
              </li>
              <li>
                Scheduled sessions:{" "}
                <span className="font-medium text-slate-800">{ops.scheduledSessions}</span>
              </li>
              <li>
                Overdue scheduled:{" "}
                <span
                  className={
                    ops.overdueScheduledSessions > 0
                      ? "font-medium text-red-600"
                      : "font-medium text-slate-800"
                  }
                >
                  {ops.overdueScheduledSessions}
                </span>
              </li>
            </ul>
          ) : (
            <p className="text-sm text-slate-400">Ops snapshot unavailable for this facility.</p>
          )}
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
            <ShieldCheck className="h-4 w-4 text-teal-600" /> Governance coverage
          </div>
          <ul className="space-y-1 text-sm text-slate-600">
            <li>
              Virtual hospitals configured:{" "}
              <span className="font-medium text-slate-800">{ALL_VIRTUAL_HOSPITALS.length}</span>{" "}
              <span className="text-xs text-slate-400">({routableCount} routable today)</span>
            </li>
            <li>
              Session modes governed:{" "}
              <span className="font-medium text-slate-800">
                {templatedModes}/{CLINICAL_SESSION_MODES.length}
              </span>{" "}
              <span className="text-xs text-slate-400">(rest planned, gaps recorded)</span>
            </li>
            <li>
              Lifecycle events accepted:{" "}
              {analytics.isLoading ? (
                <span className="text-slate-400">loading…</span>
              ) : typeof agg?.eventsAccepted === "number" ? (
                <span className="font-medium text-slate-800">{agg.eventsAccepted}</span>
              ) : (
                <span className="text-slate-400">analytics unavailable</span>
              )}
            </li>
          </ul>
        </div>
      </section>

      {/* Pinned targets */}
      <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
          <Pin className="h-4 w-4 text-teal-600" /> Pinned routing targets
        </div>
        {pins.length === 0 ? (
          <p className="text-sm text-slate-400">
            Nothing pinned yet. Pin frequent people, facilities, workspaces or virtual hospitals
            from the routing directory — pinning speeds up discovery and never bypasses policy.
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {pins.map((pin) => (
              <li
                key={`${pin.kind}:${pin.id}`}
                className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-700"
              >
                <span className="font-medium">{pin.label}</span>
                {pin.detail ? <span className="text-slate-400"> · {pin.detail}</span> : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Sections */}
      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {SECTIONS.map(({ href, icon: Icon, title, description }) => (
          <Link
            key={href}
            href={href}
            className="group rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:border-teal-300 hover:shadow"
          >
            <div className="mb-2 flex items-center gap-2">
              <Icon className="h-5 w-5 text-teal-600" />
              <h2 className="text-base font-semibold text-slate-800 group-hover:text-teal-700">
                {title}
              </h2>
            </div>
            <p className="text-sm text-slate-500">{description}</p>
          </Link>
        ))}
        <Link
          href="/telemedicine"
          className="group rounded-lg border border-dashed border-slate-300 bg-slate-50 p-5 transition hover:border-teal-300"
        >
          <div className="mb-2 flex items-center gap-2">
            <Stethoscope className="h-5 w-5 text-slate-500" />
            <h2 className="text-base font-semibold text-slate-700 group-hover:text-teal-700">
              Telemedicine hub (sessions)
            </h2>
          </div>
          <p className="text-sm text-slate-500">
            The live session workspace: referral-aware scheduling, session join and orchestration.
          </p>
        </Link>
      </section>
    </div>
  );
}
