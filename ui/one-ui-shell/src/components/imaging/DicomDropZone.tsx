"use client";

import { useCallback, useState } from "react";
import { FileImage, Loader2, Upload } from "lucide-react";
import { isDicomCandidate } from "@/lib/dicom/dicomService";

interface DicomDropZoneProps {
  onFileSelected: (file: File) => void;
  onOpenUploadDialog?: () => void;
  onReject?: (message: string) => void;
  loading?: boolean;
  compact?: boolean;
}

export function DicomDropZone({ onFileSelected, onOpenUploadDialog, onReject, loading, compact }: DicomDropZoneProps) {
  const [dragOver, setDragOver] = useState(false);

  const handleFiles = useCallback(
    (fileList: FileList | null) => {
      if (!fileList?.length) return;
      const file = Array.from(fileList).find((f) => isDicomCandidate(f));
      if (file) {
        onFileSelected(file);
      } else {
        onReject?.("Please choose a .dcm / DICOM file (not a ZIP or image preview).");
      }
    },
    [onFileSelected, onReject],
  );

  return (
    <div
      className={`flex flex-col items-center justify-center text-center ${compact ? "p-6" : "flex-1 p-8"}`}
      onDragOver={(e) => {
        e.preventDefault();
        setDragOver(true);
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragOver(false);
        handleFiles(e.dataTransfer.files);
      }}
    >
      <div
        className={`w-full max-w-md rounded-2xl border-2 border-dashed px-6 py-10 transition-colors ${
          dragOver ? "border-impilo-400 bg-impilo-900/20" : "border-gray-600 bg-gray-900/50"
        }`}
      >
        <FileImage className="mx-auto mb-3 h-12 w-12 text-gray-500" />
        <p className="text-sm font-medium text-gray-300">Drop a DICOM file here</p>
        <p className="mt-1 text-xs text-gray-500">CT, MRI, X-Ray (.dcm) — view and mark up in the browser</p>
        <label className="mt-4 inline-flex cursor-pointer items-center gap-2 rounded-lg bg-impilo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-impilo-700">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
          {loading ? "Loading…" : "Choose DICOM file"}
          <input
            type="file"
            accept=".dcm,.dicom,.DCM,.DICOM,application/dicom,application/octet-stream,*/*"
            className="sr-only"
            disabled={loading}
            onChange={(e) => {
              handleFiles(e.target.files);
              e.target.value = "";
            }}
          />
        </label>
        {onOpenUploadDialog && (
          <button
            type="button"
            onClick={onOpenUploadDialog}
            className="mt-3 block w-full text-xs text-impilo-400 hover:text-impilo-300"
          >
            Or upload to PACS (Orthanc) for this chart
          </button>
        )}
      </div>
    </div>
  );
}
