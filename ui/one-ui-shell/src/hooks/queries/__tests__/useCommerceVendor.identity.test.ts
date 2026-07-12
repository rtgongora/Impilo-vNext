import { describe, expect, it } from "vitest";
import { extractVendorId } from "../useCommerceVendor";

describe("extractVendorId", () => {
  it("reads vendorId from a plain body", () => {
    expect(extractVendorId({ vendorId: "V1" })).toBe("V1");
  });
  it("reads vendorId from a {success,data} envelope", () => {
    expect(extractVendorId({ success: true, data: { vendorId: "V2" } })).toBe("V2");
  });
  it("falls back to id", () => {
    expect(extractVendorId({ data: { id: "V3" } })).toBe("V3");
  });
  it("returns undefined when absent", () => {
    expect(extractVendorId({ data: { name: "x" } })).toBeUndefined();
    expect(extractVendorId(null)).toBeUndefined();
  });
});
