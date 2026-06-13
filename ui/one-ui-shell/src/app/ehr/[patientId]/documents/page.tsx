"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  ClipboardList,
  File,
  FileText,
  FlaskConical,
  Image,
  Loader2,
  Paperclip,
  Plus,
  Save,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalDocuments,
  useDocumentOcrStatus,
  useRequestDocumentOcr,
  useUploadDocumentFile,
  type ClinicalDocumentResource,
} from "@/hooks/queries/useClinicalDocuments";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const DOC_TYPE_CONFIG: Record<string, { label: string; icon: typeof FileText; color: string }> = {
  CLINICAL_NOTE: { label: "Clinical Note", icon: FileText, color: "bg-primary-soft text-primary" },
  LAB_REPORT: { label: "Lab Report", icon: ClipboardList, color: "bg-green-100 text-green-700" },
  IMAGING: { label: "Imaging", icon: Image, color: "bg-purple-100 text-warning-foreground" },
  CONSENT_FORM: { label: "Consent Form", icon: File, color: "bg-yellow-100 text-yellow-700" },
  REFERRAL_LETTER: { label: "Referral Letter", icon: Paperclip, color: "bg-orange-100 text-orange-700" },
  DISCHARGE_SUMMARY: { label: "Discharge Summary", icon: FileText, color: "bg-teal-100 text-teal-700" },
  PRESCRIPTION: { label: "Prescription", icon: ClipboardList, color: "bg-pink-100 text-pink-700" },
  PROCEDURE_NOTE: { label: "Procedure Note", icon: FileText, color: "bg-indigo-100 text-primary-hover" },
  OTHER: { label: "Other", icon: File, color: "bg-neutral-100 text-muted-foreground" },
};

