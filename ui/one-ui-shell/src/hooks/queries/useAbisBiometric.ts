/**
 * Hooks for the biometric enrol → verify workspace. These drive the REAL
 * pipeline: browser capture image → BFF → ABIS (extract template + enrol +
 * verify) → the core-infra matcher-engine (SourceAFIS/ONNX). No mocks.
 */
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/identity/biometric/abis";

export type Modality = "FINGERPRINT" | "FACE" | "IRIS";

export interface ExtractResult {
  ok: boolean;
  templateBase64?: string;
  quality?: number;
  detail?: string;
}

export interface VerifyResult {
  result: "MATCH" | "NO_MATCH" | "UNAVAILABLE" | "NO_REFERENCE";
  confidence: number;
  summary?: string;
}

/** Extract a template from a capture image (base64, no data-URL prefix). */
export function useExtractTemplate() {
  return useMutation({
    mutationFn: (input: { modality: Modality; sampleBase64: string; width?: number; height?: number; dpi?: number }) =>
      apiClient.post<ExtractResult>(`${BASE}/extract`, input),
  });
}

/** Enrol an extracted template under an opaque subject reference. */
export function useEnrollTemplate() {
  return useMutation({
    mutationFn: (input: { subjectRef: string; modality: Modality; templateBase64: string; position?: string }) =>
      apiClient.post<{ id?: string; subjectRef?: string; status?: string }>(`${BASE}/enroll`, input),
  });
}

/** 1:1 verify a probe template against a subject → real MATCH / NO_MATCH. */
export function useVerifyTemplate() {
  return useMutation({
    mutationFn: (input: { subjectRef: string; modality: Modality; probeBase64: string; position?: string }) =>
      apiClient.post<VerifyResult>(`${BASE}/verify`, input),
  });
}

/** Read a File as raw base64 (strips the `data:*;base64,` prefix). */
export function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}
