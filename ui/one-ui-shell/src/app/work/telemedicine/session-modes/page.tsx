"use client";

/**
 * Session mode governance — /work/telemedicine/session-modes
 *
 * The ten clinical session modes and what each drives: identity visibility,
 * consent, recording, participant roles, documentation target and audit
 * depth. Modes backed by a live W0 session template show the REAL template
 * from the registry (`GET /internal/v1/session-templates/{mode}`), including
 * its Khuluma notification keys; planned modes state their gap honestly.
 */

import Link from "next/link";
import { useState } from "react";
import {
  ArrowLeft,
  BellRing,
  CheckCircle2,
  CircleDashed,
  FileText,
  Layers,
  Loader2,
  Mic,
  ShieldCheck,
  Users,
} from "lucide-react";
import {
  CLINICAL_SESSION_MODES,
  SESSION_MODE_STATUS_LABELS,
  type ClinicalSessionMode,
} from "@/lib/telemedicine/session-modes";
import { PRIVACY_LEVELS, PRIVACY_LEVEL_ORDER } from "@/lib/telemedicine/privacy";
import { PrivacyBadge } from "@/components/telemedicine/PrivacyBadge";
import { useSessionTemplateDetail, useSessionTemplateModes } from "@/hooks/queries/useSessionModes";

const STATUS_ICON: Record<string, { icon: typeof CheckCircle2; className: string }> = {
  TEMPLATED: { icon: CheckCircle2, className: "text-emerald-600" },
  TEMPLATED_PARTIAL: { icon: CheckCircle2, className: "text-amber-500" },
  PLANNED: { icon: CircleDashed, className: "text-slate-400" },
};

export default function SessionModesPage() {
  const [selectedId, setSelectedId] = useState<string>(CLINICAL_SESSION_MODES[0].id);
  const registryModes = useSessionTemplateModes();
  const selected =
    CLINICAL_SESSION_MODES.find((m) => m.id === selectedId) ?? CLINICAL_SESSION_MODES[0];

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
          <Layers className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Session mode governance</h1>
            <p className="text-sm text-slate-500">
              The selected mode drives UI, identity visibility, documentation template,
              participant rights, consent and audit. Groups route; modes govern.
            </p>
          </div>
        </div>
      </header>

      {/* Registry state — real data */}
      <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center gap-2 text-sm text-slate-600">
          <ShieldCheck className="h-4 w-4 text-teal-600" />
          Governed template registry:{" "}
          {registryModes.isLoading ? (
            <span className="inline-flex items-center gap-1 text-slate-400">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> loading…
            </span>
          ) : (registryModes.data ?? []).length > 0 ? (
            <span>
              {(registryModes.data ?? []).map((mode) => (
                <span
                  key={mode}
                  className="mr-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700"
                >
                  {mode}
                </span>
              ))}
            </span>
          ) : (
            <span className="text-slate-400">
              registry unreachable — mode statuses below reflect the doctrine matrix only
            </span>
          )}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[320px,1fr]">
        {/* Mode selector */}
        <ul className="space-y-1">
          {CLINICAL_SESSION_MODES.map((mode) => {
            const status = STATUS_ICON[mode.status];
            const StatusIcon = status.icon;
            return (
              <li key={mode.id}>
                <button
                  onClick={() => setSelectedId(mode.id)}
                  className={`flex w-full items-center gap-2 rounded-md border px-3 py-2 text-left text-sm transition ${
                    selected.id === mode.id
                      ? "border-teal-500 bg-teal-50 text-teal-800"
                      : "border-slate-200 bg-white text-slate-700 hover:border-teal-200"
                  }`}
                >
                  <StatusIcon className={`h-4 w-4 shrink-0 ${status.className}`} />
                  <span className="flex-1">{mode.name}</span>
                </button>
              </li>
            );
          })}
        </ul>

        {/* Mode detail */}
        <ModeDetail mode={selected} />
      </div>

      {/* Identity visibility doctrine — all six levels in one place */}
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-1 text-base font-semibold text-slate-800">
          Identity visibility &amp; privacy badges
        </h2>
        <p className="mb-4 text-sm text-slate-500">
          Every session surface must show which of these six levels applies. The badge declares
          the intended level; enforcement is Tshepo/OPA policy work (handoff HO-4) and is shown
          honestly per level below.
        </p>
        <ul className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {PRIVACY_LEVEL_ORDER.map((level) => {
            const descriptor = PRIVACY_LEVELS[level];
            return (
              <li key={level} className="rounded-md border border-slate-200 p-3">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <PrivacyBadge level={level} />
                  <span
                    className={`rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                      descriptor.policyEnforced
                        ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                        : "border-amber-200 bg-amber-50 text-amber-700"
                    }`}
                  >
                    {descriptor.policyEnforced ? "policy-enforced" : "declared only — not yet enforced"}
                  </span>
                </div>
                <p className="text-xs text-slate-500">{descriptor.description}</p>
              </li>
            );
          })}
        </ul>
      </section>

      <footer className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
        Adding governed templates for the planned modes (MDT, case presentation, case notes
        review, audit/mortality review, emergency advisory, diagnostics review) is a
        `libs/session-templates` change owned by the W0 session suite — handoff HO-3. Identity
        visibility enforcement is Tshepo/OPA policy work — handoff HO-4. Badges here declare the
        intended level; they do not claim enforcement.
      </footer>
    </div>
  );
}

