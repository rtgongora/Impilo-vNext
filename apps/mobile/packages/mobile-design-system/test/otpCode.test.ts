import { describe, it, expect } from "vitest";
import { sanitizeOtp, isOtpComplete, otpCells, DEFAULT_OTP_LENGTH } from "../src/forms/otpCode";

describe("otpCode helpers", () => {
  it("defaults to a 6-digit code", () => {
    expect(DEFAULT_OTP_LENGTH).toBe(6);
  });

  it("sanitizes to digits only and clamps to length", () => {
    expect(sanitizeOtp("12 34-56")).toBe("123456");
    expect(sanitizeOtp("abc123456789")).toBe("123456");
    expect(sanitizeOtp("1234", 4)).toBe("1234");
    expect(sanitizeOtp("", 6)).toBe("");
  });

  it("detects completeness against length", () => {
    expect(isOtpComplete("123456")).toBe(true);
    expect(isOtpComplete("12345")).toBe(false);
    expect(isOtpComplete("1234", 4)).toBe(true);
    expect(isOtpComplete("12x4", 4)).toBe(false); // sanitizes to "124"
  });

  it("splits into per-cell characters with empty trailing cells", () => {
    expect(otpCells("12", 6)).toEqual(["1", "2", "", "", "", ""]);
    expect(otpCells("123456", 6)).toEqual(["1", "2", "3", "4", "5", "6"]);
    expect(otpCells("", 4)).toEqual(["", "", "", ""]);
  });
});
