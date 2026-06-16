import { describe, expect, it } from "vitest";
import { normalizeProviderListingResponse } from "@/lib/provider-activation";

describe("normalizeProviderListingResponse", () => {
  it("maps a single VARAPI provider object from BFF envelope", () => {
    const rows = normalizeProviderListingResponse({
      data: {
        providerPublicId: "PROV-ZW-ADMIN-001",
        givenName: "System",
        familyName: "Admin",
        cadre: "PLATFORM_ADMIN",
        practiceNumber: "MOHCC-ADMIN-001",
        status: "ACTIVE",
      },
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]?.providerId).toBe("PROV-ZW-ADMIN-001");
    expect(rows[0]?.displayName).toBe("System Admin");
    expect(rows[0]?.registrationNumber).toBe("MOHCC-ADMIN-001");
  });

  it("maps provider arrays", () => {
    const rows = normalizeProviderListingResponse({
      data: [{ providerPublicId: "P-1", status: "ACTIVE" }, { providerPublicId: "P-2", status: "SUSPENDED" }],
    });
    expect(rows.map((r) => r.providerId)).toEqual(["P-1", "P-2"]);
  });
});
