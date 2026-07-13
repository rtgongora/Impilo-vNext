/**
 * Regression guard for QA #15 / #14: madiApi drive+donor lookups must unwrap the
 * BFF `{data, meta}` envelope so callers receive arrays/objects — never the raw
 * envelope. Spreading an un-unwrapped envelope (`[...listDrives()]`) previously
 * threw "object is not iterable" and blanked the whole MADI surface.
 */
import { describe, expect, it, vi, beforeEach } from "vitest";

const get = vi.fn();
vi.mock("../api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

import { madiApi, unwrapMadiData } from "../madi";

beforeEach(() => {
  get.mockReset();
});

describe("madiApi envelope unwrapping", () => {
  it("listDrives returns the inner array, not the {data,meta} envelope", async () => {
    get.mockResolvedValueOnce({ data: [{ driveId: "d1" }], meta: { request_id: "r" } });
    const drives = await madiApi.listDrives();
    expect(Array.isArray(drives)).toBe(true);
    expect(drives).toHaveLength(1);
    // The spread that used to crash the drives page must now succeed.
    expect(() => [...drives]).not.toThrow();
  });

  it("drivesNearMe returns the inner array, not the {data,meta} envelope", async () => {
    get.mockResolvedValueOnce({ data: [{ drive_id: "n1" }], meta: {} });
    const near = await madiApi.drivesNearMe("site-ref");
    expect(Array.isArray(near)).toBe(true);
    expect(() => [...near]).not.toThrow();
  });

  it("unwrapMadiData tolerates a bare (already-unwrapped) array", () => {
    expect(unwrapMadiData<number[]>([1, 2, 3])).toEqual([1, 2, 3]);
  });

  it("unwrapMadiData returns null when the BFF sends {data:null} for a non-donor", () => {
    expect(unwrapMadiData<unknown>({ data: null, meta: {} })).toBeNull();
  });
});
