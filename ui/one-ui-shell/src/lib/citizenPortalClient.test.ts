import { describe, expect, it } from "vitest";
import { citizenPortalPathForTests } from "./citizenPortalClient";

describe("citizenPortalPathForTests", () => {
  it("prefixes gateway-relative portal paths", () => {
    expect(citizenPortalPathForTests("/me")).toBe("/api/v1/portal/me");
    expect(citizenPortalPathForTests("id/request")).toBe("/api/v1/portal/id/request");
  });
});