function DocumentOcrButton({ objectId }: { objectId: string }) {
  const requestOcr = useRequestDocumentOcr(objectId);
  const ocrStatus = useDocumentOcrStatus(objectId);
  const status = String((ocrStatus.data as { data?: { status?: string } } | undefined)?.data?.status ?? "").toUpperCase();

  return (
    <button
      type="button"
      disabled={requestOcr.isPending || status === "RUNNING" || status === "PENDING"}
      onClick={() => requestOcr.mutate()}
      className="rounded-md border border-border px-2 py-1 text-[11px] font-medium text-foreground hover:bg-background disabled:opacity-50"
    >
      {requestOcr.isPending || status === "RUNNING" || status === "PENDING"
        ? "Indexing…"
        : status === "COMPLETED"
          ? "Re-index OCR"
          : "Index / OCR"}
    </button>
  );
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

export default function DocumentsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );

  const { data: documentsData, isLoading } = useClinicalDocuments(patientId);
  const uploadDocument = useUploadDocumentFile();

  const documents: ClinicalDocumentResource[] = documentsData?.data ?? [];
  const notesAndSummaries = documents.filter(
    (document) =>
      document.attributes.documentType === "CLINICAL_NOTE" ||
      document.attributes.documentType === "DISCHARGE_SUMMARY"
  ).length;
  const diagnosticsAttachments = documents.filter(
    (document) =>
      document.attributes.documentType === "LAB_REPORT" ||
      document.attributes.documentType === "IMAGING"
  ).length;
  const recentUploads = documents.filter((document) => {
    const createdAt = new Date(document.attributes.createdAt).getTime();
    return createdAt >= Date.now() - 1000 * 60 * 60 * 24 * 30;
  }).length;

  const [showForm, setShowForm] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [form, setForm] = useState({
    title: "",
    document_type: "CLINICAL_NOTE",
    description: "",
  });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!selectedFile) return;
    uploadDocument.mutate(
      {
        patientId,
        file: selectedFile,
        documentType: form.document_type,
        title: form.title.trim() || selectedFile.name,
        description: form.description.trim() || null,
        uploaded_by: user?.displayName ?? user?.email ?? "",
        encounter_id: activeEncounter?.id ?? null,
      },
      {
        onSuccess: () => {
          setForm({ title: "", document_type: "CLINICAL_NOTE", description: "" });
          setSelectedFile(null);
          setShowForm(false);
        },
      }
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Documents">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-5">
            <ClinicalReviewHeader
              badge="Clinical documents"
              badgeIcon={Paperclip}
              title="Keep uploads tied to the current review so notes, results, and supporting files stay in one workflow"
              description="Documents now sit inside the same longitudinal review loop: upload from the active encounter, keep diagnostic attachments visible next to orders and results, and move straight back into notes or the summary without losing context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/orders`, label: "Orders", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/results`, label: "Results", icon: FlaskConical, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Notes and summaries",
                  value: String(notesAndSummaries),
                  detail: "Clinical narrative documents already available in the chart.",
                },
                {
                  label: "Diagnostic attachments",
                  value: String(diagnosticsAttachments),
                  detail: "Lab and imaging artifacts that support ordering and review decisions.",
                },
                {
                  label: "Recent uploads",
                  value: String(recentUploads),
                  detail: "Files added in the last 30 days and ready for follow-through.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Document continuity
              </p>
              <p className="mt-2 text-sm text-foreground">
                {activeEncounter
                  ? "New uploads from this workspace attach to the active encounter so the evidence trail stays with the current clinical episode."
                  : "There is no active encounter in scope, so uploads will still land on the patient record but may need follow-up linkage from notes or the next encounter."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use this surface for the attachment itself, then move directly back to notes, orders, or results through the linked workspaces above.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-impilo-400" />
                <h2 className="text-sm font-semibold text-foreground">Documents ({documents.length})</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((value) => !value)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
              >
                <Plus className="h-4 w-4" />
                Upload Document
              </button>
            </div>

            {showForm && (
              <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-border bg-card p-5">
                <h3 className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <Paperclip className="h-4 w-4 text-impilo-400" />
                  Upload Clinical Document
                </h3>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Title *</label>
                    <input
                      type="text"
                      required
                      value={form.title}
                      onChange={(e) => updateField("title", e.target.value)}
                      placeholder="e.g. Blood Test Results"
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Document Type</label>
                    <select
                      value={form.document_type}
                      onChange={(e) => updateField("document_type", e.target.value)}
                      className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                    >
                      {Object.entries(DOC_TYPE_CONFIG).map(([key, config]) => (
                        <option key={key} value={key}>{config.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Description</label>
                  <textarea
                    value={form.description}
                    onChange={(e) => updateField("description", e.target.value)}
                    rows={2}
                    placeholder="Brief description of the document content..."
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 resize-none"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">File *</label>
                  <input
                    type="file"
                    required
                    onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                  />
                  {selectedFile && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      {selectedFile.name} ({formatFileSize(selectedFile.size)})
                    </p>
                  )}
                </div>
                {activeEncounter && (
                  <p className="text-xs text-muted-foreground">
                    Linked to encounter: {activeEncounter.attributes.encounterType} (started {new Date(activeEncounter.attributes.startedAt).toLocaleString()})
                  </p>
                )}
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setShowForm(false)}
                    className="flex-1 rounded-lg bg-neutral-100 py-2 text-sm font-medium text-foreground transition-colors hover:bg-neutral-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={uploadDocument.isPending}
                    className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-primary py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-50"
                  >
                    {uploadDocument.isPending ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" /> Uploading...
                      </>
                    ) : (
                      <>
                        <Save className="h-4 w-4" /> Upload
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}

            {documents.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <FileText className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No documents uploaded yet</p>
              </div>
            ) : (
              <div className="space-y-3">
                {documents.map((document) => {
                  const attrs = document.attributes;
                  const typeConfig = DOC_TYPE_CONFIG[attrs.documentType] ?? DOC_TYPE_CONFIG.OTHER;
                  const Icon = typeConfig.icon;

                  return (
                    <div key={document.id} className="rounded-lg border border-border bg-card p-4 transition-colors hover:bg-background">
                      <div className="flex items-start gap-3">
                        <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${typeConfig.color}`}>
                          <Icon className="h-5 w-5" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="mb-1 flex items-center gap-2">
                            <span className="text-sm font-medium text-foreground">{attrs.title}</span>
                            <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${typeConfig.color}`}>
                              {typeConfig.label}
                            </span>
                          </div>
                          {attrs.description && (
                            <p className="mb-1 line-clamp-2 text-xs text-muted-foreground">{attrs.description}</p>
                          )}
                          <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                            <span>By: {attrs.uploadedBy}</span>
                            <span>{new Date(attrs.createdAt).toLocaleDateString()}</span>
                            {attrs.mimeType && <span className="font-mono">{attrs.mimeType}</span>}
                            {attrs.fileSize > 0 && <span>{formatFileSize(attrs.fileSize)}</span>}
                            <DocumentOcrButton objectId={document.id} />
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
