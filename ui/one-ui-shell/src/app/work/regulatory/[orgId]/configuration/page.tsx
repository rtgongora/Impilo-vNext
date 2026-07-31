"use client";

/**
 * A regulator's own configuration, read-only (NCZ-W1A).
 *
 * Route: /work/regulatory/[orgId]/configuration
 *
 * Read-only is the design of this wave, not a gap in it. Changing configuration is a governed act
 * with four-eyes enforced in schema; a screen that could bypass that would undo the guarantee. So
 * there are no edit affordances here at all — not disabled ones, none.
 *
 * Two things this page exists to say plainly, because they are what a council needs to act on:
 *   1. which of its rules are BUILT BUT NOT SET, and which decision each is waiting on — rendered
 *      as "awaiting Council decision NCZ-DEC-002", never as blank and never as zero;
 *   2. when a value that is not set sits on a journey an applicant can already start, which is a
 *      materially different situation from one nothing depends on yet.
 */

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, FileWarning, History, Loader2, Lock, PackageCheck, TriangleAlert } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRegulatoryOrganisations } from "@/hooks/queries/useRegulatoryOrganisations";
import { useRequireRegulatorySession } from "@/hooks/useRequireRegulatorySession";
import {
  useConfigVersionHistory,
  useRegulatoryConfiguration,
  type ConfigDefinitionSummary,
  type ConfigPackSummary,
} from "@/hooks/queries/useRegulatoryConfiguration";

/** Types whose value the regulator alone may set — mirrors carries_policy_value in org-registry. */
function policyLabel(definition: ConfigDefinitionSummary): string | null {
  const { status, decisionRef } = definition.policy;
  if (status !== "PENDING_REGULATOR_APPROVAL") return null;
  return decisionRef
    ? `Value not set — awaiting Council decision ${decisionRef}`
    : "Value not set — awaiting a Council decision";
}

function SourceBanner({ pack }: { pack: ConfigPackSummary }) {
  const { loadState, loadFailureReason, loadCheckedAt } = pack.source;
  if (loadState !== "LOAD_FAILED") return null;
  return (
    <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-4 text-sm">
      <div className="flex items-start gap-2">
        <FileWarning className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" aria-hidden />
        <div className="space-y-1">
          <p className="font-medium text-amber-200">
            This pack&rsquo;s source files could not be loaded
          </p>
          <p className="text-amber-100/80">{loadFailureReason}</p>
          <p className="text-amber-100/60">
            The configuration below is unaffected and continues to govern every case: it was
            approved separately and is what live cases are pinned to. What has stopped is the
            loading of any further configuration from those files.
            {loadCheckedAt ? ` Last checked ${loadCheckedAt}.` : null}
          </p>
        </div>
      </div>
    </div>
  );
}

