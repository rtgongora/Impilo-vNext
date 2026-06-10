import { describe, expect, it } from "vitest";
import { buildOrganisationCreatePayload } from "@/lib/admin-governance/api/organisationsApi";
import {
  eligibilityBlocksAssignment,
  lookupUnavailableMessage,
} from "@/lib/admin-governance/api/providerWorkerApi";
import { pendingBackendResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import type { ProviderWorkerEligibility } from "@/lib/admin-governance/types";

describe("organisation registry API helpers", () => {
  it("organisation create builds correct payload", () => {
    const payload = buildOrganisationCreatePayload({
      organisationCode: "MOHCC-HQ",
      organisationType: "public_hospital",
      legalName: "MoHCC HQ",
      tradingName: "MoHCC",
      country: "ZW",
      registrationNumber: "REG-1",
      operatingJurisdictions: "Harare, Bulawayo",
    });
    expect(payload.organisationCode).toBe("MOHCC-HQ");
    expect(payload.operatingJurisdictions).toEqual(["Harare", "Bulawayo"]);
  });
});

describe("provider worker eligibility helpers", () => {
  it("blocks direct assignment when lookup unavailable", () => {
    expect(eligibilityBlocksAssignment({ lookupAvailable: false })).toBe(true);
    expect(lookupUnavailableMessage({ integrationStatus: "pending_backend" })).toContain("not currently available");
  });

  it("blocks assignment when council/HSC blocked reasons present", () => {
    const eligibility: ProviderWorkerEligibility = {
      lookupAvailable: true,
      blockedReasons: ["Council registration is SUSPENDED"],
    };
    expect(eligibilityBlocksAssignment(eligibility)).toBe(true);
  });

  it("allows assignment when eligibility is clean", () => {
    const eligibility: ProviderWorkerEligibility = {
      lookupAvailable: true,
      blockedReasons: [],
    };
    expect(eligibilityBlocksAssignment(eligibility)).toBe(false);
  });
});

describe("admin governance API client", () => {
  it("renders pending_backend honestly without fake success", () => {
    const result = pendingBackendResponse("Backend integration pending", "Endpoint missing");
    expect(result.status).toBe("pending_backend");
    expect(result.integrationStatus).toBe("pending_backend");
    expect(isPendingBackend(result)).toBe(true);
    expect(result.policyDecision?.allowed).toBe(false);
  });

  it("does not claim audit ingested on pending backend", () => {
    const result = pendingBackendResponse("Backend integration pending", "Endpoint missing");
    expect(result.auditEventId).toBeUndefined();
    expect(result.auditStatus).toBeUndefined();
  });
});

describe("access request SoR metadata expectations", () => {
  it("marks fallback queue reconciliation explicitly", () => {
    const response = pendingBackendResponse("Queued", "Workforce governance unavailable");
    expect(response.integrationStatus).toBe("pending_backend");
    expect(response.status).not.toBe("completed");
  });
});
