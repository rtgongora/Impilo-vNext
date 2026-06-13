"use client";

import Link from "next/link";
import { useState } from "react";
import { ShieldCheck, CheckCircle, XCircle, KeyRound, FileText } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useSecureHandover,
  useCreateSHS,
  useVerifySHS,
} from "@/hooks/queries/useVitoRecovery";

function SecureHandoverPanel() {
  const [healthId, setHealthId] = useState("");
  const [oldCardId, setOldCardId] = useState("");
  const [reason, setReason] = useState("");
  const [newPublicKey, setNewPublicKey] = useState("");

  const handover = useSecureHandover();

  function handleSubmit() {
    if (!healthId.trim() || !oldCardId.trim() || !reason.trim() || !newPublicKey.trim()) return;
    handover.mutate(
      { healthId: healthId.trim(), oldCardId: oldCardId.trim(), reason: reason.trim(), newPublicKey: newPublicKey.trim() },
      {
        onSuccess: () => {
          setHealthId("");
          setOldCardId("");
          setReason("");
          setNewPublicKey("");
        },
      },
    );
  }

  const canSubmit = healthId.trim() && oldCardId.trim() && reason.trim() && newPublicKey.trim();

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
      <div className="flex items-center gap-2">
        <KeyRound className="h-4 w-4 text-primary" />
        <h2 className="text-sm font-semibold text-foreground">Secure Handover</h2>
      </div>
      <p className="text-xs text-muted-foreground">
        Transfer digital identity to a new credential after card loss or compromise.
      </p>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Health ID
        <input
          className="rounded-xl border border-border px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. ZW-0001-0001-0001"
          value={healthId}
          onChange={(e) => setHealthId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Old Card ID
        <input
          className="rounded-xl border border-border px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="Card identifier to revoke"
          value={oldCardId}
          onChange={(e) => setOldCardId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Revocation Reason
        <input
          className="rounded-xl border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. LOST, STOLEN, DAMAGED"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        New Public Key
        <textarea
          rows={3}
          className="rounded-xl border border-border px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-none"
          placeholder="PEM or JWK public key for the new credential"
          value={newPublicKey}
          onChange={(e) => setNewPublicKey(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={handover.isPending || !canSubmit}
        onClick={handleSubmit}
        className="w-full rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {handover.isPending ? "Processing handover…" : "Submit Handover"}
      </button>

      {handover.isError && (
        <p className="text-xs text-red-600">Handover failed. Verify the card ID and Health ID are correct.</p>
      )}

      {handover.isSuccess && handover.data?.data && (
        <div className="flex items-start gap-2 rounded-xl border border-success/25 bg-success-soft p-3">
          <CheckCircle className="h-4 w-4 flex-shrink-0 text-primary mt-0.5" />
          <div className="text-xs text-primary-hover space-y-0.5">
            <p className="font-semibold">Handover complete</p>
            <p>New card ID: <span className="font-mono">{handover.data.data.newCardId}</span></p>
          </div>
        </div>
      )}
    </div>
  );
}

function CreateSHSPanel() {
  const [healthId, setHealthId] = useState("");
  const [fhirBundle, setFhirBundle] = useState("");

  const createShs = useCreateSHS();

  function handleCreate() {
    if (!healthId.trim() || !fhirBundle.trim()) return;
    createShs.mutate({ healthId: healthId.trim(), fhirBundleJson: fhirBundle.trim() });
  }

  const canCreate = healthId.trim() && fhirBundle.trim();

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
      <div className="flex items-center gap-2">
        <FileText className="h-4 w-4 text-primary" />
        <h2 className="text-sm font-semibold text-foreground">Create Secure Health State (SHS)</h2>
      </div>
      <p className="text-xs text-muted-foreground">
        Generate a signed SHS capsule from a FHIR bundle for offline or emergency use.
      </p>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Health ID
        <input
          className="rounded-xl border border-border px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. ZW-0001-0001-0001"
          value={healthId}
          onChange={(e) => setHealthId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        FHIR Bundle JSON
        <textarea
          rows={6}
          className="rounded-xl border border-border px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-y"
          placeholder='{"resourceType":"Bundle","type":"collection","entry":[]}'
          value={fhirBundle}
          onChange={(e) => setFhirBundle(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={createShs.isPending || !canCreate}
        onClick={handleCreate}
        className="w-full rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {createShs.isPending ? "Creating SHS…" : "Create SHS Capsule"}
      </button>

      {createShs.isError && (
        <p className="text-xs text-red-600">SHS creation failed. Ensure the FHIR bundle is valid JSON.</p>
      )}

      {createShs.isSuccess && createShs.data?.data && (
        <div className="rounded-xl border border-success/25 bg-success-soft p-3 space-y-2">
          <div className="flex items-center gap-2">
            <CheckCircle className="h-4 w-4 flex-shrink-0 text-primary" />
            <p className="text-xs font-semibold text-primary-hover">SHS capsule created</p>
          </div>
          <p className="text-xs text-muted-foreground">
            Expires: <span className="font-medium text-foreground">{new Date(createShs.data.data.expiresAt).toLocaleString()}</span>
          </p>
          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            JWS compact
            <textarea
              readOnly
              rows={4}
              className="rounded-xl border border-border bg-card px-3 py-2 text-xs font-mono resize-none text-foreground"
              value={createShs.data.data.jwsCompact}
            />
          </label>
        </div>
      )}
    </div>
  );
}

function VerifySHSPanel() {
  const [jws, setJws] = useState("");
  const verifyShs = useVerifySHS();

  function handleVerify() {
    if (!jws.trim()) return;
    verifyShs.mutate({ jwsCompact: jws.trim() });
  }

  const result = verifyShs.data?.data;

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
      <div className="flex items-center gap-2">
        <ShieldCheck className="h-4 w-4 text-primary" />
        <h2 className="text-sm font-semibold text-foreground">Verify SHS Capsule</h2>
      </div>
      <p className="text-xs text-muted-foreground">
        Validate a JWS compact token and inspect the decoded health state claims.
      </p>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        JWS Compact Token
        <textarea
          rows={4}
          className="rounded-xl border border-border px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-y"
          placeholder="eyJ..."
          value={jws}
          onChange={(e) => setJws(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={verifyShs.isPending || !jws.trim()}
        onClick={handleVerify}
        className="w-full rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {verifyShs.isPending ? "Verifying…" : "Verify SHS"}
      </button>

      {verifyShs.isError && (
        <p className="text-xs text-red-600">Verification request failed. Check BFF connectivity.</p>
      )}

      {result && (
        <div
          className={`rounded-xl border p-3 space-y-3 ${
            result.valid
              ? "border-success/25 bg-success-soft"
              : "border-danger/28 bg-danger-soft"
          }`}
        >
          <div className="flex items-center gap-2">
            {result.valid ? (
              <CheckCircle className="h-4 w-4 flex-shrink-0 text-primary" />
            ) : (
              <XCircle className="h-4 w-4 flex-shrink-0 text-red-600" />
            )}
            <p
              className={`text-sm font-semibold ${
                result.valid ? "text-primary-hover" : "text-red-800"
              }`}
            >
              {result.valid ? "Valid capsule" : "Invalid capsule"}
            </p>
          </div>

          {result.valid && (
            <div className="space-y-1.5">
              <p className="text-xs text-muted-foreground">
                Issued: <span className="font-medium text-foreground">{new Date(result.issuedAt).toLocaleString()}</span>
              </p>
              <p className="text-xs text-muted-foreground">
                Issuer: <span className="font-medium text-foreground font-mono">{result.issuer}</span>
              </p>

              {Object.keys(result.claims).length > 0 && (
                <div className="mt-2">
                  <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Claims</p>
                  <div className="rounded-lg border border-border bg-card overflow-auto max-h-48">
                    <table className="w-full text-xs">
                      <tbody>
                        {Object.entries(result.claims).map(([k, v]) => (
                          <tr key={k} className="border-b border-border last:border-0">
                            <td className="px-3 py-1.5 font-medium text-muted-foreground font-mono whitespace-nowrap">{k}</td>
                            <td className="px-3 py-1.5 text-foreground font-mono break-all">
                              {typeof v === "object" ? JSON.stringify(v) : String(v)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function VitoRecoveryPage() {
  return (
    <AppLayout>
      <PageShell
        title="Recovery & Secure Health State"
        subtitle="Perform secure credential handovers and manage signed SHS capsules for offline identity continuity"
        icon={<ShieldCheck className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <SecureHandoverPanel />
            <div className="space-y-6">
              <CreateSHSPanel />
              <VerifySHSPanel />
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
