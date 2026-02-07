"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

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

export default function VerifyCredentialPage() {
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
      const res = await fetch(`/v1/public/verify/${encodeURIComponent(token)}`);
      if (res.ok) {
        const data = await res.json();
        setResult(data.data ?? data);
      } else if (res.status === 429) {
        setError("Rate limit exceeded. Please try again later.");
      } else {
        setError(`Verification failed (${res.status})`);
      }
    } catch {
      setError("Network error during verification");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Verify Credential</h1>

      <Card className="max-w-xl mb-6">
        <CardHeader>
          <CardTitle>Enter Verification Token</CardTitle>
        </CardHeader>
        <form onSubmit={handleVerify} className="p-4 flex gap-4 items-end">
          <div className="flex-1">
            <input type="text" value={token} onChange={(e) => setToken(e.target.value)}
              placeholder="Paste verification token from QR code..."
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
          </div>
          <button type="submit" disabled={loading}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
            {loading ? "Verifying..." : "Verify"}
          </button>
        </form>
      </Card>

      {error && (
        <Card className="max-w-xl">
          <div className="p-4 text-red-800 bg-red-50 rounded-lg">{error}</div>
        </Card>
      )}

      {result && (
        <Card className="max-w-xl">
          <div className="p-6">
            <div className={`text-center mb-4 p-4 rounded-lg ${
              result.status === "VALID" ? "bg-green-50" :
              result.status === "EXPIRED" ? "bg-yellow-50" :
              "bg-red-50"
            }`}>
              <span className={`text-2xl font-bold ${
                result.status === "VALID" ? "text-green-700" :
                result.status === "EXPIRED" ? "text-yellow-700" :
                "text-red-700"
              }`}>{result.status}</span>
            </div>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-neutral-500">Type</dt><dd>{result.credentialType}</dd></div>
              <div className="flex justify-between"><dt className="text-neutral-500">Subject</dt><dd>{result.subjectName}</dd></div>
              <div className="flex justify-between"><dt className="text-neutral-500">Title</dt><dd>{result.title}</dd></div>
              <div className="flex justify-between"><dt className="text-neutral-500">Issued By</dt><dd>{result.issuedBy}</dd></div>
              <div className="flex justify-between"><dt className="text-neutral-500">Valid</dt><dd>{result.validFrom} — {result.validTo ?? "∞"}</dd></div>
              <div className="flex justify-between"><dt className="text-neutral-500">Verified At</dt><dd>{new Date(result.verifiedAt).toLocaleString()}</dd></div>
            </dl>
          </div>
        </Card>
      )}
    </div>
  );
}
