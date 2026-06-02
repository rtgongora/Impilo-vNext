/**
 * DICOM parsing, rendering, and PACS upload via Experience BFF → Orthanc.
 * Adapted from Impilo Lovable imaging module.
 */

import dicomParser from "dicom-parser";
import { apiClient } from "@/lib/api-client";

export const DicomTags = {
  PatientName: "x00100010",
  PatientID: "x00100020",
  PatientBirthDate: "x00100030",
  PatientSex: "x00100040",
  StudyInstanceUID: "x0020000d",
  StudyDate: "x00080020",
  StudyTime: "x00080030",
  StudyDescription: "x00081030",
  AccessionNumber: "x00080050",
  SeriesInstanceUID: "x0020000e",
  Modality: "x00080060",
  BodyPartExamined: "x00180015",
  SOPInstanceUID: "x00080018",
  Rows: "x00280010",
  Columns: "x00280011",
  BitsAllocated: "x00280100",
  PixelRepresentation: "x00280103",
  PixelSpacing: "x00280030",
  WindowCenter: "x00281050",
  WindowWidth: "x00281051",
  RescaleIntercept: "x00281052",
  RescaleSlope: "x00281053",
  PhotometricInterpretation: "x00280004",
  PixelData: "x7fe00010",
  TransferSyntaxUID: "x00020010",
} as const;

const UNCOMPRESSED_TRANSFER_SYNTAXES = new Set([
  "1.2.840.10008.1.2",
  "1.2.840.10008.1.2.1",
  "1.2.840.10008.1.2.2",
  "1.2.840.10008.1.2.1.99",
]);

const MAX_DIMENSION = 8192;

export interface ParsedDicomData {
  patientName: string;
  patientId: string;
  patientBirthDate: string;
  patientSex: string;
  studyDate: string;
  studyTime: string;
  studyDescription: string;
  accessionNumber: string;
  modality: string;
  bodyPartExamined: string;
  rows: number;
  columns: number;
  bitsAllocated: number;
  pixelSpacing: [number, number] | null;
  windowCenter: number | null;
  windowWidth: number | null;
  rescaleIntercept: number;
  rescaleSlope: number;
  photometricInterpretation: string;
  pixelData: Uint8Array | Int16Array | Uint16Array | null;
  transferSyntaxUid: string;
}

export interface ViewerCompatibility {
  supported: boolean;
  reason?: string;
}

export interface DicomImage {
  width: number;
  height: number;
  pixelData: Int16Array | Uint16Array | Uint8Array;
  minPixelValue: number;
  maxPixelValue: number;
  slope: number;
  intercept: number;
  windowWidth: number;
  windowCenter: number;
  invert: boolean;
  color: boolean;
  columnPixelSpacing: number;
  rowPixelSpacing: number;
  sizeInBytes: number;
  getPixelData: () => Int16Array | Uint16Array | Uint8Array;
}

export interface FetchDicomResult {
  image: DicomImage | null;
  error?: string;
  parsed?: ParsedDicomData;
}

function parseDicomDataSet(byteArray: Uint8Array): dicomParser.DataSet {
  try {
    return dicomParser.parseDicom(byteArray);
  } catch (err) {
    const detail = err instanceof Error ? err.message : "parse error";
    throw new Error(
      `Unable to read DICOM (${detail}). Common causes: JPEG/JPEG-2000 compressed slices (re-export as uncompressed), or a non-DICOM file renamed to .dcm.`,
    );
  }
}

/** True if the file looks like a DICOM candidate (extension or DICM magic). */
export function isDicomCandidate(file: File): boolean {
  const name = file.name.toLowerCase();
  if (name.endsWith(".dcm") || name.endsWith(".dicom") || name.endsWith(".dcm.zip")) return true;
  if (file.type === "application/dicom" || file.type === "application/octet-stream") return true;
  return !name.includes(".") || name.endsWith(".img");
}

