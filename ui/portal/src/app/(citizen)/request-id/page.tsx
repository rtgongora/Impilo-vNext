"use client";

import { useState } from "react";
import { portalApi } from "@/lib/portalApi";

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

export default function RequestIdPage() {
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
      await portalApi.requestId({
        type: form.type,
        givenName: form.givenName || undefined,
        familyName: form.familyName || undefined,
        dateOfBirth: form.dateOfBirth || undefined,
        sex: form.sex || undefined,
      });
      setSubmitted(true);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "An unexpected error occurred",
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (submitted) {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center">
          <h2 className="text-lg font-semibold text-green-800 mb-2">
            Request Submitted
          </h2>
          <p className="text-sm text-green-700">
            Your request has been received and is being processed. You will be
            notified once your health identity document is ready for collection.
          </p>
          <button
            type="button"
            onClick={() => {
              setSubmitted(false);
              setForm(INITIAL_FORM);
            }}
            className="mt-4 bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
          >
            Submit Another Request
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto">
      <div className="bg-card rounded-xl shadow-sm border border-neutral-200 p-6">
        <h1 className="text-xl font-semibold text-neutral-900 mb-1">
          Request Health ID
        </h1>
        <p className="text-sm text-neutral-500 mb-6">
          Apply for a new health identity document or request a replacement.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Request type */}
          <div>
            <label
              htmlFor="type"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Request Type
            </label>
            <select
              id="type"
              value={form.type}
              onChange={(e) => update("type", e.target.value as RequestType)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm bg-card focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="NEW">New ID</option>
              <option value="REPLACEMENT">Replacement</option>
            </select>
          </div>

          {/* Given name */}
          <div>
            <label
              htmlFor="givenName"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Given Name
            </label>
            <input
              id="givenName"
              type="text"
              value={form.givenName}
              onChange={(e) => update("givenName", e.target.value)}
              placeholder="Enter your given name"
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          {/* Family name */}
          <div>
            <label
              htmlFor="familyName"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Family Name
            </label>
            <input
              id="familyName"
              type="text"
              value={form.familyName}
              onChange={(e) => update("familyName", e.target.value)}
              placeholder="Enter your family name"
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          {/* Date of birth */}
          <div>
            <label
              htmlFor="dateOfBirth"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Date of Birth
            </label>
            <input
              id="dateOfBirth"
              type="date"
              value={form.dateOfBirth}
              onChange={(e) => update("dateOfBirth", e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          {/* Sex */}
          <div>
            <label
              htmlFor="sex"
              className="block text-sm font-medium text-neutral-700 mb-1"
            >
              Sex
            </label>
            <select
              id="sex"
              value={form.sex}
              onChange={(e) => update("sex", e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm bg-card focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">Select</option>
              <option value="M">Male</option>
              <option value="F">Female</option>
              <option value="O">Other</option>
            </select>
          </div>

          {/* Error message */}
          {error && (
            <div className="bg-danger-soft border border-danger/28 rounded-lg p-3">
              <p className="text-sm text-danger">{error}</p>
            </div>
          )}

          {/* Submit */}
          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? "Submitting..." : "Submit Request"}
          </button>
        </form>
      </div>
    </div>
  );
}
