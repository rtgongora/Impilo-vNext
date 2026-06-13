"use client";

import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useFundoCertificates } from "@/hooks/queries/useFundoLms";

export default function CertificatesPage() {
  const subject = useLearningSubject();
  const { data } = useFundoCertificates(subject);
  const items = (((data?.data as Record<string, unknown>)?.items as Array<Record<string, unknown>>) ?? []).filter(Boolean);

  return (
    <AppLayout>
      <PageShell title="Certificates" subtitle="Native certificate metadata (not signed credential issuance in this phase).">
        <ul className="space-y-2" data-testid="fundo-certificate-list">
          {items.map((c) => (
            <li key={String(c.id)} className="rounded border border-border bg-card p-3 text-sm">
              <p className="font-medium text-foreground">{String(c.title ?? c.certificateNumber)}</p>
              <p className="text-xs text-muted-foreground">{String(c.certificateNumber)}</p>
              <Link href={`/learning/certificates/${String(c.id)}`} className="text-teal-700 hover:underline">
                Open
              </Link>
            </li>
          ))}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
