"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

export default function CreatePrintJobPage() {
  const [form, setForm] = useState({
    jobType: "PROVIDER_CARD",
    subjectType: "PROVIDER",
    subjectId: "",
    subjectName: "",
    templateName: "provider-card",
    priority: 5,
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
      const res = await fetch("/api/v1/print-jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, payload: {} }),
      });
      if (res.ok) {
        const data = await res.json();
        setResult({ success: true, message: `Print job created: ${data.data?.jobId ?? "OK"}` });
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
      <h1 className="text-2xl font-bold mb-6">New Print Job</h1>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>Print Job Details</CardTitle>
        </CardHeader>
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Job Type</label>
              <select value={form.jobType} onChange={(e) => updateField("jobType", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm">
                <option value="PROVIDER_CARD">Provider Card</option>
                <option value="CLIENT_CARD">Client Card</option>
                <option value="FACILITY_BADGE">Facility Badge</option>
                <option value="SHARE_SLIP">Share Slip</option>
                <option value="EMERGENCY_CAPSULE">Emergency Capsule</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Template</label>
              <input type="text" value={form.templateName} onChange={(e) => updateField("templateName", e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" required />
            </div>
          </div>

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

          <div className="w-32">
            <label className="block text-sm font-medium text-neutral-700 mb-1">Priority (1-10)</label>
            <input type="number" min={1} max={10} value={form.priority}
              onChange={(e) => updateField("priority", parseInt(e.target.value))}
              className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm" />
          </div>

          {result && (
            <div className={`p-3 rounded-lg text-sm ${result.success ? "bg-green-50 text-green-800" : "bg-red-50 text-red-800"}`}>
              {result.message}
            </div>
          )}

          <button type="submit" disabled={submitting}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50">
            {submitting ? "Creating..." : "Create Print Job"}
          </button>
        </form>
      </Card>
    </div>
  );
}
