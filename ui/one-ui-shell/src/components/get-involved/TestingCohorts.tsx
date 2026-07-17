"use client";

/**
 * Open testing / participation cohorts (gateway ADR W4). Lists cohorts a citizen can join to
 * help test Impilo, and enrols them WITHOUT an account — a contact is required so the team can
 * reach them about testing (that is the whole point of enrolling). Fail-soft list; honest
 * enrol errors.
 *
 * Backend:
 *   GET  /internal/v1/public/gateway/get-involved/cohorts -> [{ id, name, segment, description }]
 *   POST /internal/v1/public/gateway/get-involved/cohorts/{id}/enroll { contact, segment }
 *        -> { status, message }
 */

import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";

interface Cohort {
  id: string;
  name?: string;
  segment?: string;
  description?: string;
}

interface EnrollResult {
  status?: string;
  message?: string;
}

function normalizeCohorts(res: unknown): Cohort[] {
  const list = Array.isArray(res)
    ? res
    : res && typeof res === "object" && Array.isArray((res as { data?: unknown[] }).data)
      ? (res as { data: unknown[] }).data
      : [];
  return list.filter((x): x is Cohort => Boolean(x) && typeof (x as Cohort).id === "string");
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.status === 429) return "Too many requests from this connection. Please try again later.";
    if (obj.error?.message) return obj.error.message;
  }
  return "We could not sign you up just now. Please try again later.";
}

function CohortCard({ cohort }: { cohort: Cohort }) {
  const [open, setOpen] = useState(false);
  const [contact, setContact] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enrolled, setEnrolled] = useState<EnrollResult | null>(null);

  const enroll = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await apiClient.post<EnrollResult>(
        `/internal/v1/public/gateway/get-involved/cohorts/${encodeURIComponent(cohort.id)}/enroll`,
        { contact: contact.trim(), segment: cohort.segment },
      );
      setEnrolled(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <li className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
      {cohort.segment && (
        <span className="w-fit rounded-full bg-sky-50 px-2 py-0.5 text-[11px] font-medium text-sky-700">
          {cohort.segment}
        </span>
      )}
      <h3 className="mt-2 font-semibold text-slate-900">{cohort.name || "Testing group"}</h3>
      {cohort.description && <p className="mt-1 flex-grow text-sm text-slate-600">{cohort.description}</p>}

      {enrolled ? (
        <p className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
          {enrolled.message || "Thank you for signing up to help test. We will be in touch."}
        </p>
      ) : open ? (
        <form onSubmit={enroll} className="mt-4 space-y-2">
          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-2 text-sm text-red-800">{error}</div>
          )}
          <input
            value={contact}
            onChange={(e) => setContact(e.target.value)}
            required
            maxLength={255}
            placeholder="Phone or email so we can reach you"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={submitting || !contact.trim()}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              {submitting ? "Signing up…" : "Sign up to help test"}
            </button>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mt-4 w-fit rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-sm font-semibold text-emerald-800 hover:bg-emerald-100"
        >
          Help test this
        </button>
      )}
    </li>
  );
}

export function TestingCohorts() {
  const [cohorts, setCohorts] = useState<Cohort[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await apiClient.get<unknown>("/internal/v1/public/gateway/get-involved/cohorts");
        if (!cancelled) setCohorts(normalizeCohorts(res));
      } catch {
        if (!cancelled)
          setError("We could not load the testing opportunities just now. Please try again later.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) return <p className="text-sm text-slate-500">Loading testing opportunities…</p>;
  if (error)
    return <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>;

  if (cohorts.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600" data-testid="cohorts-empty">
        There are no open testing groups right now. Check back soon, or{" "}
        <a href="/get-involved/idea" className="font-medium text-emerald-700 hover:text-emerald-800">
          share an idea
        </a>{" "}
        in the meantime.
      </div>
    );
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2" data-testid="testing-cohorts">
      {cohorts.map((cohort) => (
        <CohortCard key={cohort.id} cohort={cohort} />
      ))}
    </ul>
  );
}
