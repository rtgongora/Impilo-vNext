"use client";

/**
 * Documents — View and upload clinical documents.
 * Route: /ehr/[patientId]/documents | pageTitle: "Documents"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { FileText, Plus, Loader2} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClinicalDocuments,
  useUploadDocument,
  type ClinicalDocumentResource } from "@/hooks/queries/useClinicalDocuments";
import { useAuthStore } from "@/hooks/useAuthStore";

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const value = bytes / Math.pow(1024, i);
  return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

const DOC_TYPE_BADGE: Record<string, string> = {
  CLINICAL_NOTE: "bg-blue-100 text-blue-700",
  LAB_REPORT: "bg-green-100 text-green-700",
  IMAGING: "bg-purple-100 text-purple-700",
  CONSENT_FORM: "bg-yellow-100 text-yellow-700",
  REFERRAL_LETTER: "bg-orange-100 text-orange-700",
  OTHER: "bg-gray-100 text-gray-600" };

/* ------------------------------------------------------------------ */
/*  Empty form state                                                   */
/* ------------------------------------------------------------------ */

const EMPTY_FORM = {
  title: "",
  document_type: "CLINICAL_NOTE",
  description: "",
  uploaded_by: "",
  storage_key: "" };

/* ------------------------------------------------------------------ */
/*  Page component                                                     */
/* ------------------------------------------------------------------ */

export default function DocumentsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const { data: documentsData, isLoading } = useClinicalDocuments(patientId);
  const uploadDocument = useUploadDocument();

  const documents: ClinicalDocumentResource[] = documentsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM, uploaded_by: user?.displayName ?? user?.email ?? "" });

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
        uploaded_by: form.uploaded_by,
        storage_key: form.storage_key.trim() || undefined },
      {
        onSuccess: () => {
          setForm({ ...EMPTY_FORM });
          setShowForm(false);
        } },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Documents" subtitle="Clinical documents and uploads">

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading documents...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-500" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Documents ({documents.length})
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Upload Document
              </button>
            </div>

            {/* Upload Document form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">Upload Document</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Title
                      </label>
                      <input
                        type="text"
                        value={form.title}
                        onChange={(e) => updateField("title", e.target.value)}
                        placeholder="e.g. Blood Test Results"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Document Type
                      </label>
                      <select
                        value={form.document_type}
                        onChange={(e) => updateField("document_type", e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option value="CLINICAL_NOTE">Clinical Note</option>
                        <option value="LAB_REPORT">Lab Report</option>
                        <option value="IMAGING">Imaging</option>
                        <option value="CONSENT_FORM">Consent Form</option>
                        <option value="REFERRAL_LETTER">Referral Letter</option>
                        <option value="OTHER">Other</option>
                      </select>
                    </div>
                    <div className="md:col-span-2">
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Description
                      </label>
                      <textarea
                        value={form.description}
                        onChange={(e) => updateField("description", e.target.value)}
                        rows={2}
                        placeholder="Brief description of the document..."
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Uploaded By
                      </label>
                      <input
                        type="text"
                        value={form.uploaded_by}
                        onChange={(e) => updateField("uploaded_by", e.target.value)}
                        placeholder="Dr. Jane Smith"
                        required
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Storage Key (file reference)
                      </label>
                      <input
                        type="text"
                        value={form.storage_key}
                        onChange={(e) => updateField("storage_key", e.target.value)}
                        placeholder="e.g. documents/patient-123/file.pdf"
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={uploadDocument.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {uploadDocument.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Upload Document
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setForm({ ...EMPTY_FORM });
                        setShowForm(false);
                      }}
                      className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>

                  {uploadDocument.isError && (
                    <p className="text-sm text-red-600">
                      Failed to upload document. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Documents table */}
            {documents.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No documents uploaded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Title</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">MIME Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Size</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Uploaded By</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {documents.map((doc) => {
                        const a = doc.attributes;
                        return (
                          <tr
                            key={doc.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 font-medium text-gray-900">{a.title}</td>
                            <td className="px-4 py-3">
                              <span
                                className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${
                                  DOC_TYPE_BADGE[a.documentType] ?? "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {a.documentType.replace(/_/g, " ")}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-gray-700 font-mono text-xs">
                              {a.mimeType}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {formatFileSize(a.fileSize)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">{a.uploadedBy}</td>
                            <td className="px-4 py-3 text-gray-700">
                              {new Date(a.createdAt).toLocaleDateString()}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
