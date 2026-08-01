import { describe, expect, it } from "vitest";
import { citizenPortalPathForTests } from "./citizenPortalClient";

describe("citizenPortalPathForTests", () => {
  it("prefixes gateway-relative portal paths", () => {
    expect(citizenPortalPathForTests("/me")).toBe("/internal/v1/vito/portal/me");
    expect(citizenPortalPathForTests("id/request")).toBe("/internal/v1/vito/portal/id/request");
  });
});
