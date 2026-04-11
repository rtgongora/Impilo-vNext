"use client";

import { useState } from "react";
import {
  credentialVerifyUrlForToken,
  getCredentialVerifyPublicBaseUrl,
  parseCredentialVerificationResponse,
  type CredentialVerificationDisplay,
} from "@/lib/credentialVerifyPublic";

/**
 * Canonical Experience page for public credential verification.
 * Contract: GET {NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL}/v1/public/verify/{token}
 * (credential-verification-service PublicVerifyController).
 */
export default function VerifyCredentialPage() {
  const base = getCredentialVerifyPublicBaseUrl();
  const [token, setToken] = useState("");
  const [result, setResult] = useState<CredentialVerificationDisplay | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if (!base) {
    return (
      <div className="max-w-xl rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="font-medium mb-2">Credential verifier base URL not configured</p>
        <p className="mb-2">
          Set <code className="text-xs">NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL</code> to the public origin of
          credential-verification-service (e.g. <code className="text-xs">http://localhost:8094</code>) so this page can
          call <code className="text-xs">GET /v1/public/verify/{"{token}"}</code>.
        </p>
        <p className="text-xs text-gray-600">
          Same runtime pattern as <code className="text-[10px]">NEXT_PUBLIC_SHARE_SLIP_PUBLIC_URL</code> for{" "}
          <code className="text-[10px]">/share/claim</code>.
        </p>
      </div>
    );
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    if (!token.trim()) return;
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const url = credentialVerifyUrlForToken(base, token.trim());
      const res = await fetch(url, { method: "GET" });
      if (res.ok) {
        const json = await res.json();
        const parsed = parseCredentialVerificationResponse(json);
        if (!parsed) {
          setError("Unexpected response from verifier.");
          return;
        }
        setResult(parsed);
      } else if (res.status === 429) {
        setError("Too many requests. Please wait and try again.");
      } else {
        setError("Credential not found or verification failed.");
      }
    } catch {
      setError("Network error. Please check your connection.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-xl space-y-6">
      <p className="text-sm text-gray-600">
        Enter the verification token from the QR code or link on a certificate or credential card.
      </p>

      <form onSubmit={handleVerify} className="flex flex-col sm:flex-row gap-3 sm:items-end bg-white border border-gray-200 rounded-lg p-4">
        <div className="flex-1 min-w-0">
          <label htmlFor="verify-token" className="block text-sm font-medium text-gray-700 mb-1">
            Verification token
          </label>
          <input
            id="verify-token"
            type="text"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="Paste or type token…"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            required
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 shrink-0"
        >
          {loading ? "Verifying…" : "Verify"}
        </button>
      </form>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</div>
      )}

      {result && (
        <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
          <div
            className={`text-center p-4 border-b ${
              result.status === "VALID"
                ? "bg-green-50 border-green-200"
                : result.status === "EXPIRED"
                  ? "bg-yellow-50 border-yellow-200"
                  : "bg-red-50 border-red-200"
            }`}
          >
            <span
              className={`text-2xl font-bold ${
                result.status === "VALID"
                  ? "text-green-700"
                  : result.status === "EXPIRED"
                    ? "text-yellow-700"
                    : "text-red-700"
              }`}
            >
              {result.status}
            </span>
          </div>
          <dl className="p-6 space-y-3 text-sm">
            <div className="flex justify-between gap-4 py-2 border-b border-gray-100">
              <dt className="text-gray-500 shrink-0">Name</dt>
              <dd className="font-medium text-right">{result.subjectName || "—"}</dd>
            </div>
            <div className="flex justify-between gap-4 py-2 border-b border-gray-100">
              <dt className="text-gray-500 shrink-0">Credential</dt>
              <dd className="text-right">
                {result.credentialType || "—"} — {result.title || "—"}
              </dd>
            </div>
            <div className="flex justify-between gap-4 py-2 border-b border-gray-100">
              <dt className="text-gray-500 shrink-0">Issued by</dt>
              <dd className="text-right">{result.issuedBy || "—"}</dd>
            </div>
            <div className="flex justify-between gap-4 py-2 border-b border-gray-100">
              <dt className="text-gray-500 shrink-0">Valid period</dt>
              <dd className="text-right">
                {result.validFrom || "—"} — {result.validTo ?? "No expiry"}
              </dd>
            </div>
            <div className="flex justify-between gap-4 py-2">
              <dt className="text-gray-500 shrink-0">Verified at</dt>
              <dd className="text-right">
                {result.verifiedAt
                  ? (() => {
                      const d = new Date(result.verifiedAt);
                      return Number.isNaN(d.getTime()) ? result.verifiedAt : d.toLocaleString();
                    })()
                  : "—"}
              </dd>
            </div>
          </dl>
        </div>
      )}
    </div>
  );
}
