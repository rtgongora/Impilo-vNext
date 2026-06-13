"use client";

import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { GENERIC_RECORD_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useMemo, useState } from "react";
import { CheckCircle2, FileCheck, Loader2, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateMvumoConsentRequest,
  useCreateMvumoTemplate,
  useMvumoAdminTemplates,
  useUpdateMvumoTemplate,
} from "@/hooks/queries/useMvumoAdmin";
import {
  useCreateMvumoRemoteSession,
  useGrantMvumoRemoteSession,
  useMvumoRemoteSession,
  useVerifyMvumoRemoteSession,
} from "@/hooks/queries/useMvumoRemoteSessions";

function templateKeyOf(row: Record<string, unknown>): string {
  const key = row.templateKey ?? row.template_key ?? row.id;
  return typeof key === "string" ? key : "";
}

export default function MvumoRegistryPage() {
  const [templateForm, setTemplateForm] = useState({
    templateKey: "registry-assisted-consent",
    consentType: "DIGITAL",
    title: "Registry assisted consent",
    bodyMarkdown: "I consent to the requested registry action.",
  });
  const [selectedTemplateKey, setSelectedTemplateKey] = useState("");
  const [updateTitle, setUpdateTitle] = useState("");
  const [consentForm, setConsentForm] = useState({
    subjectPatientRef: "",
    templateKey: "registry-assisted-consent",
    workflowRef: "registry-assisted-intake",
    consentType: "DIGITAL",
    channel: "FACILITY_ASSISTED",
  });
  const [createdRequestId, setCreatedRequestId] = useState<string | null>(null);
  const [remoteSessionId, setRemoteSessionId] = useState("");
  const [remoteToken, setRemoteToken] = useState("");

  const templates = useMvumoAdminTemplates();
  const createTemplate = useCreateMvumoTemplate();
  const updateTemplate = useUpdateMvumoTemplate();
  const createConsentRequest = useCreateMvumoConsentRequest();
  const createRemoteSession = useCreateMvumoRemoteSession();
  const remoteSessionQ = useMvumoRemoteSession(remoteSessionId || undefined);
  const verifyRemoteSession = useVerifyMvumoRemoteSession();
  const grantRemoteSession = useGrantMvumoRemoteSession();

  const templateRows = useMemo(() => {
    const raw = templates.data?.data;
    if (Array.isArray(raw)) return raw.filter((row): row is Record<string, unknown> => typeof row === "object" && row !== null);
    if (raw && typeof raw === "object") return [raw as Record<string, unknown>];
    return [];
  }, [templates.data?.data]);

  return (
    <AppLayout>
      <PageShell
        title="Mvumo"
        subtitle="Adaptive digital consent — templates, requests, and remote-session orchestration"
        serviceSlug="mvumo"
      >
        <div className="prose prose-sm max-w-none text-foreground space-y-4">
          <p>
            <strong>Mvumo</strong> orchestrates consent requests and templates. FHIR evaluation remains in{" "}
            <strong>tshepo-consent-service</strong>. Platform policy acceptance lives at{" "}
            <Link href="/consent" className="text-primary hover:underline">
              /consent
            </Link>
            ; trust directive reads at{" "}
            <Link href="/admin/consent" className="text-primary hover:underline">
              /admin/consent
            </Link>
            .
          </p>
        </div>

        <div className="mt-6 space-y-6">
          <section className="rounded-2xl border border-pink-100 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Template governance</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Typed BFF: <code>/internal/v1/mvumo-admin/templates</code>
            </p>

            <div className="mt-4 grid gap-4 lg:grid-cols-2">
              <div className="rounded-xl border border-border p-4">
                <h3 className="text-sm font-semibold text-foreground">Create template</h3>
                <div className="mt-3 grid gap-3">
                  {[
                    ["templateKey", "Template key"],
                    ["consentType", "Consent type"],
                    ["title", "Title"],
                  ].map(([key, label]) => (
                    <label key={key} className="text-xs font-medium text-muted-foreground">
                      {label}
                      <input
                        value={templateForm[key as keyof typeof templateForm]}
                        onChange={(event) => setTemplateForm({ ...templateForm, [key]: event.target.value })}
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                  ))}
                  <label className="text-xs font-medium text-muted-foreground">
                    Body markdown
                    <textarea
                      value={templateForm.bodyMarkdown}
                      onChange={(event) => setTemplateForm({ ...templateForm, bodyMarkdown: event.target.value })}
                      className="mt-1 h-24 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    />
                  </label>
                </div>
                <button
                  type="button"
                  disabled={createTemplate.isPending || !templateForm.templateKey.trim()}
                  onClick={() => createTemplate.mutate(templateForm)}
                  className="mt-3 rounded-lg bg-pink-600 px-4 py-2 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50"
                >
                  {createTemplate.isPending ? "Creating..." : "Create template"}
                </button>
              </div>

              <div className="rounded-xl border border-border p-4">
                <h3 className="text-sm font-semibold text-foreground">Update template</h3>
                <label className="mt-3 block text-xs font-medium text-muted-foreground">
                  Select template
                  <select
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    value={selectedTemplateKey}
                    onChange={(e) => {
                      setSelectedTemplateKey(e.target.value);
                      const row = templateRows.find((t) => templateKeyOf(t) === e.target.value);
                      const title = row?.title;
                      setUpdateTitle(typeof title === "string" ? title : "");
                    }}
                  >
                    <option value="">Choose template</option>
                    {templateRows.map((row) => {
                      const key = templateKeyOf(row);
                      return (
                        <option key={key} value={key}>
                          {key}
                        </option>
                      );
                    })}
                  </select>
                </label>
                <label className="mt-3 block text-xs font-medium text-muted-foreground">
                  Revised title
                  <input
                    value={updateTitle}
                    onChange={(e) => setUpdateTitle(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </label>
                <button
                  type="button"
                  disabled={updateTemplate.isPending || !selectedTemplateKey || !updateTitle.trim()}
                  onClick={() =>
                    updateTemplate.mutate({
                      templateId: selectedTemplateKey,
                      body: { title: updateTitle.trim() },
                    })
                  }
                  className="mt-3 rounded-lg border border-pink-300 bg-pink-50 px-4 py-2 text-sm font-medium text-pink-800 hover:bg-pink-100 disabled:opacity-50"
                >
                  {updateTemplate.isPending ? "Saving..." : "Update template"}
                </button>
              </div>
            </div>

            <div className="mt-4 rounded-xl border border-border p-4">
              <h3 className="text-sm font-semibold text-foreground">Template inventory</h3>
              <JsonApiDataTable
                data={templateRows}
                columns={GENERIC_RECORD_COLUMNS}
                isLoading={templates.isPending}
                error={templates.error as Error | null}
                emptyTitle="No templates"
              />
            </div>
          </section>

          <section className="rounded-2xl border border-emerald-100 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Consent request initiation</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              POST <code>/internal/v1/mvumo-admin/consent-requests</code> — binds a subject to a governed template.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              {[
                ["subjectPatientRef", "Subject patient ref (CPID/Health ID)"],
                ["templateKey", "Template key"],
                ["workflowRef", "Workflow ref"],
                ["consentType", "Consent type"],
                ["channel", "Channel"],
              ].map(([key, label]) => (
                <label key={key} className="text-xs font-medium text-muted-foreground">
                  {label}
                  <input
                    value={consentForm[key as keyof typeof consentForm]}
                    onChange={(event) => setConsentForm({ ...consentForm, [key]: event.target.value })}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </label>
              ))}
            </div>
            <button
              type="button"
              disabled={
                createConsentRequest.isPending ||
                !consentForm.subjectPatientRef.trim() ||
                !consentForm.templateKey.trim()
              }
              onClick={() =>
                createConsentRequest.mutate(consentForm, {
                  onSuccess: (res) => {
                    const raw = res.data;
                    const id =
                      raw && typeof raw === "object" && "id" in raw && typeof raw.id === "string" ? raw.id : null;
                    setCreatedRequestId(id);
                  },
                })
              }
              className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              {createConsentRequest.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
              Create consent request
            </button>
            {createdRequestId ? (
              <p className="mt-3 text-sm text-primary-hover">
                Consent request created. Review Tshepo directives at{" "}
                <Link
                  href={`/admin/consent?subjectId=${encodeURIComponent(consentForm.subjectPatientRef)}`}
                  className="font-medium underline"
                >
                  /admin/consent
                </Link>
                .
              </p>
            ) : null}
          </section>

          <section className="rounded-2xl border border-violet-100 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Remote-session admin</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Governed MVUMO remote consent sessions — <code>/internal/v1/mvumo/remote-sessions/*</code>
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                disabled={createRemoteSession.isPending}
                onClick={() =>
                  createRemoteSession.mutate(
                    {
                      subjectPatientRef: consentForm.subjectPatientRef || "patient-demo",
                      workflowRef: "registry-remote-consent",
                      channel: "REMOTE_LINK",
                    },
                    {
                      onSuccess: (res) => {
                        const raw = res.data as Record<string, unknown> | undefined;
                        const id = raw?.id != null ? String(raw.id) : "";
                        if (id) setRemoteSessionId(id);
                      },
                    },
                  )
                }
                className="rounded-lg bg-violet-600 px-3 py-2 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
              >
                Create remote session
              </button>
              <input
                value={remoteSessionId}
                onChange={(e) => setRemoteSessionId(e.target.value)}
                placeholder="Session UUID"
                className="min-w-[240px] rounded-lg border border-border px-3 py-2 text-sm font-mono"
              />
            </div>
            {remoteSessionId ? (
              <p className="mt-2 text-xs text-muted-foreground">
                Status: {String((remoteSessionQ.data?.data as Record<string, unknown> | undefined)?.status ?? "—")}
              </p>
            ) : null}
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <input
                value={remoteToken}
                onChange={(e) => setRemoteToken(e.target.value)}
                placeholder="Verification token"
                className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm"
              />
              <button
                type="button"
                disabled={!remoteSessionId || verifyRemoteSession.isPending}
                onClick={() => verifyRemoteSession.mutate({ sessionId: remoteSessionId, token: remoteToken || undefined })}
                className="rounded-lg border border-border px-3 py-2 text-xs font-medium hover:bg-background disabled:opacity-50"
              >
                Verify
              </button>
              <button
                type="button"
                disabled={!remoteSessionId || grantRemoteSession.isPending}
                onClick={() => grantRemoteSession.mutate({ sessionId: remoteSessionId })}
                className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                Grant
              </button>
            </div>
          </section>

          <Link
            href="https://github.com/rtgongora/Impilo-vNext/blob/claude/staging-ux-orchestration-remediation-Yypyl/docs/architecture/mvumo-consent-architecture.md"
            className="inline-flex items-center gap-2 rounded-lg border border-primary/25 bg-primary-soft px-4 py-2 text-sm font-medium text-impilo-800 hover:bg-primary-soft"
          >
            <FileCheck className="h-4 w-4" /> Architecture doc
          </Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