export function parseDicomFile(arrayBuffer: ArrayBuffer): ParsedDicomData {
  const byteArray = new Uint8Array(arrayBuffer);
  const dataSet = parseDicomDataSet(byteArray);

  const getString = (tag: string) => {
    try {
      return dataSet.string(tag) || "";
    } catch {
      return "";
    }
  };

  const getNumber = (tag: string, defaultValue = 0) => {
    try {
      const value = dataSet.uint16(tag) ?? dataSet.int16(tag);
      return value !== undefined ? value : defaultValue;
    } catch {
      return defaultValue;
    }
  };

  const getFloat = (tag: string) => {
    try {
      const fromParser = dataSet.floatString(tag);
      if (fromParser != null && Number.isFinite(fromParser)) return fromParser;
    } catch {
      /* fall through */
    }
    try {
      const str = getString(tag);
      if (!str) return null;
      const first = str.split("\\")[0]?.trim();
      const n = parseFloat(first);
      return Number.isFinite(n) ? n : null;
    } catch {
      return null;
    }
  };

  const getFloatArray = (tag: string): number[] | null => {
    try {
      const str = getString(tag);
      if (!str) return null;
      return str.split("\\").map(parseFloat);
    } catch {
      return null;
    }
  };

  let pixelData: Uint8Array | Int16Array | Uint16Array | null = null;
  try {
    const pixelDataElement = dataSet.elements[DicomTags.PixelData];
    if (pixelDataElement && pixelDataElement.length > 0) {
      const bitsAllocated = getNumber(DicomTags.BitsAllocated, 16);
      const pixelRepresentation = getNumber(DicomTags.PixelRepresentation, 0);
      const buffer = dataSet.byteArray.buffer;
      const byteOffset = dataSet.byteArray.byteOffset + pixelDataElement.dataOffset;

      if (bitsAllocated === 8) {
        pixelData = new Uint8Array(buffer, byteOffset, pixelDataElement.length);
      } else if (bitsAllocated === 16) {
        const numPixels = pixelDataElement.length / 2;
        if (byteOffset % 2 !== 0) {
          const raw = new Uint8Array(buffer, byteOffset, pixelDataElement.length);
          if (pixelRepresentation === 0) {
            pixelData = new Uint16Array(numPixels);
            for (let i = 0; i < numPixels; i++) pixelData[i] = raw[i * 2] | (raw[i * 2 + 1] << 8);
          } else {
            const signed = new Int16Array(numPixels);
            for (let i = 0; i < numPixels; i++) signed[i] = raw[i * 2] | (raw[i * 2 + 1] << 8);
            pixelData = signed;
          }
        } else if (pixelRepresentation === 0) {
          pixelData = new Uint16Array(buffer, byteOffset, numPixels);
        } else {
          pixelData = new Int16Array(buffer, byteOffset, numPixels);
        }
      }
    }
  } catch {
    pixelData = null;
  }

  const pixelSpacingArr = getFloatArray(DicomTags.PixelSpacing);

  return {
    patientName: getString(DicomTags.PatientName),
    patientId: getString(DicomTags.PatientID),
    patientBirthDate: getString(DicomTags.PatientBirthDate),
    patientSex: getString(DicomTags.PatientSex),
    studyDate: getString(DicomTags.StudyDate),
    studyTime: getString(DicomTags.StudyTime),
    studyDescription: getString(DicomTags.StudyDescription),
    accessionNumber: getString(DicomTags.AccessionNumber),
    modality: getString(DicomTags.Modality),
    bodyPartExamined: getString(DicomTags.BodyPartExamined),
    rows: getNumber(DicomTags.Rows),
    columns: getNumber(DicomTags.Columns),
    bitsAllocated: getNumber(DicomTags.BitsAllocated, 16),
    pixelSpacing:
      pixelSpacingArr && pixelSpacingArr.length >= 2 ? [pixelSpacingArr[0], pixelSpacingArr[1]] : null,
    windowCenter: getFloat(DicomTags.WindowCenter) ?? null,
    windowWidth: getFloat(DicomTags.WindowWidth) ?? null,
    rescaleIntercept: getFloat(DicomTags.RescaleIntercept) || 0,
    rescaleSlope: getFloat(DicomTags.RescaleSlope) || 1,
    photometricInterpretation: getString(DicomTags.PhotometricInterpretation),
    pixelData,
    transferSyntaxUid: getString(DicomTags.TransferSyntaxUID),
  };
}

