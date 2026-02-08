"use client";

import { useState, useCallback } from "react";
import { mushexApi, type RemittanceToken } from "@/lib/mushexApi";

export default function RemittancePage() {
  const [token, setToken] = useState("");
  const [otp, setOtp] = useState("");
  const [result, setResult] = useState<RemittanceToken | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const claimRemittance = useCallback(async () => {
    if (!token.trim()) {
      setError("Remittance token is required.");
      return;
    }
    if (!otp.trim()) {
      setError("OTP is required.");
      return;
    }
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    setResult(null);
    try {
      const data = await mushexApi.claimRemittance(token.trim(), otp.trim());
      setResult(data);
      setSuccessMessage("Remittance claimed successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to claim remittance");
    } finally {
      setLoading(false);
    }
  }, [token, otp]);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Claim Remittance</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Enter your remittance token and OTP to claim a delegated payment.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-emerald-600 hover:text-emerald-800 font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      <div className="card p-6 max-w-lg">
        <div className="space-y-4">
          <div>
            <label htmlFor="token" className="block text-sm font-medium text-neutral-700 mb-1">
              Remittance Token
            </label>
            <input
              id="token"
              type="text"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Enter the remittance token..."
              className="input-field"
            />
          </div>

          <div>
            <label htmlFor="otp" className="block text-sm font-medium text-neutral-700 mb-1">
              One-Time Password (OTP)
            </label>
            <input
              id="otp"
              type="text"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && claimRemittance()}
              placeholder="Enter the OTP..."
              className="input-field"
              maxLength={8}
            />
          </div>

          <button
            onClick={claimRemittance}
            disabled={loading}
            className="btn-primary w-full"
          >
            {loading ? "Claiming..." : "Claim Remittance"}
          </button>
        </div>
      </div>

      {/* Result */}
      {result && (
        <div className="card p-6 mt-6 max-w-lg">
          <h3 className="text-sm font-semibold text-neutral-900 mb-3">Claim Result</h3>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-neutral-500">Token ID</span>
              <span className="font-mono text-xs">{result.id}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-neutral-500">Intent ID</span>
              <span className="font-mono text-xs">{result.intentId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-neutral-500">Status</span>
              <span className={`badge ${
                result.status === "CLAIMED"
                  ? "bg-emerald-100 text-emerald-800"
                  : result.status === "ACTIVE"
                  ? "bg-blue-100 text-blue-800"
                  : result.status === "EXPIRED"
                  ? "bg-red-100 text-red-800"
                  : "bg-neutral-100 text-neutral-600"
              }`}>
                {result.status}
              </span>
            </div>
            {result.claimedAt && (
              <div className="flex justify-between">
                <span className="text-neutral-500">Claimed At</span>
                <span className="text-xs">{new Date(result.claimedAt).toLocaleString()}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-neutral-500">Expires At</span>
              <span className="text-xs">{new Date(result.expiresAt).toLocaleString()}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
