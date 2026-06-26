"use client";

/**
 * Safety case detail + MCAZ actions.
 * Route: /work/patient-safety/cases/[caseId]
 *
 * Shows the case timeline, follow-ups, manual VigiFlow records and the E2B(R3)-aligned export
 * posture (honest — never "submitted"), plus the regulator actions: triage, request follow-up,
 * record a manual VigiFlow reference, mark export-ready, and open a serious-AEFI investigation.
 */

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface TimelineEntry {
  id: string;
  actionType: string;
  actorId: string | null;
  fromStatus: string | null;
  toStatus: string | null;
  note: string | null;
  createdAt: string;
}
interface FollowUp {
  id: string;
  requestText: string;
  channelHint: string;
  conversationRef: string | null;
  status: string;
  responses: { id: string; responseText: string; responderKind: string | null; createdAt: string }[];
}
interface VigiFlow {
  id: string;
  entryMode: string;
  vigiflowReferenceId: string;
  enteredBy: string | null;
}
interface ExportView {
  status: string;
  packageFormat: string;
  adapterEnabled: boolean;
  message: string | null;
}
interface Investigation {
  investigationReference: string;
  status: string;
  assignedTo: string | null;
  finalClassification: string | null;
}
interface CaseDetail {
  id: string;
  caseReference: string;
  status: string;
  priority: string;
  reportType: string;
  serious: boolean;
  causalityAssessment: string;
  mcazStatus: string | null;
  report: { referenceCode: string | null; primaryProductName: string | null; subjectInitials: string | null } | null;
  timeline: TimelineEntry[];
  followUps: FollowUp[];
  vigiflowSubmissions: VigiFlow[];
  latestExport: ExportView | null;
  investigation: Investigation | null;
}

