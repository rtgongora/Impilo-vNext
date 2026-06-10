import { describe, expect, it } from "vitest";
import { buildFirstAdminPayload } from "@/lib/admin-governance/api/bootstrapApi";
import { parseCsvPreview } from "@/lib/admin-governance/api/importsApi";

describe("bootstrap helpers", () => {
  it("builds sovereign first-admin payload with time-limited bootstrap role", () => {
    const payload = buildFirstAdminPayload(
      { fullName: "Ada Lovelace", officialEmail: "ada@mohcc.gov.zw", approvalReference: "MOHCC-2026-001" },
      "one_time_token",
      "secret-token",
    );
    expect(payload.requestedRoles).toEqual(["national_bootstrap_administrator"]);
    expect(payload.sovereignOrganisation.organisationType).toBe("sovereign_public_owner");
    expect(payload.mfaRequired).toBe(true);
  });
});

describe("bulk import helpers", () => {
  it("parses CSV preview rows", () => {
    const rows = parseCsvPreview("email,full_name,role_template\na@x.com,Alice,org_admin\n");
    expect(rows).toHaveLength(1);
    expect(rows[0].email).toBe("a@x.com");
    expect(rows[0].role_template).toBe("org_admin");
  });

  it("returns empty preview for invalid template", () => {
    expect(parseCsvPreview("only-header\n")).toEqual([]);
  });
});

describe("bootstrap doctrine expectations", () => {
  it("does not treat import parse as activation", () => {
    const rows = parseCsvPreview("email,full_name\nuser@test.com,User\n");
    expect(rows.length).toBeGreaterThan(0);
    expect(rows[0]).not.toHaveProperty("status", "ACTIVE");
  });
});
