"use client";

import { useState } from "react";
import { Card } from "shared-ui";

interface VerifyResult {
  status: string;
  credentialType: string;
  subjectName: string;
  title: string;
  issuedBy: string;
  validFrom: string;
  validTo: string;
  verifiedAt: string;
}

export default function VerifyPage() {
  const [token, setToken] = useState("");
  const [result, setResult] = useState<VerifyResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    if (!token.trim()) return;
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const res = await fetch(`/v1/public/verify/${encodeURIComponent(token.trim())}`);
      if (res.ok) {
        setResult(await res.json());
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
    <div>
      <h1 className="text-2xl font-bold mb-2">Verify a Credential</h1>
      <p className="text-neutral-500 mb-6">Enter the verification token from the QR code on a certificate or credential card.</p>

      <Card className="max-w-xl mb-6">
        <form onSubmit={handleVerify} className="p-4 flex gap-4 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-neutral-700 mb-1">Verification Token</label>
            <input
              type="text"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Paste or type token here..."
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:ring-2 focus:ring-brand-primary focus:border-brand-primary"
              required
            />
          </div>
          <button type="submit" disabled={loading}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
            {loading ? "Verifying..." : "Verify"}
          </button>
        </form>
      </Card>

      {error && (
        <Card className="max-w-xl mb-6">
          <div className="p-4 text-red-800 bg-danger-soft rounded-lg text-sm">{error}</div>
        </Card>
      )}

      {result && (
        <Card className="max-w-xl">
          <div className="p-6">
            <div className={`text-center mb-6 p-4 rounded-lg ${
              result.status === "VALID" ? "bg-green-50 border border-green-200" :
              result.status === "EXPIRED" ? "bg-yellow-50 border border-yellow-200" :
              "bg-danger-soft border border-danger/28"
            }`}>
              <span className={`text-3xl font-bold ${
                result.status === "VALID" ? "text-green-700" :
                result.status === "EXPIRED" ? "text-yellow-700" :
                "text-danger"
              }`}>{result.status}</span>
            </div>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between py-2 border-b border-neutral-100">
                <dt className="text-neutral-500">Name</dt><dd className="font-medium">{result.subjectName}</dd>
              </div>
              <div className="flex justify-between py-2 border-b border-neutral-100">
                <dt className="text-neutral-500">Credential</dt><dd>{result.credentialType} — {result.title}</dd>
              </div>
              <div className="flex justify-between py-2 border-b border-neutral-100">
                <dt className="text-neutral-500">Issued By</dt><dd>{result.issuedBy}</dd>
              </div>
              <div className="flex justify-between py-2 border-b border-neutral-100">
                <dt className="text-neutral-500">Valid Period</dt><dd>{result.validFrom} — {result.validTo ?? "No expiry"}</dd>
              </div>
              <div className="flex justify-between py-2">
                <dt className="text-neutral-500">Verified At</dt><dd>{new Date(result.verifiedAt).toLocaleString()}</dd>
              </div>
            </dl>
          </div>
        </Card>
      )}
    </div>
  );
}
