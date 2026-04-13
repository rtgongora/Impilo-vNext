"use client";

import { useRef, useState } from "react";
import { getShareSlipPublicBaseUrl } from "@/lib/shareSlipPublic";

interface ClaimResult {
  success?: boolean;
  documentIds?: string[];
  signedUrls?: string[];
  message?: string;
}

/** Migrated from ui/self-service/(citizen)/claim — not behind CITIZEN gate (OTP-based public contract). */
export default function ShareClaimPage() {
  const base = getShareSlipPublicBaseUrl();
  const [shareToken, setShareToken] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"token" | "otp" | "result">("token");
  const [result, setResult] = useState<ClaimResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const lastClaimSubmitRef = useRef<number>(0);
  const CLAIM_COOLDOWN_MS = 2000;

  if (!base) {
    return (
      <div className="max-w-xl rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="font-medium mb-2">Share-slip base URL not configured</p>
        <p className="mb-2">
          Set <code className="text-xs">NEXT_PUBLIC_SHARE_SLIP_PUBLIC_URL</code> to the public origin of share-slip-service (e.g.{" "}
          <code className="text-xs">http://localhost:PORT</code>) so this page can call{" "}
          <code className="text-xs">GET /v1/public/share/verify/{"{token}"}</code> and{" "}
          <code className="text-xs">POST /v1/public/share/claim</code>.
        </p>
        <p className="text-xs text-gray-600">Until then, use <code className="text-[10px]">ui/self-service</code> if it is wired to that service.</p>
      </div>
    );
  }

  async function handleCheckToken(e: React.FormEvent) {
    e.preventDefault();
    if (!shareToken.trim()) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${base}/v1/public/share/verify/${encodeURIComponent(shareToken.trim())}`);
      if (res.ok) setStep("otp");
      else if (res.status === 404) setError("Share link not found or expired.");
      else if (res.status === 429) setError("Too many requests. Wait and retry.");
      else setError("Unable to verify share link.");
    } catch {
      setError("Network error.");
    } finally {
      setLoading(false);
    }
  }

  async function handleClaim(e: React.FormEvent) {
    e.preventDefault();
    const now = Date.now();
    if (now - lastClaimSubmitRef.current < CLAIM_COOLDOWN_MS) {
      setError("Please wait before trying again.");
      return;
    }
    lastClaimSubmitRef.current = now;
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${base}/v1/public/share/claim`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ shareToken: shareToken.trim(), otp: otp.trim() }),
      });
      if (res.ok) {
        const data = (await res.json()) as ClaimResult;
        setResult(data);
        setStep("result");
      } else if (res.status === 429) setError("Too many attempts.");
      else {
        const err = await res.json().catch(() => null);
        setError((err as { message?: string })?.message ?? "Claim failed.");
      }
    } catch {
      setError("Network error.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-xl space-y-4">
      <p className="text-sm text-gray-600">Claim shared documents using share-slip public endpoints.</p>
      {step === "token" && (
        <form onSubmit={handleCheckToken} className="space-y-3 bg-white border border-gray-200 rounded-lg p-4">
          <label className="block text-sm font-medium text-gray-700">Share token</label>
          <input
            value={shareToken}
            onChange={(e) => setShareToken(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            required
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button type="submit" disabled={loading} className="bg-impilo-500 text-white px-4 py-2 rounded-lg text-sm disabled:opacity-50">
            {loading ? "Checking…" : "Continue"}
          </button>
        </form>
      )}
      {step === "otp" && (
        <form onSubmit={handleClaim} className="space-y-3 bg-white border border-gray-200 rounded-lg p-4">
          <label className="block text-sm font-medium text-gray-700">OTP</label>
          <input value={otp} onChange={(e) => setOtp(e.target.value)} className="w-full border rounded-lg px-3 py-2 text-sm" required />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button type="submit" disabled={loading} className="bg-impilo-500 text-white px-4 py-2 rounded-lg text-sm disabled:opacity-50">
            {loading ? "Claiming…" : "Claim"}
          </button>
        </form>
      )}
      {step === "result" && result && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-900">
          <p className="font-medium">{result.message ?? "Claim completed"}</p>
          {result.documentIds && result.documentIds.length > 0 && (
            <p className="text-xs mt-2">Documents: {result.documentIds.join(", ")}</p>
          )}
        </div>
      )}
    </div>
  );
}
