"use client";

/**
 * Citizen feedback submission form. POSTs to /internal/v1/nompilo/rito/cases.
 * Route: /my-life/feedback/new
 */

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Loader2 } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface CreatedCase {
  id?: string;
  caseReference?: string;
}

const CASE_TYPES = ["COMPLAINT", "COMPLIMENT", "SUGGESTION", "SAFETY_CONCERN"] as const;

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return (payload as T) ?? null;
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Could not submit your feedback (HTTP ${obj.status}).`;
  }
  return "Could not submit your feedback. Please try again.";
}

function isCaseType(value: string | null): value is (typeof CASE_TYPES)[number] {
  return value !== null && (CASE_TYPES as readonly string[]).includes(value);
}

export default function ShareFeedbackPage() {
  const searchParams = useSearchParams();
  const requestedType = searchParams.get("type");
  const [caseType, setCaseType] = useState<(typeof CASE_TYPES)[number]>(
    isCaseType(requestedType) ? requestedType : "COMPLAINT",
  );
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [anonymous, setAnonymous] = useState(false);
  const [facilityId, setFacilityId] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreatedCase | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const body: Record<string, unknown> = {
        caseType,
        title,
        description,
        anonymous,
        source: "WEB_PORTAL",
      };
      if (facilityId.trim()) body.facilityId = facilityId.trim();
      const res = await apiClient.post<unknown>("/internal/v1/nompilo/rito/cases", body);
      setCreated(unwrap<CreatedCase>(res));
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Share feedback"
        subtitle="Your voice helps improve care. You can submit anonymously."
        serviceSlug="rito"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <div className="mb-4">
          <Link
            href="/my-life/feedback"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back
          </Link>
        </div>

        <div className="mb-4 max-w-xl rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800">
          Giving birth or postnatal care feedback? Use the{" "}
          <Link href="/my-life/feedback/respectful-maternity" className="font-semibold underline">
            respectful maternity care survey
          </Link>{" "}
          instead — it asks the right questions and stays anonymous by default.
        </div>

        {created ? (
          <div className="rounded-2xl border border-teal-300 bg-teal-50 p-6">
            <div className="mb-2 flex items-center gap-2 font-semibold text-teal-900">
              <CheckCircle2 className="h-5 w-5" /> Thank you — your feedback was submitted
            </div>
            <p className="text-sm text-teal-800">
              Your case reference is{" "}
              <span className="font-mono font-semibold">
                {created.caseReference ?? created.id ?? "(pending)"}
              </span>
              . Keep it to track progress.
            </p>
            {created.id && (
              <Link
                href={`/my-life/feedback/${encodeURIComponent(created.id)}`}
                className="mt-4 inline-flex rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
              >
                Track this case
              </Link>
            )}
          </div>
        ) : (
          <form onSubmit={submit} className="max-w-xl space-y-4">
            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {error}
              </div>
            )}
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">Type</label>
              <select
                value={caseType}
                onChange={(e) => setCaseType(e.target.value as (typeof CASE_TYPES)[number])}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              >
                {CASE_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">Title</label>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                required
                rows={5}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">
                Facility ID (optional)
              </label>
              <input
                value={facilityId}
                onChange={(e) => setFacilityId(e.target.value)}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input
                type="checkbox"
                checked={anonymous}
                onChange={(e) => setAnonymous(e.target.checked)}
              />
              Submit anonymously
            </label>
            <button
              type="submit"
              disabled={submitting || !title || !description}
              className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
              {submitting ? "Submitting…" : "Submit feedback"}
            </button>
          </form>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
