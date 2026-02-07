"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

export default function IssueCredentialPage() {
  const [form, setForm] = useState({
    subjectType: "PROVIDER",
    subjectId: "",
    subjectName: "",
    credentialType: "LICENSE",
    title: "",
    issuedBy: "",
    validFrom: "",
    validTo: "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setResult(null);
    try {
      const res = await fetch("/api/v1/credentials", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      if (res.ok) {
        const data = await res.json();
        setResult({ success: true, message: `Credential issued: ${data.data?.credentialId ?? "OK"}` });
      } else {
        const err = await res.json().catch(() => null);
        setResult({ success: false, message: err?.error?.message ?? `Failed (${res.status})` });
      }
    } catch {
      setResult({ success: false, message: "Network error" });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Issue Credential</h1>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>New Credential</CardTitle>
        </CardHeader>
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Subject Type</label>
              <select value={form.subjectType} onChange={(e) => updateField("subjectType", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
                <option value="PROVIDER">Provider</option>
                <option value="CLIENT">Client</option>
                <option value="FACILITY">Facility</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Subject ID</label>
              <input type="text" value={form.subjectId} onChange={(e) => updateField("subjectId", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">Subject Name</label>
            <input type="text" value={form.subjectName} onChange={(e) => updateField("subjectName", e.target.value)}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Credential Type</label>
              <select value={form.credentialType} onChange={(e) => updateField("credentialType", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
                <option value="LICENSE">License</option>
                <option value="CERTIFICATE">Certificate</option>
                <option value="REGISTRATION">Registration</option>
                <option value="BADGE">Badge</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Title</label>
              <input type="text" value={form.title} onChange={(e) => updateField("title", e.target.value)}
                placeholder="e.g., Medical License" className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">Issued By</label>
            <input type="text" value={form.issuedBy} onChange={(e) => updateField("issuedBy", e.target.value)}
              placeholder="Issuing authority" className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Valid From</label>
              <input type="date" value={form.validFrom} onChange={(e) => updateField("validFrom", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Valid To (optional)</label>
              <input type="date" value={form.validTo} onChange={(e) => updateField("validTo", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
            </div>
          </div>

          {result && (
            <div className={`p-3 rounded-lg text-sm ${result.success ? "bg-green-50 text-green-800" : "bg-red-50 text-red-800"}`}>
              {result.message}
            </div>
          )}

          <button type="submit" disabled={submitting}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
            {submitting ? "Issuing..." : "Issue Credential"}
          </button>
        </form>
      </Card>
    </div>
  );
}
