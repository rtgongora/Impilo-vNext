"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileCheck, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Mvumo — product surface for the national digital consent orchestration service.
 * APIs live on mvumo-service; trust-layer FHIR/evaluation remains on tshepo-consent-service.
 */
export default function MvumoRegistryPage() {
  const queryClient = useQueryClient();
  const [templateForm, setTemplateForm] = useState({
    templateKey: "registry-assisted-consent",
    consentType: "DIGITAL",
    title: "Registry assisted consent",
    bodyMarkdown: "I consent to the requested registry action.",
  });

  const templates = useQuery({
    queryKey: ["mvumo-admin", "templates"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/mvumo-admin/templates"),
  });

  const createTemplate = useMutation({
    mutationFn: () => apiClient.post<ApiResponse<unknown>>("/internal/v1/mvumo-admin/templates", templateForm),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["mvumo-admin", "templates"] }),
  });

  return (
    <AppLayout>
      <PageShell
        title="Mvumo"
        subtitle="Adaptive digital consent — not a checkbox, not PDF-only, not a single signature channel"
        icon={<Shield className="h-6 w-6 text-impilo-600" />}
      >
        <div className="prose prose-sm max-w-none text-gray-700 space-y-4">
          <p>
            <strong>Mvumo</strong> orchestrates consent <em>requests</em>, <em>templates</em>,{" "}
            <em>remote sessions</em>, and <em>adaptive assurance</em> across portals, tokens, OTP, PIN, USSD,
            assisted facility capture, offline sync, paper-to-digital proof, witnessed flows, and break-glass
            acknowledgement — as policy allows.
          </p>
          <p>
            Enforcement and FHIR <code className="rounded bg-gray-100 px-1">Consent</code> evaluation remain in{" "}
            <strong>tshepo-consent-service</strong>. See repository docs:{" "}
            <code className="rounded bg-gray-100 px-1">docs/architecture/mvumo-consent-architecture.md</code>.
          </p>
          <div className="flex flex-wrap gap-3 not-prose">
            <Link
              href="https://github.com/rtgongora/Impilo-vNext/blob/claude/staging-ux-orchestration-remediation-Yypyl/docs/architecture/mvumo-consent-architecture.md"
              className="inline-flex items-center gap-2 rounded-lg border border-impilo-200 bg-impilo-50 px-4 py-2 text-sm font-medium text-impilo-800 hover:bg-impilo-100"
            >
              <FileCheck className="h-4 w-4" /> Architecture doc
            </Link>
            <Link
              href="https://github.com/rtgongora/Impilo-vNext/blob/claude/staging-ux-orchestration-remediation-Yypyl/docs/audits/mvumo-consent-current-state-audit.md"
              className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Current-state audit
            </Link>
          </div>
        </div>

        <div className="mt-6 rounded-2xl border border-pink-100 bg-white p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-pink-600">Typed admin contract</p>
              <h2 className="mt-1 text-lg font-semibold text-gray-900">Mvumo template governance</h2>
              <p className="mt-1 max-w-3xl text-sm text-gray-600">
                Uses <code>/internal/v1/mvumo-admin/templates</code>, a typed Experience BFF contract over the sovereign
                Mvumo service. The broad <code>/internal/v1/mvumo/**</code> proxy remains available for lower-level
                pass-throughs, but this registry page no longer stays documentation-only.
              </p>
            </div>
            <span className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
              live typed BFF
            </span>
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <div className="rounded-xl border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-900">Create template</h3>
              <div className="mt-3 grid gap-3">
                {[
                  ["templateKey", "Template key"],
                  ["consentType", "Consent type"],
                  ["title", "Title"],
                ].map(([key, label]) => (
                  <label key={key} className="text-xs font-medium text-gray-600">
                    {label}
                    <input
                      value={templateForm[key as keyof typeof templateForm]}
                      onChange={(event) => setTemplateForm({ ...templateForm, [key]: event.target.value })}
                      className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                    />
                  </label>
                ))}
                <label className="text-xs font-medium text-gray-600">
                  Body markdown
                  <textarea
                    value={templateForm.bodyMarkdown}
                    onChange={(event) => setTemplateForm({ ...templateForm, bodyMarkdown: event.target.value })}
                    className="mt-1 h-24 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  />
                </label>
              </div>
              <button
                type="button"
                disabled={createTemplate.isPending || !templateForm.templateKey.trim() || !templateForm.title.trim()}
                onClick={() => createTemplate.mutate()}
                className="mt-3 rounded-lg bg-pink-600 px-4 py-2 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50"
              >
                {createTemplate.isPending ? "Creating..." : "Create template"}
              </button>
              {createTemplate.isError ? (
                <p className="mt-2 text-xs text-red-600">Template creation failed. Verify Mvumo service availability.</p>
              ) : null}
            </div>

            <div className="rounded-xl border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-900">Template inventory</h3>
              {templates.isLoading ? <p className="mt-3 text-sm text-gray-500">Loading templates...</p> : null}
              {templates.isError ? (
                <p className="mt-3 text-sm text-red-600">Mvumo templates unavailable.</p>
              ) : (
                <pre className="mt-3 max-h-80 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-slate-100">
                  {JSON.stringify(templates.data?.data ?? [], null, 2)}
                </pre>
              )}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
