"use client";

/**
 * Documents — Clinical documents and attachments.
 * Route: /ehr/[patientId]/documents | pageTitle: "Documents"
 *
 * Lovable-aligned: card layout, encounter linkage, richer document types,
 * description display, user attribution from auth store.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  FileText,
  Plus,
  Loader2,
  File,
  Image,
  ClipboardList,
  Save,
  Paperclip,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalDocuments,
  useUploadDocument,
  type ClinicalDocumentResource,
} from "@/hooks/queries/useClinicalDocuments";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const DOC_TYPE_CONFIG: Record<string, { label: string; icon: typeof FileText; color: string }> = {
  CLINICAL_NOTE: { label: "Clinical Note", icon: FileText, color: "bg-blue-100 text-blue-700" },
  LAB_REPORT: { label: "Lab Report", icon: ClipboardList, color: "bg-green-100 text-green-700" },
  IMAGING: { label: "Imaging", icon: Image, color: "bg-purple-100 text-purple-700" },
  CONSENT_FORM: { label: "Consent Form", icon: File, color: "bg-yellow-100 text-yellow-700" },
  REFERRAL_LETTER: { label: "Referral Letter", icon: Paperclip, color: "bg-orange-100 text-orange-700" },
  DISCHARGE_SUMMARY: { label: "Discharge Summary", icon: FileText, color: "bg-teal-100 text-teal-700" },
  PRESCRIPTION: { label: "Prescription", icon: ClipboardList, color: "bg-pink-100 text-pink-700" },
  PROCEDURE_NOTE: { label: "Procedure Note", icon: FileText, color: "bg-indigo-100 text-indigo-700" },
  OTHER: { label: "Other", icon: File, color: "bg-gray-100 text-gray-600" },
};

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export default function DocumentsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );

  const { data: documentsData, isLoading } = useClinicalDocuments(patientId);
  const uploadDocument = useUploadDocument();

  const documents: ClinicalDocumentResource[] = documentsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    title: "",
    document_type: "CLINICAL_NOTE",
    description: "",
    storage_key: "",
  });

  function updateField(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    uploadDocument.mutate(
      {
        patientId,
        documentType: form.document_type,
        title: form.title,
        description: form.description.trim() || null,
        uploaded_by: user?.displayName ?? user?.email ?? "",
        encounter_id: activeEncounter?.id ?? null,
        facility_id: facility?.id ?? null,
        storage_key: form.storage_key.trim() || undefined,
      },
      {
        onSuccess: () => {
          setForm({ title: "", document_type: "CLINICAL_NOTE", description: "", storage_key: "" });
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
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="space-y-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-500" />
                <h2 className="text-sm font-semibold text-gray-900">
                  Documents ({documents.length})
                </h2>
              </div>
              <button
                onClick={() => setShowForm((v) => !v)}
                className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Upload Document
              </button>
            </div>

            {showForm && (
              <form onSubmit={handleSubmit} className="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
                <h3 className="text-sm font-medium text-gray-900 flex items-center gap-2">
                  <Paperclip className="w-4 h-4 text-blue-500" />
                  Upload Clinical Document
                </h3>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Title *</label>
                    <input type="text" required value={form.title} onChange={(e) => updateField("title", e.target.value)}
                      placeholder="e.g. Blood Test Results"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Document Type</label>
                    <select value={form.document_type} onChange={(e) => updateField("document_type", e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      {Object.entries(DOC_TYPE_CONFIG).map(([key, cfg]) => (
                        <option key={key} value={key}>{cfg.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Description</label>
                  <textarea value={form.description} onChange={(e) => updateField("description", e.target.value)}
                    rows={2} placeholder="Brief description of the document content..."
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">File Reference</label>
                  <input type="text" value={form.storage_key} onChange={(e) => updateField("storage_key", e.target.value)}
                    placeholder="e.g. documents/patient-123/file.pdf (or paste URL)"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                {activeEncounter && (
                  <p className="text-xs text-gray-500">
                    Linked to encounter: {activeEncounter.attributes.encounterType} (started {new Date(activeEncounter.attributes.startedAt).toLocaleString()})
                  </p>
                )}
                <div className="flex gap-3">
                  <button type="button" onClick={() => setShowForm(false)}
                    className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">
                    Cancel
                  </button>
                  <button type="submit" disabled={uploadDocument.isPending}
                    className="flex-1 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                    {uploadDocument.isPending ? <><Loader2 className="w-4 h-4 animate-spin" /> Uploading...</> : <><Save className="w-4 h-4" /> Upload</>}
                  </button>
                </div>
              </form>
            )}

            {documents.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No documents uploaded yet</p>
              </div>
            ) : (
              <div className="space-y-3">
                {documents.map((doc) => {
                  const a = doc.attributes;
                  const typeConfig = DOC_TYPE_CONFIG[a.documentType] ?? DOC_TYPE_CONFIG.OTHER;
                  const Icon = typeConfig.icon;

                  return (
                    <div key={doc.id} className="bg-white rounded-lg border border-gray-200 p-4 hover:bg-gray-50 transition-colors">
                      <div className="flex items-start gap-3">
                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${typeConfig.color}`}>
                          <Icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-gray-900">{a.title}</span>
                            <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${typeConfig.color}`}>
                              {typeConfig.label}
                            </span>
                          </div>
                          {a.description && (
                            <p className="text-xs text-gray-500 mb-1 line-clamp-2">{a.description}</p>
                          )}
                          <div className="flex items-center gap-3 text-xs text-gray-400">
                            <span>By: {a.uploadedBy}</span>
                            <span>{new Date(a.createdAt).toLocaleDateString()}</span>
                            {a.mimeType && <span className="font-mono">{a.mimeType}</span>}
                            {a.fileSize > 0 && <span>{formatFileSize(a.fileSize)}</span>}
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
