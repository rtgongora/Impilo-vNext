"use client";

import Link from "next/link";
import {
  AlertTriangle,
  Baby,
  BedDouble,
  ClipboardList,
  HeartPulse,
  Loader2,
  Pill,
  ShieldAlert,
  Stethoscope,
  Syringe,
  Target,
  Users,
} from "lucide-react";
import { useClerkingContinuityContext } from "./useClerkingContinuityContext";
import { ClerkingExtensionsPanel } from "./ClerkingExtensionsPanel";
import { ConditionalClerkingPanel } from "./ConditionalClerkingPanel";

function Panel({
  title,
  icon: Icon,
  href,
  linkLabel,
  children,
  unavailable,
}: {
  title: string;
  icon: typeof Stethoscope;
  href?: string;
  linkLabel?: string;
  children: React.ReactNode;
  unavailable?: boolean;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4" data-testid={`clerking-panel-${title}`}>
      <div className="mb-3 flex items-center gap-2">
        <Icon className="h-4 w-4 text-muted-foreground" />
        <h3 className="text-sm font-medium text-foreground">{title}</h3>
      </div>
      {unavailable ? (
        <p className="text-sm text-warning" data-testid="clerking-unavailable">
          {title} unavailable — this is not a record that there is none.
        </p>
      ) : (
        children
      )}
      {href && linkLabel && (
        <Link href={href} className="mt-2 inline-block text-xs text-primary hover:text-primary-hover">
          {linkLabel}
        </Link>
      )}
    </div>
  );
}

function Empty({ label }: { label: string }) {
  return <p className="text-sm text-muted-foreground">{label}</p>;
}

