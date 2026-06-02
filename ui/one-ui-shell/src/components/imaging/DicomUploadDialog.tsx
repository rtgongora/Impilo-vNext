"use client";

import { useEffect, useRef, useState } from "react";
import { FileImage, Loader2, Upload, X } from "lucide-react";
import { isDicomCandidate, parseDicomFile, uploadDicomFiles, type ParsedDicomData } from "@/lib/dicom/dicomService";

interface DicomUploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  patientId?: string;
  onSuccess?: (lastInstanceId?: string) => void;
  /** Open first selected file in the in-browser DWV viewer (no PACS required). */
  onViewLocal?: (payload: { file: File; fileName: string }) => void;
}

function formatDicomValue(value: string | number | null | undefined): string {
  if (value == null || value === "") return "—";
  return String(value);
}

export function DicomUploadDialog({ open, onOpenChange, patientId, onSuccess, onViewLocal }: DicomUploadDialogProps) {
  const [files, setFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [metadata, setMetadata] = useState<ParsedDicomData | null>(null);
  const [metadataLoading, setMetadataLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    setFiles([]);
    setMetadata(null);
    setError(null);
  }, [open]);

  useEffect(() => {
    if (!files.length) {
      setMetadata(null);
      return;
    }
    setMetadataLoading(true);
    const first = files[0];
    first
      .arrayBuffer()
      .then((buffer) => {
        try {
          setMetadata(parseDicomFile(buffer));
        } catch {
          setMetadata(null);
        }
      })
      .catch(() => setMetadata(null))
      .finally(() => setMetadataLoading(false));
  }, [files]);

  if (!open) return null;

  function handleSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(e.target.files || []);
    const valid = selected.filter((f) => isDicomCandidate(f));
    setFiles((prev) => [...prev, ...valid]);
    e.target.value = "";
  }

  function handleViewLocal() {
    const first = files[0];
    if (!first) {
      setError("Select a DICOM file first.");
      return;
    }
    setError(null);
    onViewLocal?.({ file: first, fileName: first.name });
    onOpenChange(false);
    setFiles([]);
  }

  async function handleUpload() {
    if (files.length === 0) {
      setError("Select at least one DICOM file.");
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const result = await uploadDicomFiles(files);
      if (result.uploaded > 0) {
        const lastId = [...result.results].reverse().find((r) => r.orthancInstanceId)?.orthancInstanceId;
        onSuccess?.(lastId);
        onOpenChange(false);
        setFiles([]);
      } else {
        setError(result.results[0]?.error ?? "Upload failed");
      }
    } catch {
      setError("Upload failed. Check PACS / Orthanc is running.");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div
        role="dialog"
        aria-modal
        className="w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-2xl border border-gray-200 bg-white shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              <Upload className="h-5 w-5 text-impilo-600" />
              Upload DICOM
            </h2>
            <p className="text-xs text-gray-500 mt-1">
              Files are ingested via Experience BFF → Orthanc PACS
              {patientId ? ` (chart: ${patientId})` : ""}.
            </p>
          </div>
          <button type="button" onClick={() => onOpenChange(false)} className="rounded-lg p-2 hover:bg-gray-100" aria-label="Close">
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="space-y-4 p-5">
          <input
            ref={inputRef}
            type="file"
            accept=".dcm,.dicom,application/dicom,application/octet-stream"
            multiple
            className="hidden"
            onChange={handleSelect}
          />
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
            className="w-full rounded-xl border-2 border-dashed border-gray-300 py-8 text-sm font-medium text-gray-600 hover:border-impilo-400 hover:bg-impilo-50/50"
          >
            Choose .dcm / DICOM files
          </button>

          {files.length > 0 && (
            <ul className="max-h-28 overflow-y-auto rounded-lg border border-gray-200 divide-y text-sm">
              {files.map((f, i) => (
                <li key={`${f.name}-${i}`} className="flex items-center justify-between px-3 py-2">
                  <span className="flex items-center gap-2 truncate">
                    <FileImage className="h-4 w-4 shrink-0 text-gray-400" />
                    {f.name}
                  </span>
                  <button type="button" onClick={() => setFiles((p) => p.filter((_, j) => j !== i))} className="text-gray-400 hover:text-red-600">
                    <X className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {metadataLoading && (
            <p className="text-sm text-gray-500 flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" /> Reading metadata…
            </p>
          )}

          {metadata && !metadataLoading && (
            <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 text-xs space-y-2">
              <p className="font-semibold text-gray-800">First file metadata</p>
              <p>
                <span className="text-gray-500">Patient:</span> {formatDicomValue(metadata.patientName)} (
                {formatDicomValue(metadata.patientId)})
              </p>
              <p>
                <span className="text-gray-500">Study:</span> {formatDicomValue(metadata.studyDescription)} ·{" "}
                {formatDicomValue(metadata.studyDate)} · {formatDicomValue(metadata.modality)}
              </p>
            </div>
          )}

          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>

        <div className="flex flex-wrap justify-end gap-2 border-t border-gray-200 px-5 py-4">
          <button type="button" onClick={() => onOpenChange(false)} disabled={uploading} className="px-4 py-2 text-sm rounded-lg border border-gray-300">
            Cancel
          </button>
          {onViewLocal && (
            <button
              type="button"
              onClick={() => void handleViewLocal()}
              disabled={uploading || files.length === 0}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-impilo-300 bg-impilo-50 text-impilo-800 hover:bg-impilo-100 disabled:opacity-50"
            >
              <FileImage className="h-4 w-4" />
              View &amp; diagnose
            </button>
          )}
          <button
            type="button"
            onClick={() => void handleUpload()}
            disabled={uploading || files.length === 0}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-impilo-600 text-white hover:bg-impilo-700 disabled:opacity-50"
          >
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            Upload to PACS {files.length > 0 ? `(${files.length})` : ""}
          </button>
        </div>
      </div>
    </div>
  );
}
