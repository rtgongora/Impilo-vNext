"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoCpdEvidence } from "@/hooks/queries/useFundoLms";

function courseLabel(item: Record<string, unknown>) {
  return String(item.courseTitle ?? item.title ?? item.courseCode ?? "Course");
}

export default function CpdEvidencePage() {
  const subject = useLearningSubject();
  const { data } = useFundoCpdEvidence(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const evidence = (payload.evidence as Array<Record<string, unknown>> | undefined) ?? [];

  return (
    <AppLayout>
      <PageShell title="CPD evidence" subtitle="Eligibility/evidence only. Regulated CPD authority remains outside Fundo.">
        <ul className="space-y-2">
          {evidence.map((e) => (
            <li key={String(e.certificateId ?? e.enrolmentId)} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <p className="font-medium text-gray-900">Course: {courseLabel(e)}</p>
              <p>Certificate: {String(e.certificateNumber ?? e.serialNumber ?? (e.certificateId ? "Available" : "-"))}</p>
              <p>CPD points: {String(e.cpdPoints ?? "-")}</p>
              {e.certificateId ? (
                <Link href={`/learning/certificates/${String(e.certificateId)}`} className="text-impilo-700 hover:underline">
                  Open certificate
                </Link>
              ) : null}
            </li>
          ))}
          {evidence.length === 0 ? <p className="text-sm text-gray-500">No CPD evidence yet.</p> : null}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