function ModeDetail({ mode }: { mode: ClinicalSessionMode }) {
  const template = useSessionTemplateDetail(mode.backingTemplate);
  const liveTemplate = template.data;

  return (
    <div className="space-y-4">
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-1 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-800">{mode.name}</h2>
          <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-[11px] font-medium text-slate-600">
            {SESSION_MODE_STATUS_LABELS[mode.status]}
          </span>
        </div>
        <p className="mb-4 text-sm text-slate-500">{mode.description}</p>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
              Identity visibility (default first)
            </p>
            <div className="flex flex-wrap gap-1.5">
              <PrivacyBadge level={mode.defaultIdentityVisibility} />
              {mode.identityVisibility
                .filter((level) => level !== mode.defaultIdentityVisibility)
                .map((level) => (
                  <span key={level} className="opacity-60">
                    <PrivacyBadge level={level} />
                  </span>
                ))}
            </div>
            <p className="mt-1.5 text-xs text-slate-400">
              {PRIVACY_LEVELS[mode.defaultIdentityVisibility].description}
            </p>
          </div>

          <dl className="space-y-1.5 text-sm">
            <DetailRow
              icon={ShieldCheck}
              label="Consent"
              value={mode.consentRequired ? "Required before session" : "Not required (patient may be absent)"}
            />
            <DetailRow
              icon={Mic}
              label="Recording"
              value={
                mode.recordingDefault === "OFF"
                  ? "Off — no exceptions in this mode"
                  : mode.recordingDefault === "OFF_UNLESS_GOVERNED"
                    ? "Off unless a governed exception applies"
                    : "On under recording governance (consent + approval)"
              }
            />
            <DetailRow icon={FileText} label="Documentation target" value={mode.documentationTarget} />
            <DetailRow icon={ShieldCheck} label="Audit depth" value={mode.auditDepth} />
          </dl>
        </div>

        <div className="mt-4">
          <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
            Participant roles
          </p>
          <ul className="flex flex-wrap gap-1.5">
            {mode.participantRoles.map((role) => (
              <li
                key={role}
                className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs text-slate-600"
              >
                <Users className="h-3 w-3" /> {role.replaceAll("_", " ").toLowerCase()}
              </li>
            ))}
          </ul>
        </div>

        {mode.gap ? (
          <p className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
            <span className="font-medium">Honest gap: </span>
            {mode.gap}
          </p>
        ) : null}
      </section>

      {/* Live backing template (real registry data) */}
      {mode.backingTemplate ? (
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="mb-3 text-sm font-semibold text-slate-800">
            Backing governed template:{" "}
            <span className="font-mono text-teal-700">{mode.backingTemplate}</span>
          </h3>
          {template.isLoading ? (
            <p className="inline-flex items-center gap-1 text-sm text-slate-400">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading template from registry…
            </p>
          ) : liveTemplate ? (
            <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
              <dl className="space-y-1.5">
                <DetailRow label="Owning service" value={String(liveTemplate.owningService ?? "—")} />
                <DetailRow label="Join flow" value={String(liveTemplate.joinFlow ?? "—")} />
                <DetailRow
                  label="Max participants"
                  value={String(liveTemplate.maxParticipants ?? "—")}
                />
                <DetailRow
                  label="Recording consent required"
                  value={liveTemplate.recording?.consentRequired ? "yes" : "no"}
                />
                <DetailRow label="Audit depth" value={String(liveTemplate.auditDepth ?? "—")} />
              </dl>
              <div>
                <p className="mb-1.5 flex items-center gap-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                  <BellRing className="h-3.5 w-3.5" /> Khuluma notification keys
                </p>
                {Array.isArray(liveTemplate.khulumaNotificationKeys) &&
                liveTemplate.khulumaNotificationKeys.length > 0 ? (
                  <ul className="space-y-1">
                    {liveTemplate.khulumaNotificationKeys.map((key) => (
                      <li key={key} className="font-mono text-xs text-slate-600">
                        {key}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-xs text-slate-400">
                    No notification keys declared by this template.
                  </p>
                )}
              </div>
            </div>
          ) : (
            <p className="text-sm text-slate-400">
              Template registry unreachable — the governed template exists in
              `contracts/schemas/session-templates` but could not be fetched right now.
            </p>
          )}
        </section>
      ) : null}
    </div>
  );
}

function DetailRow({
  icon: Icon,
  label,
  value,
}: {
  icon?: typeof ShieldCheck;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-start gap-2">
      {Icon ? <Icon className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" /> : null}
      <div>
        <dt className="text-xs text-slate-400">{label}</dt>
        <dd className="text-slate-700">{value}</dd>
      </div>
    </div>
  );
}
