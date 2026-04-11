import { describe, expect, it } from "vitest";
import {
  credentialVerifyUrlForToken,
  normalizeCredentialVerifyBaseUrl,
  parseCredentialVerificationResponse,
} from "./credentialVerifyPublic";

describe("normalizeCredentialVerifyBaseUrl", () => {
  it("returns empty for blank", () => {
    expect(normalizeCredentialVerifyBaseUrl(undefined)).toBe("");
    expect(normalizeCredentialVerifyBaseUrl("  ")).toBe("");
  });

  it("trims and strips trailing slash", () => {
    expect(normalizeCredentialVerifyBaseUrl("http://localhost:8094/")).toBe("http://localhost:8094");
  });
});

describe("credentialVerifyUrlForToken", () => {
  it("builds verifier path", () => {
    expect(credentialVerifyUrlForToken("http://localhost:8094", "abc 123")).toBe(
      "http://localhost:8094/v1/public/verify/abc%20123",
    );
  });
});

describe("parseCredentialVerificationResponse", () => {
  it("unwraps ApiResponse data", () => {
    const json = {
      success: true,
      data: {
        status: "VALID",
        credentialType: "CPD",
        subjectName: "Dr. A",
        title: "Module 1",
        issuedBy: "MOHCC",
        validFrom: "2025-01-01",
        validTo: "2026-01-01",
        verifiedAt: "2026-04-10T12:00:00Z",
      },
    };
    const r = parseCredentialVerificationResponse(json);
    expect(r?.status).toBe("VALID");
    expect(r?.subjectName).toBe("Dr. A");
    expect(r?.validTo).toBe("2026-01-01");
  });

  it("accepts flat payload", () => {
    const json = {
      status: "EXPIRED",
      credentialType: "X",
      subjectName: "B",
      title: "T",
      issuedBy: "I",
      validFrom: "2020-01-01",
      validTo: null,
      verifiedAt: "2026-01-01T00:00:00Z",
    };
    const r = parseCredentialVerificationResponse(json);
    expect(r?.status).toBe("EXPIRED");
    expect(r?.validTo).toBeNull();
  });

  it("returns null for invalid input", () => {
    expect(parseCredentialVerificationResponse(null)).toBeNull();
    expect(parseCredentialVerificationResponse({})).toBeNull();
  });
});
