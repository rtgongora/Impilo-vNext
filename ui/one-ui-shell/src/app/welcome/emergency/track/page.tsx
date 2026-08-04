"use client";

/**
 * Public emergency status tracking (no sign-in). An anonymous requester enters the
 * reference from their assistance receipt and sees a disclosure-limited status via the
 * public gateway lane GET /internal/v1/public/gateway/sos/{reference}. The response is
 * PII-free (reference, status, stage, callback-pending, created-at) — no callback
 * number, no description, no location, no personal fields.
 */

import { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { Loader2, RefreshCw } from "lucide-react";
import { PublicShell } from "@/components/public/PublicShell";
import { SosStatusTimeline } from "@/components/public/emergency/SosStatusTimeline";
import { apiClient } from "@/lib/api-client";

interface SosStatus {
  requestReference?: string;
  status?: string;
  stage?: string;
  callbackPending?: boolean;
  createdAt?: string;
}

function EmergencyTrackInner() {
  const searchParams = useSearchParams();
  const [reference, setReference] = useState("");
  const [status, setStatus] = useState<SosStatus | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const prefillDone = useRef(false);

  async function track(rawRef: string) {
    const trimmed = rawRef.trim();
    if (!trimmed) return;
    setLoading(true);
    setError("");
    setNotFound(false);
    setStatus(null);
    try {
      const res = await apiClient.get<SosStatus>(
        `/internal/v1/public/gateway/sos/${encodeURIComponent(trimmed)}`,
      );
      setStatus(res);
    } catch (e) {
      if ((e as { status?: number })?.status === 404) {
        setNotFound(true);
      } else {
        setError("Status is temporarily unavailable. Please try again shortly.");
      }
    } finally {
      setLoading(false);
    }
  }

  // ?ref= prefill from the assistance receipt link.
  useEffect(() => {
    const prefill = searchParams.get("ref");
    if (prefill && !prefillDone.current) {
      prefillDone.current = true;
      setReference(prefill);
      void track(prefill);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        /{" "}
        <Link href="/welcome/emergency" className="hover:text-slate-900">
          Emergency
        </Link>{" "}
        / Track a request
      </nav>
      <h1 className="mt-2 text-3xl font-bold text-slate-900">Track your emergency request</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Enter the reference from your assistance receipt to see the latest status. If the situation
        is life-threatening, call the emergency numbers — do not wait.
      </p>

      <form
        className="mt-6 flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void track(reference);
        }}
      >
        <input
          type="text"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          placeholder="e.g. SOS-2026-000123"
          aria-label="Request reference"
          data-testid="track-reference-input"
          className="min-w-64 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:border-emerald-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <RefreshCw className="h-4 w-4" />}
          Check status
        </button>
      </form>

      {error && <p className="mt-4 text-sm text-red-700">{error}</p>}
      {notFound && (
        <p className="mt-4 text-sm text-slate-600" data-testid="track-not-found">
          We could not find a request with that reference. Check the reference and try again.
        </p>
      )}

      {status && (
        <section
          data-testid="track-status"
          className="mt-6 rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-mono text-sm text-slate-500">{status.requestReference}</span>
            {status.status && (
              <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
                {status.status.replace(/_/g, " ")}
              </span>
            )}
          </div>
          {status.stage && <p className="mt-3 text-lg font-semibold text-slate-900">{status.stage}</p>}
          <SosStatusTimeline status={status.status} callbackPending={status.callbackPending} />
          {status.callbackPending && (
            <p className="mt-3 text-sm text-slate-600">
              Keep your phone nearby — the responder calls the number you provided to confirm
              before anything is arranged.
            </p>
          )}
        </section>
      )}
    </PublicShell>
  );
}

export default function EmergencyTrackPage() {
  return (
    <Suspense
      fallback={
        <PublicShell>
          <div className="py-16 text-sm text-slate-500">Loading…</div>
        </PublicShell>
      }
    >
      <EmergencyTrackInner />
    </Suspense>
  );
}
