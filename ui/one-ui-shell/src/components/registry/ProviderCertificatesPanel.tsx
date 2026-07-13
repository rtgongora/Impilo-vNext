"use client";

import { useState } from "react";
import { Award, Download, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import {
  useMyCertificates,
  isCertificateDownloadable,
  type MyCertificate,
} from "@/hooks/queries/useMyCertificates";

const STATUS_STYLE: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  SUSPENDED: "bg-amber-100 text-amber-700",
  REVOKED: "bg-red-100 text-red-700",
  EXPIRED: "bg-neutral-100 text-neutral-600",
};

/** Self-service "My certificates" — list the provider's certificates and download the PDF. */
export function ProviderCertificatesPanel() {
  const { data: certificates = [], isPending, isError } = useMyCertificates();
  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function download(cert: MyCertificate) {
    setDownloadingId(cert.id);
    setError(null);
    try {
      const blob = await apiClient.getBlob(`/internal/v1/identity/certificates/${cert.id}/download`);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `certificate-${cert.licenseNumber ?? cert.id}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      setError("Could not download this certificate. It may not be available for its current status.");
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden" data-testid="my-certificates">
      <div className="px-6 py-4 border-b border-border bg-background flex items-center gap-2">
        <Award className="h-5 w-5 text-primary" />
        <h2 className="text-sm font-semibold text-foreground">My certificates</h2>
      </div>
      <div className="p-4">
        {isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading certificates…
          </div>
        ) : isError ? (
          <p className="text-sm text-muted-foreground">Certificates are unavailable right now.</p>
        ) : certificates.length === 0 ? (
          <p className="text-sm text-muted-foreground">No certificates on record yet.</p>
        ) : (
          <ul className="divide-y divide-border">
            {certificates.map((cert) => {
              const status = String(cert.status ?? "").toUpperCase();
              const downloadable = isCertificateDownloadable(cert);
              return (
                <li key={cert.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-foreground">
                      {cert.licenseType ?? "Certificate"}{cert.licenseNumber ? ` · ${cert.licenseNumber}` : ""}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {cert.councilName ? `${cert.councilName} · ` : ""}
                      {cert.validTo ? `valid to ${new Date(cert.validTo).toLocaleDateString()}` : ""}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>
                      {status || "—"}
                    </span>
                    {downloadable ? (
                      <button
                        type="button"
                        onClick={() => download(cert)}
                        disabled={downloadingId === cert.id}
                        className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
                      >
                        {downloadingId === cert.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Download className="h-3.5 w-3.5" />}
                        PDF
                      </button>
                    ) : (
                      <span className="text-xs text-muted-foreground" title="Not available for this status">Unavailable</span>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
      </div>
    </section>
  );
}
