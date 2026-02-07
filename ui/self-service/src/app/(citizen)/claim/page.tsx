"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

interface ClaimResult {
  success: boolean;
  documentIds: string[];
  signedUrls: string[];
  message: string;
}

export default function ClaimPage() {
  const [shareToken, setShareToken] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"token" | "otp" | "result">("token");
  const [result, setResult] = useState<ClaimResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleCheckToken(e: React.FormEvent) {
    e.preventDefault();
    if (!shareToken.trim()) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`/v1/public/share/verify/${encodeURIComponent(shareToken.trim())}`);
      if (res.ok) {
        setStep("otp");
      } else if (res.status === 404) {
        setError("Share link not found or has expired.");
      } else if (res.status === 429) {
        setError("Too many requests. Please wait and try again.");
      } else {
        setError("Unable to verify share link.");
      }
    } catch {
      setError("Network error.");
    } finally {
      setLoading(false);
    }
  }

  async function handleClaim(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/v1/public/share/claim", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ shareToken: shareToken.trim(), otp: otp.trim() }),
      });
      if (res.ok) {
        const data = await res.json();
        setResult(data);
        setStep("result");
      } else if (res.status === 429) {
        setError("Too many attempts. Please wait and try again.");
      } else {
        const err = await res.json().catch(() => null);
        setError(err?.message ?? "Claim failed. Check your OTP and try again.");
      }
    } catch {
      setError("Network error.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-2">Claim Shared Documents</h1>
      <p className="text-neutral-500 mb-6">Enter your share token and OTP to access shared documents.</p>

      {step === "token" && (
        <Card className="max-w-xl">
          <CardHeader><CardTitle>Step 1: Enter Share Token</CardTitle></CardHeader>
          <form onSubmit={handleCheckToken} className="p-4 space-y-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Share Token</label>
              <input type="text" value={shareToken} onChange={(e) => setShareToken(e.target.value)}
                placeholder="Paste your share token..." className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
            </div>
            {error && <div className="p-3 bg-red-50 text-red-800 rounded-lg text-sm">{error}</div>}
            <button type="submit" disabled={loading}
              className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
              {loading ? "Checking..." : "Continue"}
            </button>
          </form>
        </Card>
      )}

      {step === "otp" && (
        <Card className="max-w-xl">
          <CardHeader><CardTitle>Step 2: Enter OTP</CardTitle></CardHeader>
          <form onSubmit={handleClaim} className="p-4 space-y-4">
            <p className="text-sm text-neutral-600">An OTP has been sent to the registered contact. Enter it below to claim the documents.</p>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">One-Time Password</label>
              <input type="text" value={otp} onChange={(e) => setOtp(e.target.value)}
                placeholder="6-digit OTP" maxLength={6} className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm text-center text-lg tracking-widest" required />
            </div>
            {error && <div className="p-3 bg-red-50 text-red-800 rounded-lg text-sm">{error}</div>}
            <div className="flex gap-2">
              <button type="button" onClick={() => { setStep("token"); setError(""); }}
                className="px-4 py-2 bg-neutral-200 text-neutral-700 rounded-lg text-sm font-medium hover:bg-neutral-300">
                Back
              </button>
              <button type="submit" disabled={loading}
                className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
                {loading ? "Claiming..." : "Claim Documents"}
              </button>
            </div>
          </form>
        </Card>
      )}

      {step === "result" && result && (
        <Card className="max-w-xl">
          <div className="p-6">
            <div className={`text-center mb-4 p-4 rounded-lg ${result.success ? "bg-green-50" : "bg-red-50"}`}>
              <span className={`text-xl font-bold ${result.success ? "text-green-700" : "text-red-700"}`}>
                {result.success ? "Documents Ready" : "Claim Failed"}
              </span>
              <p className="text-sm text-neutral-600 mt-1">{result.message}</p>
            </div>
            {result.signedUrls.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-semibold text-neutral-700">Download Links</h3>
                {result.signedUrls.map((url, i) => (
                  <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                    className="block px-4 py-2 bg-blue-50 text-blue-700 rounded-lg text-sm hover:bg-blue-100 transition-colors">
                    Document {i + 1} — Click to download
                  </a>
                ))}
                <p className="text-xs text-neutral-400 mt-2">Links expire after a short period. Download documents promptly.</p>
              </div>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
