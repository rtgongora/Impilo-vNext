"use client";

import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCertificate } from "@/hooks/queries/useFundoLms";

export default function CertificateDetailPage() {
  const params = useParams<{ certificateId: string }>();
  const certificateId = params?.certificateId;
  const { data } = useFundoCertificate(certificateId);
  const cert = ((data?.data as Record<string, unknown>)?.certificate ?? {}) as Record<string, unknown>;

  return (
    <AppLayout>
      <PageShell title="Certificate detail" subtitle="Certificate metadata + CPD evidence/eligibility view.">
        <div className="rounded border border-gray-200 bg-white p-4 text-sm">
          <p>Certificate number: {String(cert.certificateNumber ?? "-")}</p>
          <p>Title: {String(cert.title ?? "-")}</p>
          <p>Status: {String(cert.status ?? "-")}</p>
          <p>Issued at: {String(cert.issuedAt ?? "-")}</p>
          <p>CPD eligible: {String(cert.cpdEligible ?? false)}</p>
          <p>CPD points (evidence): {String(cert.cpdPoints ?? "-")}</p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
