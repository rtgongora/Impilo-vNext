import * as dicomParser from "dicom-parser";
import type { DataSet } from "dicom-parser";

export type NativeWlDecodeResult =
  | {
      ok: true;
      width: number;
      height: number;
      /** HU-like values after rescale (float32, rows * cols) */
      hu: Float32Array;
      defaultCenter: number;
      defaultWidth: number;
    }
  | { ok: false; reason: string };

function u16(ds: DataSet, tag: string): number | undefined {
  try {
    const v = ds.uint16(tag);
    return Number.isFinite(v) ? v : undefined;
  } catch {
    return undefined;
  }
}

function dicomString(ds: DataSet, tag: string): string | undefined {
  try {
    const s = ds.string(tag)?.trim();
    return s && s.length > 0 ? s : undefined;
  } catch {
    return undefined;
  }
}

function parseFloatDs(s: string | undefined): number | undefined {
  if (!s) return undefined;
  const n = Number(s.replace(",", "."));
  return Number.isFinite(n) ? n : undefined;
}

/**
 * Parses a single-frame uncompressed monochrome DICOM and returns HU-like samples.
 * Supports common Orthanc explicit-VR little-endian CT/MR exports (8- or 16-bit).
 */
export function decodeNativeGrayscaleForWl(arrayBuffer: ArrayBuffer): NativeWlDecodeResult {
  try {
    const byteArray = new Uint8Array(arrayBuffer);
    const ds = dicomParser.parseDicom(byteArray);

    const rows = u16(ds, "x00280010");
    const cols = u16(ds, "x00280011");
    const bitsAllocated = u16(ds, "x00280100") ?? 16;
    const pixelRepresentation = u16(ds, "x00280103") ?? 0;
    const photometric = dicomString(ds, "x00280004") ?? "MONOCHROME2";

    if (!rows || !cols) {
      return { ok: false, reason: "Missing Rows/Columns" };
    }
    if (!photometric.startsWith("MONOCHROME")) {
      return { ok: false, reason: `PhotometricInterpretation ${photometric} not supported` };
    }
    if (bitsAllocated !== 16 && bitsAllocated !== 8) {
      return { ok: false, reason: `BitsAllocated ${bitsAllocated} not supported` };
    }

    const pixelEl = ds.elements["x7fe00010"];
    if (!pixelEl) {
      return { ok: false, reason: "No PixelData" };
    }

    const expected = rows * cols * (bitsAllocated / 8);
    if (pixelEl.length < expected) {
      return { ok: false, reason: "PixelData shorter than expected for one frame" };
    }

    const offset = pixelEl.dataOffset;
    const hu = new Float32Array(rows * cols);
    const slope = parseFloatDs(dicomString(ds, "x00281053")) ?? 1;
    const intercept = parseFloatDs(dicomString(ds, "x00281052")) ?? 0;

    if (bitsAllocated === 16) {
      const view = new DataView(byteArray.buffer, byteArray.byteOffset + offset, expected);
      for (let i = 0; i < rows * cols; i++) {
        const stored = pixelRepresentation === 1 ? view.getInt16(i * 2, true) : view.getUint16(i * 2, true);
        hu[i] = stored * slope + intercept;
      }
    } else {
      for (let i = 0; i < rows * cols; i++) {
        const stored = byteArray[offset + i]!;
        hu[i] = stored * slope + intercept;
      }
    }

    let defaultCenter = parseFloatDs(dicomString(ds, "x00281050")) ?? 40;
    let defaultWidth = parseFloatDs(dicomString(ds, "x00281051")) ?? 400;
    if (!Number.isFinite(defaultCenter)) defaultCenter = 40;
    if (!Number.isFinite(defaultWidth) || defaultWidth < 1) defaultWidth = 400;

    return {
      ok: true,
      width: cols,
      height: rows,
      hu,
      defaultCenter,
      defaultWidth,
    };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return { ok: false, reason: msg };
  }
}

export function applyWindowLevelToRgba(
  hu: Float32Array,
  width: number,
  height: number,
  windowCenter: number,
  windowWidth: number,
  outRgba: Uint8ClampedArray,
): void {
  const ww = Math.max(windowWidth, 1e-6);
  const low = windowCenter - ww / 2;
  const high = windowCenter + ww / 2;
  const n = width * height;
  for (let i = 0; i < n; i++) {
    const v = hu[i]!;
    let y: number;
    if (v <= low) y = 0;
    else if (v >= high) y = 255;
    else y = ((v - low) / (high - low)) * 255;
    const o = i * 4;
    const g = y | 0;
    outRgba[o] = g;
    outRgba[o + 1] = g;
    outRgba[o + 2] = g;
    outRgba[o + 3] = 255;
  }
}