export default function SafetyCaseDetailPage() {
  const params = useParams();
  const caseId = params.caseId as string;
  const queryClient = useQueryClient();
  const base = `/internal/v1/patient-safety/cases/${caseId}`;

  const { data, isLoading } = useQuery<{ data: CaseDetail }>({
    queryKey: ["patient-safety-case", caseId],
    queryFn: () => apiClient.get(base),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["patient-safety-case", caseId] });

  const [followUpText, setFollowUpText] = useState("");
  const [conversationRef, setConversationRef] = useState("");
  const [vigiflowRef, setVigiflowRef] = useState("");
  const [reviewer, setReviewer] = useState("");

  const triage = useMutation({
    mutationFn: () => apiClient.post(`${base}/triage`, { priority: "HIGH", assignedReviewerId: reviewer || null, note: "Triaged" }),
    onSuccess: invalidate,
  });
  const requestFollowUp = useMutation({
    mutationFn: () =>
      apiClient.post(`${base}/follow-up`, {
        requestText: followUpText,
        channelHint: "COMMS_HUB",
        conversationRef: conversationRef || null,
      }),
    onSuccess: () => {
      setFollowUpText("");
      setConversationRef("");
      invalidate();
    },
  });
  const recordVigiflow = useMutation({
    mutationFn: () => apiClient.post(`${base}/vigiflow-manual-entry`, { vigiflowReferenceId: vigiflowRef }),
    onSuccess: () => {
      setVigiflowRef("");
      invalidate();
    },
  });
  const markExportReady = useMutation({
    mutationFn: () => apiClient.post(`${base}/mark-export-ready`),
    onSuccess: invalidate,
  });
  const openInvestigation = useMutation({
    mutationFn: () => apiClient.post(`${base}/investigation`, { assignedTo: reviewer || "district-pho" }),
    onSuccess: invalidate,
  });

  const c = data?.data;

  return (
    <AppLayout>
      <PageShell title={c ? `Case ${c.caseReference}` : "Safety case"} subtitle="MCAZ review & disposition">
        <Link
          href="/work/patient-safety/mcaz"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Workbench
        </Link>

        {isLoading || !c ? (
          <div className="flex items-center gap-2 py-12 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading case…
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              {/* Summary */}
              <div className="rounded-lg border border-border p-4">
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <Badge>{c.reportType}</Badge>
                  <Badge tone={c.priority === "URGENT" ? "red" : c.priority === "HIGH" ? "amber" : "neutral"}>
                    {c.priority}
                  </Badge>
                  {c.serious && <Badge tone="red">Serious</Badge>}
                  <Badge tone="blue">{c.status.replace(/_/g, " ")}</Badge>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                  {c.report?.referenceCode ?? "—"} · {c.report?.primaryProductName ?? "—"}
                  {c.report?.subjectInitials ? ` · ${c.report.subjectInitials}` : ""}
                </p>
                <p className="mt-1 text-sm">Causality: {c.causalityAssessment.replace(/_/g, " ")}</p>
                {c.mcazStatus && <p className="mt-1 text-sm text-muted-foreground">{c.mcazStatus}</p>}
              </div>

              {/* Export posture — honest */}
              {c.latestExport && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                  <p className="font-semibold">
                    Export: {c.latestExport.packageFormat} · {c.latestExport.status} ·{" "}
                    {c.latestExport.adapterEnabled ? "adapter connected" : "adapter not configured"}
                  </p>
                  {c.latestExport.message && <p className="mt-1">{c.latestExport.message}</p>}
                </div>
              )}

              {/* VigiFlow records */}
              {c.vigiflowSubmissions.length > 0 && (
                <div className="rounded-lg border border-border p-4">
                  <h3 className="mb-2 text-sm font-semibold">VigiFlow (manual entry)</h3>
                  {c.vigiflowSubmissions.map((v) => (
                    <p key={v.id} className="text-sm text-muted-foreground">
                      {v.vigiflowReferenceId} · {v.entryMode} {v.enteredBy ? `· ${v.enteredBy}` : ""}
                    </p>
                  ))}
                </div>
              )}

              {/* Follow-ups */}
              {c.followUps.length > 0 && (
                <div className="rounded-lg border border-border p-4">
                  <h3 className="mb-2 text-sm font-semibold">Follow-up requests</h3>
                  {c.followUps.map((f) => (
                    <div key={f.id} className="mb-2 border-b border-border pb-2 last:border-0">
                      <p className="text-sm">
                        {f.requestText} <span className="text-xs text-muted-foreground">· {f.status}</span>
                      </p>
                      {f.responses.map((r) => (
                        <p key={r.id} className="ml-3 mt-1 text-sm text-muted-foreground">
                          ↳ {r.responseText}
                        </p>
                      ))}
                    </div>
                  ))}
                </div>
              )}

              {/* Investigation */}
              {c.investigation && (
                <div className="rounded-lg border border-border p-4">
                  <h3 className="mb-2 text-sm font-semibold">Serious-AEFI investigation</h3>
                  <p className="text-sm">
                    {c.investigation.investigationReference} · {c.investigation.status}
                    {c.investigation.finalClassification ? ` · ${c.investigation.finalClassification}` : ""}
                  </p>
                </div>
              )}

              {/* Timeline */}
              <div className="rounded-lg border border-border p-4">
                <h3 className="mb-2 text-sm font-semibold">Case timeline</h3>
                <ol className="space-y-2">
                  {c.timeline.map((t) => (
                    <li key={t.id} className="text-sm">
                      <span className="font-medium">{t.actionType.replace(/_/g, " ")}</span>
                      {t.toStatus ? ` → ${t.toStatus.replace(/_/g, " ")}` : ""}
                      {t.note ? <span className="text-muted-foreground"> · {t.note}</span> : ""}
                    </li>
                  ))}
                </ol>
              </div>
            </div>

            {/* Actions */}
            <aside className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground">Actions</h3>

              <ActionBox label="Triage">
                <input
                  className="w-full rounded border border-border px-2 py-1.5 text-sm"
                  placeholder="Reviewer id"
                  value={reviewer}
                  onChange={(e) => setReviewer(e.target.value)}
                />
                <ActionButton onClick={() => triage.mutate()} pending={triage.isPending} label="Triage (High)" />
              </ActionBox>

              <ActionBox label="Request follow-up (Comms Hub)">
                <textarea
                  className="w-full rounded border border-border px-2 py-1.5 text-sm"
                  rows={2}
                  placeholder="What information is needed?"
                  value={followUpText}
                  onChange={(e) => setFollowUpText(e.target.value)}
                />
                <input
                  className="w-full rounded border border-border px-2 py-1.5 text-sm"
                  placeholder="Conversation ref (optional)"
                  value={conversationRef}
                  onChange={(e) => setConversationRef(e.target.value)}
                />
                <ActionButton
                  onClick={() => requestFollowUp.mutate()}
                  pending={requestFollowUp.isPending}
                  disabled={!followUpText.trim()}
                  label="Send request"
                />
              </ActionBox>

              <ActionBox label="Record VigiFlow reference (manual)">
                <input
                  className="w-full rounded border border-border px-2 py-1.5 text-sm"
                  placeholder="e.g. ZW-2026-0001"
                  value={vigiflowRef}
                  onChange={(e) => setVigiflowRef(e.target.value)}
                />
                <ActionButton
                  onClick={() => recordVigiflow.mutate()}
                  pending={recordVigiflow.isPending}
                  disabled={!vigiflowRef.trim()}
                  label="Save VigiFlow id"
                />
              </ActionBox>

              <ActionBox label="Export">
                <ActionButton
                  onClick={() => markExportReady.mutate()}
                  pending={markExportReady.isPending}
                  label="Mark export-ready"
                />
              </ActionBox>

              {c.reportType === "AEFI" && !c.investigation && (
                <ActionBox label="Investigation">
                  <ActionButton
                    onClick={() => openInvestigation.mutate()}
                    pending={openInvestigation.isPending}
                    label="Open AEFI investigation"
                  />
                </ActionBox>
              )}
            </aside>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Badge({ children, tone = "neutral" }: { children: React.ReactNode; tone?: "neutral" | "red" | "amber" | "blue" }) {
  const tones: Record<string, string> = {
    neutral: "bg-neutral-100 text-neutral-700",
    red: "bg-red-100 text-red-800",
    amber: "bg-amber-100 text-amber-800",
    blue: "bg-blue-100 text-blue-800",
  };
  return <span className={`rounded px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>;
}

function ActionBox({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2 rounded-lg border border-border p-3">
      <p className="text-xs font-semibold text-muted-foreground">{label}</p>
      {children}
    </div>
  );
}

function ActionButton({
  onClick,
  pending,
  disabled,
  label,
}: {
  onClick: () => void;
  pending: boolean;
  disabled?: boolean;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      disabled={pending || disabled}
      className="inline-flex w-full items-center justify-center gap-1.5 rounded bg-primary px-3 py-1.5 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
    >
      {pending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
      {label}
    </button>
  );
}
