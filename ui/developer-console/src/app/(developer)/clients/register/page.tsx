"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { registerClient } from "@/lib/developerApi";

export default function RegisterClientPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    clientName: "",
    description: "",
    contactEmail: "",
    sandboxEnabled: true,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const result = await registerClient(form);
      router.push(`/clients/${result.client_id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Register Client</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Register a new partner application or API consumer
        </p>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 max-w-2xl">
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1.5">
              Client Name <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              required
              value={form.clientName}
              onChange={(e) => setForm({ ...form, clientName: e.target.value })}
              className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
              placeholder="e.g. DHIS2 Adapter"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1.5">
              Description
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              rows={3}
              className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary resize-none"
              placeholder="Brief description of what this client does"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1.5">
              Contact Email <span className="text-danger">*</span>
            </label>
            <input
              type="email"
              required
              value={form.contactEmail}
              onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary"
              placeholder="dev@partner.org"
            />
          </div>

          <div className="flex items-center gap-3">
            <input
              id="sandbox"
              type="checkbox"
              checked={form.sandboxEnabled}
              onChange={(e) => setForm({ ...form, sandboxEnabled: e.target.checked })}
              className="w-4 h-4 rounded border-neutral-300 text-brand-primary focus:ring-brand-primary/20"
            />
            <label htmlFor="sandbox" className="text-sm text-neutral-700">
              Enable sandbox environment for testing
            </label>
          </div>

          {error && (
            <div className="bg-danger/10 border border-danger/20 rounded-lg p-3 text-sm text-danger">
              {error}
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={submitting}
              className="px-6 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? "Registering..." : "Register Client"}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="px-6 py-2 border border-neutral-200 rounded-lg text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
