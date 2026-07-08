"use client";

import { useState } from "react";
import { AlertCircle, CheckCircle2, Loader2, Upload, X } from "lucide-react";
import { apiClient } from "@/lib/api-client";

export interface FileUploadFieldProps {
  name: string;
  label: string;
  accept?: string;
  maxSizeMB?: number;
  help?: string;
  required?: boolean;
  onFileUploaded?: (storageRef: string, fileName: string) => void;
  defaultStorageRef?: string;
  readOnly?: boolean;
}

export function FileUploadField({
  name,
  label,
  accept = "*/*",
  maxSizeMB = 100,
  help,
  required = false,
  onFileUploaded,
  defaultStorageRef,
  readOnly = false,
}: FileUploadFieldProps) {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploaded, setUploaded] = useState(!!defaultStorageRef);
  const [error, setError] = useState<string | null>(null);
  const [storageRef, setStorageRef] = useState(defaultStorageRef || "");

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (!selectedFile) return;

    setError(null);

    // Validate file size
    if (selectedFile.size > maxSizeMB * 1024 * 1024) {
      setError(`File size exceeds ${maxSizeMB}MB limit`);
      return;
    }

    setFile(selectedFile);
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      // Use document-service endpoint for file upload via postForm
      // This integrates with the existing MinIO infrastructure
      const response = await apiClient.postForm<{ storageRef?: string; fileRef?: string }>(
        "/internal/v1/documents/upload",
        formData
      );

      const ref = response.storageRef || response.fileRef;
      if (!ref) {
        throw new Error("No storage reference returned from upload");
      }

      setStorageRef(ref);
      setUploaded(true);
      setFile(null);

      if (onFileUploaded) {
        onFileUploaded(ref, file.name);
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Upload failed";
      setError(errorMessage);
      console.error("File upload error:", err);
    } finally {
      setUploading(false);
    }
  };

  const handleRemove = () => {
    setStorageRef("");
    setUploaded(false);
    setFile(null);
    setError(null);
  };

  if (readOnly && uploaded) {
    return (
      <label>
        <span className="text-xs font-medium text-slate-600">{label}</span>
        <div className="mt-1 flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
          <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
          <span className="text-sm text-slate-700 truncate">{storageRef}</span>
        </div>
      </label>
    );
  }

  return (
    <label>
      <span className="text-xs font-medium text-slate-600">
        {label} {required && <span className="text-red-600">*</span>}
      </span>

      {!uploaded ? (
        <div className="mt-1 space-y-2">
          <div className="rounded-md border-2 border-dashed border-slate-300 bg-slate-50 p-4 text-center hover:border-teal-400 hover:bg-teal-50/30 transition">
            <div className="flex flex-col items-center gap-2">
              <Upload className="h-5 w-5 text-slate-400" />
              <p className="text-xs font-medium text-slate-600">
                {file ? file.name : "Click or drag file here"}
              </p>
              {!file && <p className="text-[10px] text-slate-500">Max {maxSizeMB}MB</p>}
            </div>
            <input
              type="file"
              name={`${name}-input`}
              accept={accept}
              onChange={handleFileSelect}
              disabled={uploading}
              className="hidden"
            />
          </div>

          {file && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm text-slate-700 truncate">{file.name}</span>
              {!uploading && (
                <button
                  type="button"
                  onClick={() => {
                    setFile(null);
                    setError(null);
                  }}
                  className="p-1 rounded hover:bg-slate-100"
                  title="Remove"
                >
                  <X className="h-4 w-4 text-slate-400" />
                </button>
              )}
            </div>
          )}

          {file && !uploading && (
            <button
              type="button"
              onClick={handleUpload}
              className="w-full h-8 rounded-md bg-teal-50 text-teal-700 text-xs font-semibold hover:bg-teal-100 transition"
            >
              Upload File
            </button>
          )}

          {uploading && (
            <div className="flex items-center justify-center gap-2 h-8 rounded-md bg-teal-50 text-teal-700">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span className="text-xs font-medium">Uploading...</span>
            </div>
          )}

          {error && (
            <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-2">
              <AlertCircle className="h-4 w-4 text-red-600 shrink-0 mt-0.5" />
              <span className="text-xs text-red-700">{error}</span>
            </div>
          )}

          {help && <p className="text-[10px] text-slate-500">{help}</p>}

          {/* Hidden input to store the storage reference for form submission */}
          {uploaded && <input type="hidden" name={name} value={storageRef} />}
        </div>
      ) : (
        <div className="mt-1 rounded-md border border-emerald-200 bg-emerald-50 p-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 min-w-0">
              <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-emerald-900 truncate">File uploaded</p>
                <p className="text-[10px] text-emerald-700 truncate">{storageRef}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleRemove}
              className="p-1 rounded hover:bg-emerald-100 text-emerald-600"
              title="Remove"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          {/* Hidden input to preserve the storage reference for form submission */}
          <input type="hidden" name={name} value={storageRef} />
        </div>
      )}
    </label>
  );
}
