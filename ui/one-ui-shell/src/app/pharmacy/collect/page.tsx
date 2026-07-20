"use client";

import { useState } from "react";
import { PackageCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import type { Modality } from "@/hooks/queries/useAbisBiometric";

/**
 * Pharmacy collection / pickup-claim. A collector presents a pickup token to
 * receive a dispensed medication; optionally their biometric is verified 1:1 at
 * hand-over (A4). MATCH → collected-with-biometric; NO_MATCH → collection rejected;
 * unavailable → falls back to the token check. Honest failure — never a fabricated
 * hand-over.
 */
export default function PharmacyCollectWorkspace() {
  const [token, setToken] = useState("");
  const [subjectRef, setSubjectRef] = useState("");
  const [probe, setProbe] = useState<{ modality: Modality; probeBase64: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  async function claim() {
    if (!token.trim()) return;
    setBusy(true);
    setResult(null);
    try {
      await apiClient.post("/internal/v1/pharmacy/pickup/claim", {
        token: token.trim(),
        // Optional collector biometric verify at hand-over.
        biometricSubjectRef: probe ? subjectRef.trim() || undefined : undefined,
        biometricModality: probe?.modality,
        biometricProbeBase64: probe?.probeBase64,
      });
      setResult({ ok: true, message: "Collection confirmed — medication handed over." });
    } catch {
      setResult({
        ok: false,
        message: "Collection was rejected — invalid/expired token or biometric mismatch. Nothing handed over.",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Collect a prescription"
        subtitle="Claim a dispensed medication with a pickup token, optionally verifying the collector's biometric."
        icon={<PackageCheck className="h-5 w-5" />}
      >
        <div className="max-w-lg space-y-4">
          <label className="block">
            <span className="block text-sm font-medium mb-1">Pickup token</span>
            <input
              data-testid="pickup-token"
              className="w-full rounded-md border px-3 py-2 bg-background text-sm"
              placeholder="Enter or scan the pickup token"
              value={token}
              onChange={(e) => setToken(e.target.value)}
            />
          </label>

          <label className="block">
            <span className="block text-sm font-medium mb-1">Collector reference (for biometric verify)</span>
            <input
              data-testid="collector-ref"
              className="w-full rounded-md border px-3 py-2 bg-background text-sm"
              placeholder="Patient / collector CPID (only needed with a biometric)"
              value={subjectRef}
              onChange={(e) => setSubjectRef(e.target.value)}
            />
          </label>

          <BiometricVerifyField
            label="Verify collector by biometric (optional)"
            onProbe={setProbe}
            disabled={busy}
          />

          <button
            type="button"
            data-testid="claim-btn"
            className="rounded-md bg-primary text-primary-foreground px-4 py-2 text-sm font-medium disabled:opacity-50"
            onClick={claim}
            disabled={!token.trim() || busy}
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin inline" /> : "Confirm collection"}
          </button>

          {result && (
            <p
              data-testid="claim-result"
              className={`text-sm ${result.ok ? "text-green-600" : "text-red-600"}`}
            >
              {result.message}
            </p>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
