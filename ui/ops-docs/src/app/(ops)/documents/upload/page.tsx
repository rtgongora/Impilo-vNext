"use client";

import { useState, useRef } from "react";
import { Card, CardHeader, CardTitle } from "shared-ui";

export default function DocumentUploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [subjectType, setSubjectType] = useState("CLIENT");
  const [subjectId, setSubjectId] = useState("");
  const [documentTypeCode, setDocumentTypeCode] = useState("");
  const [confidentiality, setConfidentiality] = useState("NORMAL");
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!file) return;

    setUploading(true);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("subjectType", subjectType);
      formData.append("subjectId", subjectId);
      formData.append("documentTypeCode", documentTypeCode);
      formData.append("confidentiality", confidentiality);

      const res = await fetch("/api/v1/documents", {
        method: "POST",
        body: formData,
      });

      if (res.ok) {
        setResult({ success: true, message: "Document uploaded successfully" });
        setFile(null);
        setSubjectId("");
        setDocumentTypeCode("");
        if (fileRef.current) fileRef.current.value = "";
      } else {
        const err = await res.json().catch(() => null);
        setResult({ success: false, message: err?.error?.message ?? `Upload failed (${res.status})` });
      }
    } catch {
      setResult({ success: false, message: "Network error during upload" });
    } finally {
      setUploading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Upload Document</h1>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle>New Document</CardTitle>
        </CardHeader>
        <form onSubmit={handleUpload} className="p-4 space-y-4">
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">File</label>
            <input
              ref={fileRef}
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="w-full text-sm"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Subject Type</label>
              <select
                value={subjectType}
                onChange={(e) => setSubjectType(e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm"
              >
                <option value="CLIENT">Client</option>
                <option value="PROVIDER">Provider</option>
                <option value="FACILITY">Facility</option>
                <option value="ENCOUNTER">Encounter</option>
                <option value="REFERRAL">Referral</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Subject ID</label>
              <input
                type="text"
                value={subjectId}
                onChange={(e) => setSubjectId(e.target.value)}
                placeholder="UUID of subject"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm"
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Document Type Code</label>
              <input
                type="text"
                value={documentTypeCode}
                onChange={(e) => setDocumentTypeCode(e.target.value)}
                placeholder="e.g., LAB_RESULT, REFERRAL_LETTER"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-neutral-700 mb-1">Confidentiality</label>
              <select
                value={confidentiality}
                onChange={(e) => setConfidentiality(e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm"
              >
                <option value="NORMAL">Normal</option>
                <option value="RESTRICTED">Restricted</option>
                <option value="HIGHLY_RESTRICTED">Highly Restricted</option>
              </select>
            </div>
          </div>

          {result && (
            <div className={`p-3 rounded-lg text-sm ${
              result.success ? "bg-green-50 text-green-800" : "bg-red-50 text-red-800"
            }`}>
              {result.message}
            </div>
          )}

          <button
            type="submit"
            disabled={uploading || !file}
            className="px-4 py-2 bg-brand-primary text-white rounded-lg text-sm font-medium hover:bg-brand-primary/90 disabled:opacity-50"
          >
            {uploading ? "Uploading..." : "Upload Document"}
          </button>
        </form>
      </Card>
    </div>
  );
}
