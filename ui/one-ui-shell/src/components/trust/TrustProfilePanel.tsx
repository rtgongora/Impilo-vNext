"use client";

import type { ReactNode } from "react";
import {
  AlertCircle,
  BadgeCheck,
  Briefcase,
  Clock,
  Loader2,
  MapPin,
  ShieldCheck,
  Stethoscope,
  UserCheck,
} from "lucide-react";
import {
  useTrustProfile,
  type EmploymentBlock,
  type IdentityBlock,
  type OperationalBlock,
  type ProfessionalBlock,
} from "@/hooks/queries/useTrustProfile";

/**
 * Four-block doctrine trust profile panel (IATG WS-D → E2 UI surfacing).
 *
 * Renders identity / professional / employment / operational as four cards.
 * Each card cites its own {@code sourceOfRecord} and degrades INDEPENDENTLY:
 *   - OK → the block's fields;
 *   - UNAVAILABLE → "source unreachable — nothing fabricated";
 *   - NO_PROVIDER_LINKED (professional only) → provider-creation guidance.
 *
 * The BFF is a composition layer, never a source of truth — one broken
 * downstream never poisons the other blocks, and nothing is invented for a
 * block the shell has no upstream evidence for.
 */
export function TrustProfilePanel() {
  const { data, isLoading, isError } = useTrustProfile();

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-border bg-background px-4 py-6"
        data-testid="trust-profile-panel"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Assembling your trust profile from each system of record…
        </div>
      </section>
    );
  }

  if (isError || !data) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-4"
        data-testid="trust-profile-panel"
      >
        <div className="flex items-start gap-2 text-sm text-warning-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Your trust profile could not be loaded right now. Nothing was changed — retry once the
            service is reachable.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-4" data-testid="trust-profile-panel">
      <header className="flex items-start gap-3">
        <ShieldCheck className="mt-1 h-5 w-5 shrink-0 text-primary" />
        <div>
          <h2 className="text-lg font-semibold text-foreground">Your trust profile</h2>
          <p className="text-sm text-muted-foreground">
            Four independent views of who you are across the Health OS. Each block names the system
            of record it came from and degrades on its own — nothing here is fabricated.
          </p>
        </div>
      </header>

      <div className="grid gap-3 sm:grid-cols-2">
        <IdentityCard block={data.identity} />
        <ProfessionalCard block={data.professional} />
        <EmploymentCard block={data.employment} />
        <OperationalCard block={data.operational} />
      </div>

      <p className="text-xs text-muted-foreground">
        Generated {new Date(data.generatedAt).toLocaleString()}.
      </p>
    </section>
  );
}

// ── Shared card scaffold ────────────────────────────────────────────────────

function TrustCard({
  title,
  icon: Icon,
  sourceOfRecord,
  testId,
  children,
}: {
  title: string;
  icon: typeof ShieldCheck;
  sourceOfRecord: string;
  testId: string;
  children: ReactNode;
}) {
  return (
    <div
      className="space-y-2 rounded-xl border border-border bg-card p-4 shadow-sm"
      data-testid={testId}
    >
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4 shrink-0 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      </div>
      <p className="text-[11px] uppercase tracking-wide text-muted-foreground">
        Source of record: <span className="font-medium">{sourceOfRecord}</span>
      </p>
      {children}
    </div>
  );
}

/** UNAVAILABLE — honest degradation. Never fabricates the block's data. */
function UnavailableNote() {
  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted-foreground"
      data-testid="trust-block-unavailable"
    >
      <Clock className="mt-0.5 h-4 w-4 shrink-0" />
      <p>Source unreachable — nothing fabricated. Retry once it is reachable.</p>
    </div>
  );
}

function Field({ label, value }: { label: string; value?: ReactNode }) {
  if (value === undefined || value === null || value === "") return null;
  return (
    <p className="text-sm text-muted-foreground">
      {label}: <span className="font-medium text-foreground">{value}</span>
    </p>
  );
}

// ── Identity ────────────────────────────────────────────────────────────────

