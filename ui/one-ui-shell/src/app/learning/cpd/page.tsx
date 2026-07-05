"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoCpdEvidence } from "@/hooks/queries/useFundoLms";
import { useFundoCpdCandidatesByPublicId } from "@/hooks/queries/useProviderCouncil";
import { useAuthStore } from "@/hooks/useAuthStore";

interface CandidateStateRow {
  courseId?: string;
  externalRef?: string;
  verificationState?: string;
  linkedCpdEventId?: number | string | null;
  [key: string]: unknown;
}

function candidateStateBadge(state: string) {
  const s = state.toUpperCase();
  if (s === "ACCEPTED") return "bg-emerald-100 text-emerald-800 border-emerald-200";
  if (s === "REJECTED") return "bg-red-100 text-red-800 border-red-200";
  return "bg-amber-100 text-amber-800 border-amber-200";
}

export default function CpdEvidencePage() {
  const subject = useLearningSubject();
  const user = useAuthStore((state) => state.user);
  const { data } = useFundoCpdEvidence(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const evidence = (
    (payload.items as Array<Record<string, unknown>> | undefined) ??
    (payload.evidence as Array<Record<string, unknown>> | undefined) ??
    []
  );
  const providerId = user?.providerId ?? "";
  // The learning subject id IS the provider public id (see useLearningSubject),
  // which is the key Varapi stores candidates under.
  const providerPublicId = subject?.subjectType === "PROVIDER_PUBLIC_ID" ? subject.subjectId : undefined;
  const { data: candidates } = useFundoCpdCandidatesByPublicId(providerPublicId);
  const candidateByCourse = new Map<string, CandidateStateRow>();
  for (const c of (Array.isArray(candidates) ? candidates : []) as CandidateStateRow[]) {
    if (c.courseId) candidateByCourse.set(String(c.courseId), c);
  }

  return (
      <PageShell
        title="CPD evidence"
        subtitle="Fundo provides evidence and completion artifacts; council acceptance and ledger authority remain in Varapi."
      >
        <div className="mb-4 rounded-lg border border-info/25 bg-info-soft p-3 text-sm text-primary-hover">
          <p className="font-semibold">Council handoff</p>
          <p className="mt-1">
            Submit evidence packages to your regulator from Provider Council Self-Service and track acceptance/rejection there.
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Link
              href={providerId ? `/registry/provider-council/self-service?providerId=${encodeURIComponent(providerId)}` : "/registry/provider-council/self-service"}
              className="rounded border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-indigo-100"
            >
              Open council self-service
            </Link>
            <Link href="/home/credentials" className="rounded border border-indigo-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-indigo-100">
              Open credential status
            </Link>
          </div>
        </div>

        <ul className="space-y-2" data-testid="fundo-cpd-evidence-list">
          {evidence.map((e, index) => {
            const candidate = e.courseId ? candidateByCourse.get(String(e.courseId)) : undefined;
            const councilState = candidate?.verificationState
              ? String(candidate.verificationState)
              : null;
            return (
              <li
                key={String(e.certificateId ?? e.completionId ?? e.enrolmentId ?? index)}
                className="rounded border border-border bg-card p-3 text-sm"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium text-foreground">
                    Course: {String(e.courseTitle ?? e.courseId ?? e.resourceId ?? "Course")}
                  </p>
                  {councilState ? (
                    <span
                      className={`rounded-full border px-2 py-0.5 text-[11px] font-medium ${candidateStateBadge(councilState)}`}
                      data-testid={`cpd-council-state-${index}`}
                    >
                      Council: {councilState}
                      {candidate?.linkedCpdEventId != null
                        ? ` (ledger #${String(candidate.linkedCpdEventId)})`
                        : ""}
                    </span>
                  ) : (
                    <span className="rounded-full border border-border bg-neutral-50 px-2 py-0.5 text-[11px] text-muted-foreground">
                      Not yet with council
                    </span>
                  )}
                </div>
                <p>Certificate: {String(e.certificateId ?? "-")}</p>
                <p>Completed: {String(e.completedAt ?? e.issuedAt ?? "-")}</p>
              </li>
            );
          })}
          {evidence.length === 0 ? <p className="text-sm text-muted-foreground">No CPD evidence yet.</p> : null}
        </ul>
      </PageShell>
  );
}
