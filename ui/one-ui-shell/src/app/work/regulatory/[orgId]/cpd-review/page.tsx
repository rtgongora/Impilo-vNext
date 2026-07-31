"use client";

/**
 * Provider-scoped Fundo CPD candidate review for a regulatory organisation.
 *
 * Route: /work/regulatory/[orgId]/cpd-review
 *
 * There is no council-wide CPD queue yet — enter a provider to load candidates.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FundoCpdCandidatePanel } from "@/components/registry/FundoCpdCandidatePanel";

export default function RegulatoryCpdReviewPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const [lookup, setLookup] = useState("");
  const [activeLookup, setActiveLookup] = useState("");

  const numeric = /^\d+$/.test(activeLookup) ? activeLookup : undefined;
  const publicId = numeric ? undefined : activeLookup || undefined;

  return (
    <AppLayout>
      <PageShell
        title="CPD review"
        subtitle="Fundo CPD candidates for one provider — accept or reject into the council ledger"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Regulatory workspace
          </Link>

          <p className="text-sm text-muted-foreground">
            Enter a provider to review Fundo CPD candidates — there is no council-wide queue yet.
          </p>

          <form
            className="flex flex-wrap gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              setActiveLookup(lookup.trim());
            }}
          >
            <input
              value={lookup}
              onChange={(e) => setLookup(e.target.value)}
              placeholder="Provider numeric id or public id"
              className="min-w-[240px] flex-1 rounded-lg border border-border bg-background px-3 py-2 text-sm"
            />
            <button
              type="submit"
              className="rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white hover:bg-teal-800"
            >
              Load candidates
            </button>
          </form>

          {!activeLookup ? (
            <p className="text-sm text-muted-foreground">No provider selected.</p>
          ) : (
            <FundoCpdCandidatePanel
              providerId={numeric}
              providerPublicId={publicId}
              canDecide={Boolean(numeric)}
            />
          )}

          {publicId ? (
            <p className="text-xs text-muted-foreground">
              Accept and reject require the numeric provider id so the council ledger write stays
              keyed to the registry identity. Looking up by public id is read-only.
            </p>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
