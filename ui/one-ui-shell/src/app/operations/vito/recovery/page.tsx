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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <KeyRound className="h-4 w-4 text-impilo-600" />
        <h2 className="text-sm font-semibold text-gray-900">Secure Handover</h2>
      </div>
      <p className="text-xs text-gray-500">
        Transfer digital identity to a new credential after card loss or compromise.
      </p>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        Health ID
        <input
          className="rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. ZW-0001-0001-0001"
          value={healthId}
          onChange={(e) => setHealthId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        Old Card ID
        <input
          className="rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="Card identifier to revoke"
          value={oldCardId}
          onChange={(e) => setOldCardId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        Revocation Reason
        <input
          className="rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. LOST, STOLEN, DAMAGED"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        New Public Key
        <textarea
          rows={3}
          className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-none"
          placeholder="PEM or JWK public key for the new credential"
          value={newPublicKey}
          onChange={(e) => setNewPublicKey(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={handover.isPending || !canSubmit}
        onClick={handleSubmit}
        className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {handover.isPending ? "Processing handover…" : "Submit Handover"}
      </button>

      {handover.isError && (
        <p className="text-xs text-red-600">Handover failed. Verify the card ID and Health ID are correct.</p>
      )}

      {handover.isSuccess && handover.data?.data && (
        <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3">
          <CheckCircle className="h-4 w-4 flex-shrink-0 text-emerald-600 mt-0.5" />
          <div className="text-xs text-emerald-800 space-y-0.5">
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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <FileText className="h-4 w-4 text-impilo-600" />
        <h2 className="text-sm font-semibold text-gray-900">Create Secure Health State (SHS)</h2>
      </div>
      <p className="text-xs text-gray-500">
        Generate a signed SHS capsule from a FHIR bundle for offline or emergency use.
      </p>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        Health ID
        <input
          className="rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
          placeholder="e.g. ZW-0001-0001-0001"
          value={healthId}
          onChange={(e) => setHealthId(e.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        FHIR Bundle JSON
        <textarea
          rows={6}
          className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-y"
          placeholder='{"resourceType":"Bundle","type":"collection","entry":[]}'
          value={fhirBundle}
          onChange={(e) => setFhirBundle(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={createShs.isPending || !canCreate}
        onClick={handleCreate}
        className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {createShs.isPending ? "Creating SHS…" : "Create SHS Capsule"}
      </button>

      {createShs.isError && (
        <p className="text-xs text-red-600">SHS creation failed. Ensure the FHIR bundle is valid JSON.</p>
      )}

      {createShs.isSuccess && createShs.data?.data && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 space-y-2">
          <div className="flex items-center gap-2">
            <CheckCircle className="h-4 w-4 flex-shrink-0 text-emerald-600" />
            <p className="text-xs font-semibold text-emerald-800">SHS capsule created</p>
          </div>
          <p className="text-xs text-gray-500">
            Expires: <span className="font-medium text-gray-800">{new Date(createShs.data.data.expiresAt).toLocaleString()}</span>
          </p>
          <label className="flex flex-col gap-1 text-xs text-gray-500">
            JWS compact
            <textarea
              readOnly
              rows={4}
              className="rounded-xl border border-gray-200 bg-white px-3 py-2 text-xs font-mono resize-none text-gray-800"
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
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <ShieldCheck className="h-4 w-4 text-impilo-600" />
        <h2 className="text-sm font-semibold text-gray-900">Verify SHS Capsule</h2>
      </div>
      <p className="text-xs text-gray-500">
        Validate a JWS compact token and inspect the decoded health state claims.
      </p>

      <label className="flex flex-col gap-1 text-xs text-gray-500">
        JWS Compact Token
        <textarea
          rows={4}
          className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-y"
          placeholder="eyJ..."
          value={jws}
          onChange={(e) => setJws(e.target.value)}
        />
      </label>

      <button
        type="button"
        disabled={verifyShs.isPending || !jws.trim()}
        onClick={handleVerify}
        className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
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
              ? "border-emerald-200 bg-emerald-50"
              : "border-red-200 bg-red-50"
          }`}
        >
          <div className="flex items-center gap-2">
            {result.valid ? (
              <CheckCircle className="h-4 w-4 flex-shrink-0 text-emerald-600" />
            ) : (
              <XCircle className="h-4 w-4 flex-shrink-0 text-red-600" />
            )}
            <p
              className={`text-sm font-semibold ${
                result.valid ? "text-emerald-800" : "text-red-800"
              }`}
            >
              {result.valid ? "Valid capsule" : "Invalid capsule"}
            </p>
          </div>

          {result.valid && (
            <div className="space-y-1.5">
              <p className="text-xs text-gray-500">
                Issued: <span className="font-medium text-gray-800">{new Date(result.issuedAt).toLocaleString()}</span>
              </p>
              <p className="text-xs text-gray-500">
                Issuer: <span className="font-medium text-gray-800 font-mono">{result.issuer}</span>
              </p>

              {Object.keys(result.claims).length > 0 && (
                <div className="mt-2">
                  <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">Claims</p>
                  <div className="rounded-lg border border-gray-200 bg-white overflow-auto max-h-48">
                    <table className="w-full text-xs">
                      <tbody>
                        {Object.entries(result.claims).map(([k, v]) => (
                          <tr key={k} className="border-b border-gray-100 last:border-0">
                            <td className="px-3 py-1.5 font-medium text-gray-600 font-mono whitespace-nowrap">{k}</td>
                            <td className="px-3 py-1.5 text-gray-800 font-mono break-all">
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
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
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
