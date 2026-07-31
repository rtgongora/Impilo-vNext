"use client";

/**
 * The mental-health clinical record behind an accepted referral.
 * Route: /work/mental-health/[referralId]
 *
 * W13 shipped the referral queue: accept or decline, and nothing after. mental-health-service has
 * served the record behind that decision since — assessment, risk formulation, safety plan,
 * involuntary episode, restraint and its review, admission requests, follow-up — with no caller
 * anywhere. This page is that caller.
 *
 * Each panel states its own three states separately, because on this record "nothing recorded" and
 * "could not be read" are opposite clinical facts and the blank screen that represents both is the
 * failure mode worth designing against.
 */

import Link from "next/link";
import { useParams } from "next/navigation";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { AdmissionRequestPanel } from "@/features/mental-health/AdmissionRequestPanel";
import { AssessmentPanel } from "@/features/mental-health/AssessmentPanel";
import { FollowupPanel } from "@/features/mental-health/FollowupPanel";
import { InvoluntaryEpisodePanel } from "@/features/mental-health/InvoluntaryEpisodePanel";
import { RestraintPanel } from "@/features/mental-health/RestraintPanel";
import { SafetyPlanPanel } from "@/features/mental-health/SafetyPlanPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMentalHealthReferral } from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

export default function MentalHealthRecordPage() {
  const params = useParams<{ referralId: string }>();
  const referralId = String(params?.referralId ?? "");
  const user = useAuthStore((state) => state.user);
  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";

  const referral = useMentalHealthReferral(referralId);
  const row = referral.data;

  return (
    <AppLayout>
      <PageShell
        title="Mental health record"
        subtitle="mental-health-service — the clinical record behind an accepted psychiatric emergency referral"
      >
        <div className="mb-4 flex flex-wrap gap-4 text-sm">
          <Link href="/work/mental-health" className="text-primary underline">
            ← Referral queue
          </Link>
          <Link href="/work/mental-health/restraint-review" className="text-primary underline">
            Restraint review backlog
          </Link>
        </div>

        {referral.isError ? (
          <p
            className="mb-4 rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger"
            data-testid="mh-referral-unreadable"
          >
            {bffErrorMessage(referral.error, "this referral")}
          </p>
        ) : row ? (
          <div className="mb-6 rounded-xl border border-border bg-card p-4 text-sm" data-testid="mh-referral-header">
            <p className="font-medium text-foreground">
              {row.subject_cpid ? String(row.subject_cpid) : "Identity unresolved"} · {String(row.status ?? "—")}
            </p>
            <p className="mt-1 text-muted-foreground">{String(row.request_reason ?? "No reason given")}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Referred by {String(row.requested_by ?? "—")}
              {row.reviewed_by ? ` · reviewed by ${String(row.reviewed_by)}` : ""}
            </p>
          </div>
        ) : null}

        {!actor ? (
          <p
            className="mb-4 rounded border border-warning/40 bg-warning-soft px-3 py-2 text-sm"
            data-testid="mh-no-actor"
          >
            Your provider identity is not resolved in this session. Records written now would carry
            no attributable author, so the service will refuse them — sign in with a provider
            identity before recording anything here.
          </p>
        ) : null}

        {referralId ? (
          <div className="space-y-4">
            <AssessmentPanel referralId={referralId} actor={actor} />
            <SafetyPlanPanel referralId={referralId} actor={actor} />
            <InvoluntaryEpisodePanel referralId={referralId} actor={actor} />
            <RestraintPanel referralId={referralId} actor={actor} />
            <AdmissionRequestPanel referralId={referralId} actor={actor} />
            <FollowupPanel referralId={referralId} actor={actor} />
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