export function checkViewerCompatibility(parsed: ParsedDicomData): ViewerCompatibility {
  const uid = (parsed.transferSyntaxUid || "").trim();
  const looksCompressed =
    uid &&
    !UNCOMPRESSED_TRANSFER_SYNTAXES.has(uid) &&
    (uid.includes("1.2.840.10008.1.2.4") || uid.includes("1.2.840.10008.1.2.5"));
  if (looksCompressed) {
    return {
      supported: false,
      reason: `Compressed transfer syntax (${uid}). Use Orthanc preview or re-export as uncompressed.`,
    };
  }
  if (!parsed.rows || !parsed.columns || parsed.rows < 1 || parsed.columns < 1) {
    return { supported: false, reason: "Invalid image dimensions." };
  }
  if (parsed.rows > MAX_DIMENSION || parsed.columns > MAX_DIMENSION) {
    return { supported: false, reason: "Image dimensions exceed viewer limit." };
  }
  const bitsAllocated = parsed.bitsAllocated ?? 0;
  if (bitsAllocated !== 8 && bitsAllocated !== 16) {
    return { supported: false, reason: `Unsupported bit depth (${bitsAllocated}).` };
  }
  const expectedPixels = parsed.rows * parsed.columns;
  if (!parsed.pixelData || parsed.pixelData.length < expectedPixels) {
    return { supported: false, reason: "Missing or incomplete pixel data." };
  }
  return { supported: true };
}

/** Window/level from displayed (HU) pixel range — avoids black screen from mismatched DICOM tags. */
export function computeAutoWindowLevel(image: DicomImage): { windowWidth: number; windowCenter: number } {
  const data = image.getPixelData();
  const n = Math.min(data.length, image.width * image.height);
  let minV = Infinity;
  let maxV = -Infinity;
  for (let i = 0; i < n; i++) {
    const v = data[i] * image.slope + image.intercept;
    if (v < minV) minV = v;
    if (v > maxV) maxV = v;
  }
  if (!Number.isFinite(minV) || !Number.isFinite(maxV)) {
    return { windowWidth: 400, windowCenter: 40 };
  }
  const span = maxV - minV;
  return {
    windowWidth: Math.max(span, 1),
    windowCenter: (maxV + minV) / 2,
  };
}

export function createDicomImage(parsed: ParsedDicomData): DicomImage | null {
  if (!parsed.pixelData || !parsed.rows || !parsed.columns) return null;

  const slope = parsed.rescaleSlope || 1;
  const intercept = parsed.rescaleIntercept || 0;
  const expectedPixels = parsed.rows * parsed.columns;
  let minPixel = Infinity;
  let maxPixel = -Infinity;
  for (let i = 0; i < Math.min(parsed.pixelData.length, expectedPixels); i++) {
    const val = parsed.pixelData[i] * slope + intercept;
    if (val < minPixel) minPixel = val;
    if (val > maxPixel) maxPixel = val;
  }

  const invert = parsed.photometricInterpretation === "MONOCHROME1";
  const range = maxPixel - minPixel;
  const autoWidth = range > 0 ? range : 400;
  const autoCenter = range > 0 ? (maxPixel + minPixel) / 2 : 40;
  const tagWidth = parsed.windowWidth;
  const tagCenter = parsed.windowCenter;
  const useDicomTags =
    tagWidth != null &&
    tagWidth > 1 &&
    tagCenter != null &&
    Number.isFinite(tagCenter);

  return {
    width: parsed.columns,
    height: parsed.rows,
    pixelData: parsed.pixelData,
    minPixelValue: minPixel,
    maxPixelValue: maxPixel,
    slope: parsed.rescaleSlope,
    intercept: parsed.rescaleIntercept,
    windowWidth: useDicomTags ? tagWidth : autoWidth,
    windowCenter: useDicomTags ? tagCenter : autoCenter,
    invert,
    color: false,
    columnPixelSpacing: parsed.pixelSpacing?.[1] || 1,
    rowPixelSpacing: parsed.pixelSpacing?.[0] || 1,
    sizeInBytes: parsed.pixelData.byteLength,
    getPixelData: () => parsed.pixelData!,
  };
}

export function renderDicomToCanvas(
  canvas: HTMLCanvasElement,
  image: DicomImage,
  windowWidth: number,
  windowCenter: number,
  options: {
    invert?: boolean;
    zoom?: number;
    panX?: number;
    panY?: number;
    rotation?: number;
    flipH?: boolean;
    flipV?: boolean;
  } = {},
): void {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const pixelData = image.getPixelData();
  const expectedPixels = image.width * image.height;
  if (!pixelData || pixelData.length < expectedPixels) {
    canvas.width = image.width;
    canvas.height = image.height;
    ctx.fillStyle = "#1a1a1a";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    return;
  }

  const { invert = image.invert } = options;

  canvas.width = image.width;
  canvas.height = image.height;

  const imageData = ctx.createImageData(image.width, image.height);
  const pixels = imageData.data;

  const w = Math.max(1, windowWidth);
  const windowMin = windowCenter - w / 2;
  const windowMax = windowCenter + w / 2;

  const pixelCount = expectedPixels;
  for (let i = 0; i < pixelCount; i++) {
    let pixelValue = pixelData[i] * image.slope + image.intercept;
    if (pixelValue <= windowMin) pixelValue = 0;
    else if (pixelValue >= windowMax) pixelValue = 255;
    else pixelValue = ((pixelValue - windowMin) / w) * 255;
    if (invert) pixelValue = 255 - pixelValue;
    const pixelIndex = i * 4;
    pixels[pixelIndex] = pixelValue;
    pixels[pixelIndex + 1] = pixelValue;
    pixels[pixelIndex + 2] = pixelValue;
    pixels[pixelIndex + 3] = 255;
  }

  ctx.putImageData(imageData, 0, 0);
}

