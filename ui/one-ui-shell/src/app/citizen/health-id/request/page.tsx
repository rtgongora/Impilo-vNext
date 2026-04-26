"use client";

import { useState } from "react";
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

/** Migrated from ui/portal/(citizen)/request-id */
export default function CitizenRequestHealthIdPage() {
  const [form, setForm] = useState<FormData>(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
            setSubmitted(false);
            setForm(INITIAL_FORM);
          }}
          className="mt-4 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-gray-200"
        >
          Submit another
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto bg-white rounded-xl border border-gray-200 p-6">
      <h1 className="text-xl font-semibold text-gray-900 mb-1">Request Health ID</h1>
      <p className="text-sm text-gray-500 mb-6">New or replacement health identity document.</p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-gray-700 mb-1">
            Request type
          </label>
          <select
            id="type"
            value={form.type}
            onChange={(e) => update("type", e.target.value as RequestType)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="NEW">New ID</option>
            <option value="REPLACEMENT">Replacement</option>
          </select>
        </div>
        <div>
          <label htmlFor="givenName" className="block text-sm font-medium text-gray-700 mb-1">
            Given name
          </label>
          <input
            id="givenName"
            value={form.givenName}
            onChange={(e) => update("givenName", e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="familyName" className="block text-sm font-medium text-gray-700 mb-1">
            Family name
          </label>
          <input
            id="familyName"
            value={form.familyName}
            onChange={(e) => update("familyName", e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="dateOfBirth" className="block text-sm font-medium text-gray-700 mb-1">
            Date of birth
          </label>
          <input
            id="dateOfBirth"
            type="date"
            value={form.dateOfBirth}
            onChange={(e) => update("dateOfBirth", e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <div>
          <label htmlFor="sex" className="block text-sm font-medium text-gray-700 mb-1">
            Sex
          </label>
          <select
            id="sex"
            value={form.sex}
            onChange={(e) => update("sex", e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="">Select</option>
            <option value="M">Male</option>
            <option value="F">Female</option>
            <option value="O">Other</option>
          </select>
        </div>
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-3">
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-impilo-500 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-impilo-600 disabled:opacity-50"
        >
          {submitting ? "Submitting…" : "Submit request"}
        </button>
      </form>
    </div>
  );
}