function IdentityCard({ block }: { block: IdentityBlock }) {
  return (
    <TrustCard
      title="Identity"
      icon={UserCheck}
      sourceOfRecord={block.sourceOfRecord}
      testId="trust-block-identity"
    >
      {block.status === "UNAVAILABLE" ? (
        <UnavailableNote />
      ) : (
        <div className="rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-sm">
          <p className="flex items-center gap-2 font-medium text-foreground">
            <ShieldCheck className="h-4 w-4 text-primary" /> Assurance on record
          </p>
          {"assurance" in block && block.assurance ? (
            <pre className="mt-1 overflow-x-auto whitespace-pre-wrap break-words text-xs text-muted-foreground">
              {JSON.stringify(block.assurance, null, 2)}
            </pre>
          ) : (
            <p className="mt-1 text-muted-foreground">Assurance state confirmed by the source.</p>
          )}
        </div>
      )}
    </TrustCard>
  );
}

// ── Professional ────────────────────────────────────────────────────────────

function ProfessionalCard({ block }: { block: ProfessionalBlock }) {
  return (
    <TrustCard
      title="Professional"
      icon={Stethoscope}
      sourceOfRecord={block.sourceOfRecord}
      testId="trust-block-professional"
    >
      {block.status === "UNAVAILABLE" ? (
        <UnavailableNote />
      ) : block.status === "NO_PROVIDER_LINKED" ? (
        <div
          className="flex items-start gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted-foreground"
          data-testid="trust-professional-no-provider"
        >
          <Stethoscope className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            No provider profile is linked to this Health ID. To practise as a provider, claim or
            create one from the{" "}
            <a href="/citizen/provider-claim" className="font-medium text-primary underline">
              provider claim
            </a>{" "}
            journey — nothing is activated automatically.
          </p>
        </div>
      ) : (
        <div className="rounded-lg border border-primary/25 bg-primary-soft px-3 py-2 text-sm">
          <p className="flex items-center gap-2 font-medium text-foreground">
            <BadgeCheck className="h-4 w-4 text-primary" /> Provider registration
          </p>
          <p className="mt-1 text-muted-foreground">
            Provider <span className="font-mono">{block.providerId}</span>
          </p>
          <Field label="Trust level" value={block.trustLevel} />
          <Field label="Registry status" value={block.registryStatus} />
          <Field label="Onboarding channel" value={block.onboardingChannel} />
        </div>
      )}
    </TrustCard>
  );
}

// ── Employment ──────────────────────────────────────────────────────────────

function EmploymentCard({ block }: { block: EmploymentBlock }) {
  return (
    <TrustCard
      title="Employment"
      icon={Briefcase}
      sourceOfRecord={block.sourceOfRecord}
      testId="trust-block-employment"
    >
      {block.status === "UNAVAILABLE" ? (
        <UnavailableNote />
      ) : (block.records?.length ?? 0) === 0 ? (
        <p className="text-sm text-muted-foreground">
          No employment records on file with the source.
        </p>
      ) : (
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">
            {block.recordCount ?? block.records?.length} employment record
            {(block.recordCount ?? block.records?.length) === 1 ? "" : "s"}
          </p>
          {block.records?.map((record, index) => (
            <div
              key={record.maskedEcNumber ?? index}
              className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              <Field label="Status" value={record.employmentStatus} />
              <Field label="Cadre" value={record.cadre} />
              <Field label="Grade" value={record.grade} />
              <Field label="Post" value={record.postTitle} />
              <Field label="Facility" value={record.currentPostingFacility} />
              <Field label="EC number" value={record.maskedEcNumber} />
            </div>
          ))}
        </div>
      )}
    </TrustCard>
  );
}

// ── Operational ─────────────────────────────────────────────────────────────

function OperationalCard({ block }: { block: OperationalBlock }) {
  return (
    <TrustCard
      title="Operational"
      icon={MapPin}
      sourceOfRecord={block.sourceOfRecord}
      testId="trust-block-operational"
    >
      {block.status === "UNAVAILABLE" ? (
        <UnavailableNote />
      ) : (
        <div className="rounded-lg border border-border bg-background px-3 py-2 text-sm">
          <Field
            label="Active assignments"
            value={
              block.activeAssignmentCount !== undefined
                ? String(block.activeAssignmentCount)
                : undefined
            }
          />
          {block.requiresContextChooser ? (
            <p className="text-sm text-muted-foreground">
              A work context must be chosen before starting.
            </p>
          ) : null}
          {block.activeAssignmentCount === undefined && !block.requiresContextChooser ? (
            <p className="text-muted-foreground">Work context confirmed by the source.</p>
          ) : null}
        </div>
      )}
    </TrustCard>
  );
}