export async function loadDicomFromArrayBuffer(arrayBuffer: ArrayBuffer): Promise<FetchDicomResult> {
  if (arrayBuffer.byteLength < 8) {
    return { image: null, error: "File too small to be DICOM." };
  }

  let parsed: ParsedDicomData;
  try {
    parsed = parseDicomFile(arrayBuffer);
  } catch (e) {
    const message = e instanceof Error ? e.message : "Could not parse DICOM file.";
    return { image: null, error: message };
  }

  const compat = checkViewerCompatibility(parsed);
  if (!compat.supported) {
    return { image: null, error: compat.reason, parsed };
  }
  const image = createDicomImage(parsed);
  if (!image) return { image: null, error: "Could not decode pixel data.", parsed };
  return { image, parsed };
}

export async function fetchOrthancInstanceDicom(instanceId: string): Promise<FetchDicomResult> {
  try {
    const blob = await apiClient.getBlob(`/internal/v1/pacs/instances/${instanceId}/file`);
    if (!blob.size) return { image: null, error: "Empty DICOM response from PACS." };
    return loadDicomFromArrayBuffer(await blob.arrayBuffer());
  } catch {
    return { image: null, error: "Failed to load DICOM from PACS." };
  }
}

export async function uploadDicomFile(
  file: File,
): Promise<{ orthancInstanceId?: string; error?: string }> {
  try {
    const body = await file.arrayBuffer();
    const text = await apiClient.postDicom("/internal/v1/pacs/instances/dicom", body);
    const parsed = JSON.parse(text) as { ID?: string; id?: string };
    const id = parsed.ID ?? parsed.id;
    if (!id) return { error: "Orthanc did not return an instance ID." };
    return { orthancInstanceId: id };
  } catch (e: unknown) {
    const msg =
      e && typeof e === "object" && "error" in e && typeof (e as { error?: { message?: string } }).error?.message === "string"
        ? (e as { error: { message: string } }).error.message
        : "Upload failed";
    return { error: msg };
  }
}

export async function uploadDicomFiles(
  files: File[],
): Promise<{ uploaded: number; total: number; results: { file: string; orthancInstanceId?: string; error?: string }[] }> {
  const results: { file: string; orthancInstanceId?: string; error?: string }[] = [];
  let uploaded = 0;
  for (const file of files) {
    const r = await uploadDicomFile(file);
    if (r.orthancInstanceId) {
      uploaded += 1;
      results.push({ file: file.name, orthancInstanceId: r.orthancInstanceId });
    } else {
      results.push({ file: file.name, error: r.error ?? "Unknown error" });
    }
  }
  return { uploaded, total: files.length, results };
}

export const DicomMeasurements = {
  calculateDistance(
    p1: { x: number; y: number },
    p2: { x: number; y: number },
    pixelSpacing: [number, number],
  ): number {
    const dx = (p2.x - p1.x) * pixelSpacing[1];
    const dy = (p2.y - p1.y) * pixelSpacing[0];
    return Math.sqrt(dx * dx + dy * dy);
  },
  calculateAngle(
    p1: { x: number; y: number },
    vertex: { x: number; y: number },
    p2: { x: number; y: number },
  ): number {
    const v1 = { x: p1.x - vertex.x, y: p1.y - vertex.y };
    const v2 = { x: p2.x - vertex.x, y: p2.y - vertex.y };
    const dot = v1.x * v2.x + v1.y * v2.y;
    const mag1 = Math.sqrt(v1.x * v1.x + v1.y * v1.y);
    const mag2 = Math.sqrt(v2.x * v2.x + v2.y * v2.y);
    const cosAngle = dot / (mag1 * mag2);
    return Math.acos(Math.max(-1, Math.min(1, cosAngle))) * (180 / Math.PI);
  },
};
