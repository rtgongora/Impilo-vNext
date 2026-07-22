"use client";

import { useEffect, useState } from "react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";

type RequestType = "NEW" | "REPLACEMENT";

interface FormData {
  type: RequestType;
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  sex: string;
}

const INITIAL_FORM: FormData = {
  type: "NEW",
  givenName: "",
  familyName: "",
  dateOfBirth: "",
  sex: "",
};

// Resumable draft (G-CZO-09): a citizen on a poor connection can lose the page and not
// lose progress. The draft holds only the demographic fields the form already collects.
const DRAFT_KEY = "impilo:health-id-request-draft";

function hasContent(form: FormData): boolean {
  return Boolean(form.givenName || form.familyName || form.dateOfBirth || form.sex) || form.type !== "NEW";
}

/** Migrated from ui/portal/(citizen)/request-id */
export default function CitizenRequestHealthIdPage() {
  const [form, setForm] = useState<FormData>(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [restored, setRestored] = useState(false);

  // Restore an in-progress draft on mount.
  useEffect(() => {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as Partial<FormData>;
      const next = { ...INITIAL_FORM, ...parsed };
      if (hasContent(next)) {
        setForm(next);
        setRestored(true);
      }
    } catch {
      // Ignore corrupt draft.
    }
  }, []);

  // Persist the draft as the citizen types (skip the empty initial state).
  useEffect(() => {
    try {
      if (hasContent(form)) localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
    } catch {
      // Storage unavailable (private mode) — degrade silently.
    }
  }, [form]);

  function clearDraft() {
    try {
      localStorage.removeItem(DRAFT_KEY);
    } catch {
      // ignore
    }
  }

  function startOver() {
    clearDraft();
    setForm(INITIAL_FORM);
    setRestored(false);
  }

  function update<K extends keyof FormData>(key: K, value: FormData[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await citizenPortalApi.requestId({
        type: form.type,
        givenName: form.givenName || undefined,
        familyName: form.familyName || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
        sex: form.sex || undefined,
      });
      clearDraft();
      setRestored(false);
      setSubmitted(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "An unexpected error occurred");
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <div className="max-w-lg mx-auto bg-green-50 border border-green-200 rounded-xl p-6 text-center">
        <h2 className="text-lg font-semibold text-green-800 mb-2">Request submitted</h2>
        <p className="text-sm text-green-700">
          Your request was received. You will be notified when your document is ready.
        </p>
        <button
          type="button"
          onClick={() => {
            clearDraft();
            setSubmitted(false);
            setForm(INITIAL_FORM);
          }}
          className="mt-4 bg-neutral-100 text-foreground px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-100"
        >
          Submit another
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto bg-card rounded-xl border border-border p-6">
      <h1 className="text-xl font-semibold text-foreground mb-1">Request Impilo ID</h1>
      <p className="text-sm text-muted-foreground mb-6">New or replacement health identity document.</p>
      {restored && (
        <div className="mb-4 flex items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50 p-3">
          <p className="text-sm text-blue-800">We restored your saved progress.</p>
          <button
            type="button"
            onClick={startOver}
            className="shrink-0 text-sm font-medium text-blue-700 underline hover:text-blue-900"
          >
            Start over
          </button>
        </div>
      )}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-foreground mb-1">
            Request type
          </label>
          <select
            id="type"
            value={form.type}
            onChange={(e) => update("type", e.target.value as RequestType)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            <option value="NEW">New ID</option>
            <option value="REPLACEMENT">Replacement</option>
          </select>
        </div>
        <div>
          <label htmlFor="givenName" className="block text-sm font-medium text-foreground mb-1">
            Given name
          </label>
          <input
            id="givenName"
            value={form.givenName}
            onChange={(e) => update("givenName", e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="familyName" className="block text-sm font-medium text-foreground mb-1">
            Family name
          </label>
          <input
            id="familyName"
            value={form.familyName}
            onChange={(e) => update("familyName", e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="dateOfBirth" className="block text-sm font-medium text-foreground mb-1">
            Date of birth
          </label>
          <input
            id="dateOfBirth"
            type="date"
            value={form.dateOfBirth}
            onChange={(e) => update("dateOfBirth", e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="sex" className="block text-sm font-medium text-foreground mb-1">
            Sex
          </label>
          <select
            id="sex"
            value={form.sex}
            onChange={(e) => update("sex", e.target.value)}
            className="w-full px-3 py-2 border border-border rounded-lg text-sm"
          >
            <option value="">Select</option>
            <option value="M">Male</option>
            <option value="F">Female</option>
            <option value="O">Other</option>
          </select>
        </div>
        {error && (
          <div className="bg-danger-soft border border-danger/28 rounded-lg p-3">
            <p className="text-sm text-danger">{error}</p>
          </div>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
        >
          {submitting ? "Submitting…" : "Submit request"}
        </button>
      </form>
    </div>
  );
}
