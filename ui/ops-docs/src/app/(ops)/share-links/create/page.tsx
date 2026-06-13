"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

export default function CreateShareLinkPage() {
  const [form, setForm] = useState({
    subjectType: "CLIENT",
    subjectId: "",
    subjectName: "",
    documentIds: "",
    purpose: "",
    verificationMethod: "OTP",
    expiryHours: 24,
    maxClaims: 1,
  });
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  function updateField(field: string, value: string | number) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setResult(null);
    try {
      const body = {
        ...form,
        documentIds: form.documentIds.split(",").map((id) => id.trim()).filter(Boolean),
      };
      const res = await fetch("/api/v1/share-links", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (res.ok) {
        const data = await res.json();
        setResult({ success: true, message: `Share link created: ${data.data?.linkId ?? "OK"}` });
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
      <h1 className="text-2xl font-bold mb-6">Create Share Link</h1>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>Share Link Details</CardTitle>
        </CardHeader>
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Subject Type</label>
              <select value={form.subjectType} onChange={(e) => updateField("subjectType", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
                <option value="CLIENT">Client</option>
                <option value="PROVIDER">Provider</option>
                <option value="ENCOUNTER">Encounter</option>
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
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">Document IDs (comma-separated UUIDs)</label>
            <input type="text" value={form.documentIds} onChange={(e) => updateField("documentIds", e.target.value)}
              placeholder="uuid-1, uuid-2, ..." className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">Purpose</label>
            <input type="text" value={form.purpose} onChange={(e) => updateField("purpose", e.target.value)}
              placeholder="e.g., Referral documents for specialist" className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Verification</label>
              <select value={form.verificationMethod} onChange={(e) => updateField("verificationMethod", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
                <option value="OTP">OTP</option>
                <option value="QR_SCAN">QR Scan</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Expiry (hours)</label>
              <input type="number" min={1} max={168} value={form.expiryHours}
                onChange={(e) => updateField("expiryHours", parseInt(e.target.value))}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Max Claims</label>
              <input type="number" min={1} max={10} value={form.maxClaims}
                onChange={(e) => updateField("maxClaims", parseInt(e.target.value))}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
            </div>
          </div>

          {result && (
            <div className={`p-3 rounded-lg text-sm ${result.success ? "bg-green-50 text-green-800" : "bg-danger-soft text-red-800"}`}>
              {result.message}
            </div>
          )}

          <button type="submit" disabled={submitting}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
            {submitting ? "Creating..." : "Create Share Link"}
          </button>
        </form>
      </Card>
    </div>
  );
}
