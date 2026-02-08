"use client";

import { useState } from "react";
import { msikaFlowApi } from "@/lib/msikaFlowApi";

export default function PickupPage() {
  const [orderId, setOrderId] = useState("");
  const [slip, setSlip] = useState<{ tokenId: string; otp: string; rawToken: string; expiresAt: string; qrPayload: string } | null>(null);
  const [claimCode, setClaimCode] = useState("");
  const [claimResult, setClaimResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const issueToken = async () => {
    if (!orderId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await msikaFlowApi.issuePickupToken(orderId);
      setSlip(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to issue pickup token");
    } finally {
      setLoading(false);
    }
  };

  const claimPickup = async () => {
    if (!claimCode.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await msikaFlowApi.claimPickup(claimCode);
      setClaimResult(`Pickup claimed! Order: ${result.orderId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Claim failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">Pickup & Collection</h1>
      <p className="text-sm text-neutral-500 mb-6">Issue pickup tokens, view QR codes, and claim orders.</p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">{error}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Issue Token */}
        <div className="card p-6">
          <h2 className="text-lg font-semibold mb-4">Issue Pickup Token</h2>
          <div className="space-y-3">
            <input
              type="text"
              value={orderId}
              onChange={(e) => setOrderId(e.target.value)}
              placeholder="Enter Order ID"
              className="input-field"
            />
            <button onClick={issueToken} disabled={loading} className="btn-primary w-full">
              {loading ? "Issuing..." : "Issue Token"}
            </button>
          </div>

          {slip && (
            <div className="mt-4 p-4 bg-emerald-50 rounded-lg border border-emerald-200">
              <h3 className="font-semibold text-emerald-800 mb-2">Pickup Slip</h3>
              <div className="space-y-1 text-sm">
                <p><strong>OTP:</strong> <span className="font-mono text-2xl tracking-widest text-emerald-700">{slip.otp}</span></p>
                <p><strong>Token:</strong> <span className="font-mono text-xs break-all">{slip.rawToken}</span></p>
                <p><strong>Expires:</strong> {new Date(slip.expiresAt).toLocaleString()}</p>
              </div>
              <div className="mt-3 p-3 bg-white rounded border text-center">
                <p className="text-xs text-neutral-500 mb-1">QR Payload</p>
                <p className="font-mono text-xs break-all">{slip.qrPayload}</p>
              </div>
            </div>
          )}
        </div>

        {/* Claim Pickup */}
        <div className="card p-6">
          <h2 className="text-lg font-semibold mb-4">Claim Pickup</h2>
          <div className="space-y-3">
            <input
              type="text"
              value={claimCode}
              onChange={(e) => setClaimCode(e.target.value)}
              placeholder="Enter token or OTP"
              className="input-field"
            />
            <button onClick={claimPickup} disabled={loading} className="btn-primary w-full">
              {loading ? "Claiming..." : "Claim Pickup"}
            </button>
          </div>

          {claimResult && (
            <div className="mt-4 p-3 bg-emerald-50 rounded-lg border border-emerald-200 text-sm text-emerald-800">
              {claimResult}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
