"use client";

import { useState } from "react";
import { Award, Loader2, Plus } from "lucide-react";
import {
  useProviderCertificatesAdmin,
  useCreateCertificate,
  useCertificateAction,
  type RegistryCertificate,
} from "@/hooks/queries/useProviderCertificatesAdmin";

const STATUS_STYLE: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  ISSUED: "bg-green-100 text-green-700",
  SUSPENDED: "bg-amber-100 text-amber-700",
  REVOKED: "bg-red-100 text-red-700",
  EXPIRED: "bg-neutral-100 text-neutral-600",
};

const CERTIFICATE_TYPES = [
  "PRACTISING_CERTIFICATE",
  "REGISTRATION_CERTIFICATE",
  "LETTER_OF_GOOD_STANDING",
];

/**
 * Registrar certificate lifecycle for a provider — issue and suspend/reinstate/revoke (reason
 * required). Every mutation is audited server-side (ProviderRegistryAuditHelper). Council-registrar
 * persona; keyed on the numeric registry id resolved via the W0 bridge.
 */
export function ProviderCertificatesAdminPanel({
  providerPublicId,
  providerId,
}: {
  providerPublicId: string;
  providerId?: number | null;
}) {
  const { data: certificates = [], isPending } = useProviderCertificatesAdmin(providerPublicId);
  const create = useCreateCertificate(providerPublicId);
  const action = useCertificateAction(providerPublicId);

  const [showIssue, setShowIssue] = useState(false);
  const [form, setForm] = useState({
    certificateType: CERTIFICATE_TYPES[0],
    certificateNumber: "",
    issueDate: "",
    expiryDate: "",
    issuingAuthority: "",
  });
  const [reasonFor, setReasonFor] = useState<{ id: number; action: "suspend" | "revoke" } | null>(null);
  const [reason, setReason] = useState("");

  function submitIssue(e: React.FormEvent) {
    e.preventDefault();
    if (!providerId) return;
    create.mutate(
      {
        providerId,
        certificateType: form.certificateType,
        certificateNumber: form.certificateNumber || undefined,
        issueDate: form.issueDate || undefined,
        expiryDate: form.expiryDate || undefined,
        issuingAuthority: form.issuingAuthority || undefined,
      },
      {
        onSuccess: () => {
          setShowIssue(false);
          setForm({ certificateType: CERTIFICATE_TYPES[0], certificateNumber: "", issueDate: "", expiryDate: "", issuingAuthority: "" });
        },
      },
    );
  }

  function runAction(cert: RegistryCertificate, act: string) {
    if (act === "suspend" || act === "revoke") {
      setReasonFor({ id: cert.id, action: act });
      setReason("");
      return;
    }
    action.mutate({ certificateId: cert.id, action: act });
  }

  function confirmReason() {
    if (!reasonFor || !reason.trim()) return;
    action.mutate(
      { certificateId: reasonFor.id, action: reasonFor.action, reason: reason.trim() },
      { onSuccess: () => setReasonFor(null) },
    );
  }

  return (
    <div className="bg-card rounded-xl border border-border shadow-sm">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Award className="h-4 w-4 text-primary" /> Certificates
        </h3>
        <button
          type="button"
          onClick={() => setShowIssue((s) => !s)}
          disabled={!providerId}
          className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
        >
          <Plus className="h-3.5 w-3.5" /> Issue
        </button>
      </div>

      {showIssue && (
        <form onSubmit={submitIssue} className="space-y-2 border-b border-border bg-background p-3">
          <div className="grid gap-2 sm:grid-cols-2">
            <select
              value={form.certificateType}
              onChange={(e) => setForm({ ...form, certificateType: e.target.value })}
              className="rounded-lg border border-border px-2 py-1.5 text-sm bg-card"
            >
              {CERTIFICATE_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
            </select>
            <input
              value={form.certificateNumber}
              onChange={(e) => setForm({ ...form, certificateNumber: e.target.value })}
              placeholder="Certificate number"
              className="rounded-lg border border-border px-2 py-1.5 text-sm"
            />
            <label className="text-xs text-muted-foreground">Issue date
              <input type="date" value={form.issueDate} onChange={(e) => setForm({ ...form, issueDate: e.target.value })}
                className="mt-0.5 w-full rounded-lg border border-border px-2 py-1.5 text-sm" />
            </label>
            <label className="text-xs text-muted-foreground">Expiry date
              <input type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })}
                className="mt-0.5 w-full rounded-lg border border-border px-2 py-1.5 text-sm" />
            </label>
          </div>
          <input
            value={form.issuingAuthority}
            onChange={(e) => setForm({ ...form, issuingAuthority: e.target.value })}
            placeholder="Issuing authority (council)"
            className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
          />
          <button type="submit" disabled={create.isPending}
            className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">
            {create.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null} Issue certificate
          </button>
        </form>
      )}

      <div className="p-3">
        {isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : certificates.length === 0 ? (
          <p className="text-sm text-muted-foreground">No certificates issued for this provider.</p>
        ) : (
          <ul className="divide-y divide-border">
            {certificates.map((cert) => {
              const status = String(cert.status ?? "").toUpperCase();
              return (
                <li key={cert.id} className="py-2.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-foreground">
                        {(cert.certificateType ?? "Certificate").replace(/_/g, " ")}
                        {cert.certificateNumber ? ` · ${cert.certificateNumber}` : ""}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {cert.expiryDate ? `expires ${new Date(cert.expiryDate).toLocaleDateString()}` : ""}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>{status || "—"}</span>
                      {(status === "ACTIVE" || status === "ISSUED") && (
                        <>
                          <button type="button" onClick={() => runAction(cert, "suspend")} className="rounded border border-border px-2 py-1 text-xs hover:bg-background">Suspend</button>
                          <button type="button" onClick={() => runAction(cert, "revoke")} className="rounded border border-red-300 px-2 py-1 text-xs text-red-700 hover:bg-red-50">Revoke</button>
                        </>
                      )}
                      {status === "SUSPENDED" && (
                        <button type="button" onClick={() => runAction(cert, "reinstate")} className="rounded border border-border px-2 py-1 text-xs hover:bg-background">Reinstate</button>
                      )}
                    </div>
                  </div>
                  {reasonFor?.id === cert.id && (
                    <div className="mt-2 rounded-lg border border-border bg-background p-2">
                      <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2}
                        placeholder={`Reason to ${reasonFor.action} (required)`}
                        className="w-full rounded border border-border px-2 py-1.5 text-sm" />
                      <div className="mt-1 flex justify-end gap-2">
                        <button type="button" onClick={() => setReasonFor(null)} className="rounded px-2 py-1 text-xs text-muted-foreground">Cancel</button>
                        <button type="button" onClick={confirmReason} disabled={!reason.trim() || action.isPending}
                          className="rounded bg-red-600 px-2 py-1 text-xs font-medium text-white disabled:opacity-50">Confirm {reasonFor.action}</button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
