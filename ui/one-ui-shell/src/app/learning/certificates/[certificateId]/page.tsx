"use client";

import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { useFundoCertificate } from "@/hooks/queries/useFundoLms";

export default function CertificateDetailPage() {
  const params = useParams<{ certificateId: string }>();
  const certificateId = params?.certificateId;
  const { data } = useFundoCertificate(certificateId);
  const cert = ((data?.data as Record<string, unknown>)?.certificate ?? {}) as Record<string, unknown>;

  return (
      <PageShell title="Certificate detail" subtitle="Tamper-evident metadata digest — not a PKI-signed credential. Council CPD credit is accepted in Varapi.">
        <div className="rounded border border-border bg-card p-4 text-sm" data-testid="fundo-certificate-detail">
          <p>Certificate number: {String(cert.certificateNumber ?? "-")}</p>
          <p>Title: {String(cert.title ?? "-")}</p>
          <p>Status: {String(cert.status ?? "-")}</p>
          <p>Issued at: {String(cert.issuedAt ?? "-")}</p>
          <p>CPD eligible: {String(cert.cpdEligible ?? false)}</p>
          <p>CPD points (evidence): {String(cert.cpdPoints ?? "-")}</p>
          <p className="mt-3 break-all font-mono text-xs text-muted-foreground" data-testid="fundo-certificate-digest">
            Verification digest: {String(cert.verificationDigest ?? "pending issuance")}
          </p>
        </div>
      </PageShell>
  );
}
