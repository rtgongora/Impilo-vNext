"use client";

/**
 * Callback verification console — the operator surface for PD-3 "callback before
 * dispatch". Lists anonymous emergency requests held in AWAITING_CALLBACK, shows the
 * caller's callback number so a dispatcher can phone back and confirm the emergency,
 * then marks the callback verified (POST /daidzai/requests/{id}/verify-callback), which
 * releases the request for triage/dispatch. Route: /work/daidzai/verify-callbacks
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw, PhoneCall, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface CallbackRequest {
  id: string;
  requestReference?: string;
  emergencyCategory?: string;
  severity?: string;
  description?: string;
  callbackNumber?: string;
  locationDescription?: string;
  status?: string;
  createdAt?: string;
}

function asArray(p: unknown): CallbackRequest[] {
  return Array.isArray(p) ? (p as CallbackRequest[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load requests awaiting callback. Please try again.";
}

export default function DaidzaiVerifyCallbacksPage() {
  const [data, setData] = useState<CallbackRequest[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(
        asArray(
          await apiClient.get<unknown>(
            "/internal/v1/daidzai/requests?status=AWAITING_CALLBACK",
          ),
        ),
      );
    } catch (e) {
      setError(errMessage(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const verify = async (id: string) => {
    setBusy(id);
    setError(null);
    try {
      await apiClient.post(`/internal/v1/daidzai/requests/${encodeURIComponent(id)}/verify-callback`, {});
      // Verified requests leave the worklist — refresh from source.
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Callback verification"
        subtitle="Phone back anonymous callers and confirm the emergency before dispatch (PD-3)"
        serviceSlug="daidzai"
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">
            These requests are held until a responder confirms the callback. Dispatch is blocked
            until you verify.
          </p>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not complete
            </div>
            <p>{error}</p>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            No requests awaiting callback.
          </div>
        ) : (
          <ul className="space-y-3">
            {data.map((req) => (
              <li
                key={req.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <div className="font-mono text-xs text-muted-foreground">
                    {req.requestReference ?? req.id}
                  </div>
                  <div className="text-sm font-medium">
                    {req.emergencyCategory?.replace(/_/g, " ")}
                    {req.severity ? ` · ${req.severity}` : ""}
                  </div>
                  {req.callbackNumber && (
                    <a
                      href={`tel:${req.callbackNumber}`}
                      className="mt-1 inline-flex items-center gap-1.5 text-sm font-semibold text-teal-700 hover:text-teal-800"
                    >
                      <PhoneCall className="h-3.5 w-3.5" /> {req.callbackNumber}
                    </a>
                  )}
                  {(req.description || req.locationDescription) && (
                    <div className="mt-1 text-xs text-muted-foreground">
                      {[req.locationDescription, req.description].filter(Boolean).join(" — ")}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => void verify(req.id)}
                  disabled={busy === req.id}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
                >
                  {busy === req.id ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <CheckCircle2 className="h-4 w-4" />
                  )}
                  Mark callback verified
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/daidzai/verify-callbacks" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