function VersionHistory({ definitionId, onClose }: { definitionId: string; onClose: () => void }) {
  const { data, isLoading, isError } = useConfigVersionHistory(definitionId);
  return (
    <div className="mt-3 rounded-lg border border-white/10 bg-black/20 p-3 text-xs">
      <div className="mb-2 flex items-center justify-between">
        <span className="font-medium text-white/80">Version history</span>
        <button type="button" onClick={onClose} className="text-white/50 hover:text-white/80">
          Close
        </button>
      </div>
      {isLoading ? <p className="text-white/50">Loading&hellip;</p> : null}
      {isError ? <p className="text-white/50">Version history is unavailable right now.</p> : null}
      {data?.versions?.length ? (
        <ul className="space-y-1">
          {data.versions.map((version) => (
            <li key={version.id} className="flex flex-wrap items-baseline gap-x-3 text-white/70">
              <span className="font-mono">{version.semanticVersion}</span>
              <span className="uppercase tracking-wide text-white/45">{version.lifecycleState}</span>
              {version.authoredBy ? <span>by {version.authoredBy}</span> : null}
              {version.policyStatus === "PENDING_REGULATOR_APPROVAL" ? (
                <span className="text-amber-300/80">
                  value not set{version.policyDecisionRef ? ` (${version.policyDecisionRef})` : ""}
                </span>
              ) : null}
              <span className="font-mono text-white/30">{version.contentHash?.slice(0, 12)}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {data && data.versions.length === 0 ? (
        <p className="text-white/50">No versions recorded.</p>
      ) : null}
    </div>
  );
}

function DefinitionRow({
  definition,
  definitionId,
  organizationId,
}: {
  definition: ConfigDefinitionSummary;
  definitionId: string | undefined;
  organizationId: string;
}) {
  const [showHistory, setShowHistory] = useState(false);
  const pending = policyLabel(definition);
  const isRegister = definition.typeCode === "REGISTER";

  return (
    <li className="rounded-lg border border-white/10 bg-white/[0.02] p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="font-medium text-white/90">{definition.label}</p>
          <p className="text-xs text-white/45">
            <span className="uppercase tracking-wide">{definition.typeCode.replace(/_/g, " ")}</span>
            {" · "}
            <span className="font-mono">{definition.definitionKey}</span>
          </p>
        </div>
        <span className="font-mono text-xs text-white/60">v{definition.semanticVersion}</span>
      </div>

      {pending ? (
        <p className="mt-2 flex items-center gap-2 text-sm text-amber-300/90">
          <TriangleAlert className="h-3.5 w-3.5 shrink-0" aria-hidden />
          {pending}
        </p>
      ) : null}

      {isRegister ? (
        <p className="mt-2 text-xs text-white/50">
          Materialised into the council&apos;s professional registers —{" "}
          <Link
            href={`/work/regulatory/${encodeURIComponent(organizationId)}/registers`}
            className="text-teal-300/90 hover:underline"
          >
            view live registers
          </Link>
          .
        </p>
      ) : null}

      <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-white/45">
        {definition.selfServiceRoute ? (
          <span>
            Applicants reach this at{" "}
            <span className="font-mono text-white/60">{definition.selfServiceRoute}</span>
          </span>
        ) : null}
        {definition.pinnedAtMoment ? <span>Pinned at {definition.pinnedAtMoment}</span> : null}
        {definitionId ? (
          <button
            type="button"
            onClick={() => setShowHistory((open) => !open)}
            className="inline-flex items-center gap-1 text-white/50 hover:text-white/80"
          >
            <History className="h-3 w-3" aria-hidden />
            {showHistory ? "Hide history" : "Version history"}
          </button>
        ) : null}
      </div>

      {showHistory && definitionId ? (
        <VersionHistory definitionId={definitionId} onClose={() => setShowHistory(false)} />
      ) : null}
    </li>
  );
}

function PackCard({ pack, organizationId }: { pack: ConfigPackSummary; organizationId: string }) {
  const pending = useMemo(
    () => pack.definitions.filter((d) => d.policy.status === "PENDING_REGULATOR_APPROVAL"),
    [pack.definitions],
  );
  const draft = pack.releases.filter((r) => r.lifecycleState === "DRAFT" || r.lifecycleState === "IN_REVIEW");

  return (
    <section className="space-y-4 rounded-xl border border-white/10 bg-white/[0.02] p-5">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h2 className="text-lg font-medium text-white/90">{pack.label}</h2>
          <p className="text-xs text-white/45 font-mono">{pack.packKey}</p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full border border-white/10 px-2.5 py-1 text-xs text-white/60">
          <Lock className="h-3 w-3" aria-hidden />
          Read only
        </span>
      </header>

      <SourceBanner pack={pack} />

      {!pack.activated ? (
        <div className="rounded-lg border border-white/10 bg-white/[0.02] p-4 text-sm text-white/60">
          <p className="flex items-center gap-2 font-medium text-white/80">
            <PackageCheck className="h-4 w-4" aria-hidden />
            No configuration has been activated for this pack yet
          </p>
          <p className="mt-1 text-white/50">
            {draft.length > 0
              ? `A release is prepared and awaiting approval (${draft
                  .map((r) => `${r.releaseKey} — ${r.lifecycleState.toLowerCase().replace(/_/g, " ")}`)
                  .join(", ")}). Activation is a governed act: it needs two approvers, and deployment never performs it.`
              : "Nothing is prepared for approval yet."}
          </p>
        </div>
      ) : null}

      {pending.length > 0 ? (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-4 text-sm">
          <p className="font-medium text-amber-200">
            {pending.length} {pending.length === 1 ? "rule is" : "rules are"} built but not yet set
          </p>
          <p className="mt-1 text-amber-100/70">
            Each is waiting on a Council decision. The capability exists and will report that it is
            not configured rather than guessing a value.
          </p>
        </div>
      ) : null}

      {pack.definitions.length > 0 ? (
        <ul className="space-y-2">
          {pack.definitions.map((definition) => (
            <DefinitionRow
              key={`${definition.typeCode}:${definition.definitionKey}`}
              definition={definition}
              organizationId={organizationId}
              definitionId={
                pack.definitionIndex?.[`${definition.typeCode}:${definition.definitionKey}`]
              }
            />
          ))}
        </ul>
      ) : null}
    </section>
  );
}

export default function RegulatoryConfigurationPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const sessionOk = useRequireRegulatorySession(orgId);
  const { organisation } = useRegulatoryOrganisations();
  const org = organisation(orgId);
  const { data, isLoading, isError } = useRegulatoryConfiguration(orgId);

  if (!sessionOk) {
    return (
      <AppLayout>
        <PageShell title="Configuration" serviceSlug="varapi">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Checking regulatory session…
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Configuration"
        subtitle={
          org
            ? `${org.name} — the rules currently governing this regulator`
            : "The rules currently governing this regulator"
        }
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1.5 text-sm text-white/50 hover:text-white/80"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden />
            Back to the workspace
          </Link>

          <p className="max-w-3xl text-sm text-white/55">
            Every case this regulator opens is pinned to the exact versions shown here, so a later
            change never rewrites a decision already made. Changing configuration is a governed act
            requiring two approvers and is not performed from this screen.
          </p>

          {isLoading ? <p className="text-sm text-white/50">Loading configuration&hellip;</p> : null}
          {isError ? (
            <p className="text-sm text-white/50">
              Configuration could not be loaded right now. Nothing has changed.
            </p>
          ) : null}

          {data?.packs?.length ? (
            <div className="space-y-6">
              {data.packs.map((pack) => (
                <PackCard key={pack.packId} pack={pack} organizationId={orgId} />
              ))}
            </div>
          ) : null}

          {data && data.packs.length === 0 ? (
            <p className="text-sm text-white/50">
              No configuration pack has been created for this regulator yet.
            </p>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
