"use client";

import Link from "next/link";
import { useState } from "react";
import { AlertTriangle, Loader2, ShieldAlert } from "lucide-react";
import { useRequestBreakGlass } from "@/hooks/queries/useTrustBreakGlass";

interface BreakGlassRequestPanelProps {
  patientId?: string;
  resourceType?: string;
  title?: string;
  description?: string;
  onSuccess?: () => void;
}

export function BreakGlassRequestPanel({
  patientId,
  resourceType = "PATIENT_RECORD",
  title = "Emergency break-glass access",
  description = "Request time-limited override when immediate access is required for patient safety. Every request is audited and queued for supervisor review.",
  onSuccess,
}: BreakGlassRequestPanelProps) {
  const requestBreakGlass = useRequestBreakGlass();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [requestId, setRequestId] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    if (!reason.trim()) {
      setError("A mandatory reason is required for break-glass access.");
      return;
    }

    try {
      const response = await requestBreakGlass.mutateAsync({
        reason: reason.trim(),
        resourceType,
        resourceId: patientId,
        patientId,
      });
      const submittedId = response.data?.id ?? null;
      setRequestId(submittedId);
      setSuccess("Break-glass request submitted and queued for supervisor review.");
      setReason("");
      onSuccess?.();
    } catch {
      setError("Break-glass request failed — verify trust headers and TSHEPO Authz availability.");
    }
  }

  return (
    <section
      className="rounded-xl border border-red-200 bg-red-50/60 p-4"
      data-testid="break-glass-request-panel"
    >
      <div className="flex items-start gap-2">
        <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-red-700" aria-hidden />
        <div className="flex-1">
          <h3 className="text-sm font-semibold text-red-950">{title}</h3>
          <p className="mt-1 text-xs text-red-900/80">{description}</p>
          <p className="mt-1 text-[11px] text-red-800/70">
            Posts to <code className="rounded bg-white/80 px-1">POST /internal/v1/trust/break-glass</code>
          </p>
        </div>
      </div>

      <form onSubmit={(e) => void handleSubmit(e)} className="mt-3 space-y-2">
        <label className="block text-xs font-medium text-red-900" htmlFor="break-glass-reason">
          Clinical justification
        </label>
        <textarea
          id="break-glass-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          required
          placeholder="Describe the emergency and why normal policy cannot be followed…"
          className="w-full rounded-lg border border-red-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-red-400"
        />
        {error ? (
          <p className="flex items-center gap-1 text-xs text-red-700">
            <AlertTriangle className="h-3.5 w-3.5" />
            {error}
          </p>
        ) : null}
        {success ? (
          <div className="space-y-1">
            <p className="text-xs text-emerald-800">{success}</p>
            <Link
              href={requestId ? `/admin/break-glass?requestId=${encodeURIComponent(requestId)}` : "/admin/break-glass"}
              className="text-xs font-medium text-red-900 underline"
            >
              Open break-glass review log
            </Link>
          </div>
        ) : null}
        <button
          type="submit"
          disabled={requestBreakGlass.isPending}
          className="inline-flex items-center gap-1.5 rounded-lg bg-red-700 px-3 py-2 text-xs font-medium text-white hover:bg-red-800 disabled:opacity-50"
        >
          {requestBreakGlass.isPending ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <ShieldAlert className="h-3.5 w-3.5" />
          )}
          Submit break-glass request
        </button>
      </form>
    </section>
  );
}