export function ClerkingContinuityShell({ patientId }: { patientId: string }) {
  const ctx = useClerkingContinuityContext(patientId);

  if (ctx.isLoading) {
    return (
      <div className="flex items-center justify-center py-10">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">Loading clerking continuity…</span>
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="clerking-continuity-shell">
      <div className="rounded-lg border border-border bg-muted/30 p-3">
        <h2 className="text-sm font-semibold text-foreground">Clerking continuity</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          Structured history composed from existing systems of record. Failed reads are named —
          never shown as empty history.
        </p>
        {ctx.unavailable.length > 0 && (
          <p className="mt-2 text-xs text-warning" data-testid="clerking-unavailable-banner">
            Could not load: {ctx.unavailable.join(", ")}.
          </p>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Panel
          title={`Active problems as PMH (${ctx.activeProblems.length})`}
          icon={Stethoscope}
          href={`/ehr/${patientId}/conditions`}
          linkLabel="Manage conditions"
          unavailable={ctx.unavailable.includes("problem list")}
        >
          {ctx.activeProblems.length === 0 ? (
            <Empty label="No active problems on the list" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.activeProblems.slice(0, 8).map((c) => (
                <li key={c.id} className="rounded bg-background p-2 text-sm">
                  {c.attributes.conditionName}
                  {c.attributes.icdCode ? (
                    <span className="ml-2 text-xs text-muted-foreground">({c.attributes.icdCode})</span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Medical episodes (${ctx.episodes.length})`}
          icon={ClipboardList}
          unavailable={ctx.unavailable.includes("medical episodes")}
        >
          {ctx.episodes.length === 0 ? (
            <Empty label="No medical episodes recorded" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.episodes.slice(0, 8).map((e) => (
                <li key={e.episode_id} className="rounded bg-background p-2 text-sm">
                  <span className="font-medium">{e.title || e.episode_type}</span>
                  <span className="ml-2 text-xs text-muted-foreground">{e.status}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Medications (${ctx.medications.length})`}
          icon={Pill}
          href={`/ehr/${patientId}/medications`}
          linkLabel="Manage medications"
          unavailable={ctx.unavailable.includes("medications")}
        >
          {ctx.medications.length === 0 ? (
            <Empty label="No active medications" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.medications.slice(0, 6).map((m) => {
                const row = m as { id?: string; attributes?: Record<string, unknown> };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.attributes?.medication_name ?? row.attributes?.medicationName ?? "Medication")}
                  </li>
                );
              })}
            </ul>
          )}
          {!ctx.unavailable.includes("medication reconciliation") && ctx.adherenceFindings.length > 0 && (
            <div className="mt-3 space-y-1 border-t border-border pt-2">
              <p className="text-xs font-medium text-warning-foreground">Reconciliation / adherence</p>
              {ctx.adherenceFindings.map((f, i) => (
                <p key={i} className="text-xs text-muted-foreground">
                  {f.display}: {f.finding}
                  {f.patientSays ? ` — patient says: ${f.patientSays}` : ""}
                </p>
              ))}
            </div>
          )}
          {ctx.unavailable.includes("medication reconciliation") && (
            <p className="mt-2 text-xs text-warning">Adherence findings unavailable.</p>
          )}
        </Panel>

        <Panel
          title={`Allergies (${ctx.allergies.length})`}
          icon={ShieldAlert}
          href={`/ehr/${patientId}/allergies`}
          linkLabel="Manage allergies"
          unavailable={ctx.unavailable.includes("allergies")}
        >
          {ctx.allergies.length === 0 ? (
            <Empty label="No allergy rows returned (confirm NKDA on allergies page)" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.allergies.slice(0, 6).map((a) => {
                const row = a as { id?: string; attributes?: Record<string, unknown> };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.attributes?.allergen ?? row.attributes?.substance ?? "Allergen")}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Immunizations (${ctx.immunizations.length})`}
          icon={Syringe}
          href={`/ehr/${patientId}/immunizations`}
          linkLabel="View immunizations"
          unavailable={ctx.unavailable.includes("immunizations")}
        >
          {ctx.immunizations.length === 0 ? (
            <Empty label="No immunizations recorded" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.immunizations.slice(0, 6).map((imm) => {
                const row = imm as { id?: string; attributes?: Record<string, unknown> };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.attributes?.vaccine_name ?? row.attributes?.vaccineName ?? "Vaccine")}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Surgical / past procedures (${ctx.procedures.length})`}
          icon={HeartPulse}
          href={`/ehr/${patientId}/procedures`}
          linkLabel="Manage procedures"
          unavailable={ctx.unavailable.includes("surgical history")}
        >
          {ctx.procedures.length === 0 ? (
            <Empty label="No past procedures recorded" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.procedures.slice(0, 6).map((p) => {
                const row = p as { id?: string; name?: string };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.name ?? "Procedure")}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Functional status (${ctx.functional.length})`}
          icon={ClipboardList}
          href={`/ehr/${patientId}/functional-status`}
          linkLabel="Functional assessments"
          unavailable={ctx.unavailable.includes("functional status")}
        >
          {ctx.functional.length === 0 ? (
            <Empty label="No functional assessments recorded" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.functional.slice(0, 4).map((f) => {
                const row = f as { id?: string; type?: string; interpretation?: string };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.type)} — {String(row.interpretation || "recorded")}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title={`Family history (${ctx.family.length})`}
          icon={Users}
          href={`/ehr/${patientId}/family-history`}
          linkLabel="Family history"
          unavailable={ctx.unavailable.includes("family history")}
        >
          {ctx.family.length === 0 ? (
            <Empty label="No family history members recorded" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.family.slice(0, 4).map((m) => {
                const row = m as { id?: string; relationship?: string; name?: string };
                return (
                  <li key={String(row.id)} className="rounded bg-background p-2 text-sm">
                    {String(row.relationship)} {row.name ? `(${row.name})` : ""}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel
          title="Goals & preferences"
          icon={Target}
          href={`/ehr/${patientId}/goals`}
          linkLabel="Goals"
          unavailable={
            ctx.unavailable.includes("care plans") &&
            ctx.unavailable.includes("goals") &&
            ctx.unavailable.includes("advance directives")
          }
        >
          <ul className="space-y-1 text-sm text-muted-foreground">
            <li>
              Care plans:{" "}
              {ctx.unavailable.includes("care plans")
                ? "unavailable"
                : `${Array.isArray(ctx.carePlans) ? ctx.carePlans.length : 0}`}
            </li>
            <li>
              Goals:{" "}
              {ctx.unavailable.includes("goals")
                ? "unavailable"
                : `${Array.isArray(ctx.goals) ? ctx.goals.length : 0}`}
            </li>
            <li>
              Advance directives:{" "}
              {ctx.unavailable.includes("advance directives")
                ? "unavailable"
                : `${ctx.directives.length}`}
            </li>
          </ul>
          <div className="mt-2 flex flex-wrap gap-3 text-xs">
            <Link href={`/ehr/${patientId}/care-plans`} className="text-primary">
              Care plans
            </Link>
            <Link href={`/ehr/${patientId}/advance-directives`} className="text-primary">
              Advance directives
            </Link>
            <Link href={`/ehr/${patientId}/social-history`} className="text-primary">
              Social history
            </Link>
          </div>
        </Panel>

        <Panel
          title="Pregnancy / maternity context"
          icon={Baby}
          href={`/ehr/${patientId}/maternity`}
          linkLabel="Maternity"
          unavailable={ctx.unavailable.includes("pregnancy / maternity context")}
        >
          {!ctx.maternity || Object.keys(ctx.maternity).length === 0 ? (
            <Empty label="No maternity summary returned (not an assertion of non-pregnancy)" />
          ) : (
            <p className="text-sm text-foreground">
              Maternity summary available — open maternity for clinical detail. Medical view does
              not invent pregnancy status from programme links alone.
            </p>
          )}
        </Panel>

        <Panel
          title={`Previous admissions (${ctx.admissions.length})`}
          icon={BedDouble}
          unavailable={ctx.unavailable.includes("admissions")}
        >
          {ctx.admissions.length === 0 ? (
            <Empty label="No admissions returned" />
          ) : (
            <ul className="space-y-1.5">
              {ctx.admissions.slice(0, 6).map((a, i) => {
                const row = a as Record<string, unknown>;
                return (
                  <li key={String(row.id ?? row.admission_id ?? i)} className="rounded bg-background p-2 text-sm">
                    {String(row.status ?? row.admissionType ?? row.admission_type ?? "Admission")}
                    {row.admittedAt || row.admitted_at
                      ? ` · ${String(row.admittedAt ?? row.admitted_at)}`
                      : ""}
                  </li>
                );
              })}
            </ul>
          )}
        </Panel>

        <Panel title="Adverse reactions (ADR / AEFI)" icon={AlertTriangle} href="/work/patient-safety" linkLabel="Patient safety work queue">
          <p className="text-sm text-muted-foreground">
            Pharmacovigilance remains on the patient-safety SoR. Do not fork ADR into clerking.
          </p>
        </Panel>
      </div>

      <ConditionalClerkingPanel patientId={patientId} />
      <ClerkingExtensionsPanel patientId={patientId} />
    </div>
  );
}
