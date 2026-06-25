"use client";

/**
 * QR Order Claim — claim a patient-carried order QR at a fulfilling facility (criterion D).
 * Route: /diagnostics/intake/qr | Zone: lab | Guard: facility
 *
 * Real path: UI → BFF (/internal/v1/diagnostics/intake/qr/claim) → OROS → share-slip OTP claim.
 * No sensitive order data is returned without a valid OTP + an authenticated caller.
 */

import { useState } from "react";
import { Loader2, QrCode } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQrClaim, type DiagnosticOrder } from "@/hooks/queries/useDiagnosticsOrders";

export default function QrClaimPage() {
  const [shareToken, setShareToken] = useState("");
  const [otp, setOtp] = useState("");
  const [claimed, setClaimed] = useState<DiagnosticOrder | null>(null);
  const claimMut = useQrClaim();
  const canSubmit = shareToken.trim().length > 0 && !claimMut.isPending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setClaimed(null);
    claimMut.mutate(
      { shareToken: shareToken.trim(), otp: otp.trim() || undefined },
      { onSuccess: (order) => setClaimed(order) },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Claim Order QR" subtitle="Scan or enter a patient-carried order code to claim it">
        <form onSubmit={submit} className="max-w-md space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-medium">Share token<span className="text-danger"> *</span></span>
            <input value={shareToken} onChange={(e) => setShareToken(e.target.value)}
              className="impilo-pill-input w-full" aria-label="Share token" placeholder="from the QR / order slip" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium">OTP</span>
            <input value={otp} onChange={(e) => setOtp(e.target.value)}
              className="impilo-pill-input w-full" aria-label="OTP" placeholder="one-time code" />
          </label>
          <div className="flex items-center gap-3">
            <button type="submit" disabled={!canSubmit}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50">
              {claimMut.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <QrCode className="h-4 w-4" />}
              {claimMut.isPending ? "Claiming…" : "Claim order"}
            </button>
            {claimMut.isError && <span className="text-sm text-danger" role="alert">Claim failed — check the token and OTP.</span>}
          </div>
        </form>

        {claimed && (
          <div className="mt-6 max-w-md rounded-lg border border-success/40 bg-success-soft p-4 text-sm" role="status">
            <p className="font-medium">Order claimed</p>
            <p className="text-muted-foreground">
              <span className="font-mono">{claimed.orderId}</span>
              {claimed.accessionNumber ? <> · accession <span className="font-mono">{claimed.accessionNumber}</span></> : null}
              {" "}· patient {claimed.patientCpid} · {claimed.imagingState ?? claimed.status}
            </p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
