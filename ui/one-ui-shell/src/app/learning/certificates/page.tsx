"use client";

import Link from "next/link";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import {
  summarizeCertificateValidity,
  useFundoCertificates,
  type CertificateValidityState,
} from "@/hooks/queries/useFundoLms";

const BADGE_STYLES: Record<CertificateValidityState, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-800",
  EXPIRING_SOON: "bg-amber-100 text-amber-800",
  EXPIRED: "bg-rose-100 text-rose-800",
  REVOKED: "bg-rose-100 text-rose-800",
  NO_EXPIRY: "bg-slate-100 text-slate-700",
};

export default function CertificatesPage() {
  const subject = useLearningSubject();
  const { data } = useFundoCertificates(subject);
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  return (
      <PageShell title="Certificates" subtitle="Native certificate metadata (not signed credential issuance in this phase).">
        <ul className="space-y-2" data-testid="fundo-certificate-list">
          {items.map((c) => {
            const validity = summarizeCertificateValidity(c);
            const courseId = c.courseId ? String(c.courseId) : null;
            return (
              <li key={String(c.id)} className="rounded border border-border bg-card p-3 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-medium text-foreground">{String(c.title ?? c.certificateNumber)}</p>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${BADGE_STYLES[validity.state]}`}
                    data-testid="fundo-certificate-validity"
                  >
                    {validity.label}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">{String(c.certificateNumber)}</p>
                <div className="mt-1 flex items-center gap-3">
                  <Link href={`/learning/certificates/${String(c.id)}`} className="text-teal-700 hover:underline">
                    Open
                  </Link>
                  {validity.needsRenewal && courseId && (
                    <Link
                      href={`/learning/courses/${courseId}`}
                      className="text-amber-700 hover:underline"
                      data-testid="fundo-certificate-renew"
                    >
                      Renew / refresher
                    </Link>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </PageShell>
  );
}
