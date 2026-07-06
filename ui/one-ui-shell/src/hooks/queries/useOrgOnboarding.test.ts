import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
  },
}));

import { apiClient } from "@/lib/api-client";
import {
  addRepresentative,
  createOrganization,
  fetchRepresentatives,
  submitVerification,
  verifyOrganization,
} from "./useOrgOnboarding";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

describe("useOrgOnboarding network helpers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates an organization (DRAFT)", () => {
    void createOrganization({ code: "ORG-1", legalName: "Acme Health", orgType: "PROVIDER" });
    expect(post).toHaveBeenCalledWith("/internal/v1/organizations", {
      code: "ORG-1",
      legalName: "Acme Health",
      orgType: "PROVIDER",
    });
  });

  it("adds a representative on the organization sub-path", () => {
    void addRepresentative({
      organizationId: "org-1",
      personHealthId: "hid-1",
      roleTitle: "Director",
      appointedBy: "admin-1",
    });
    expect(post).toHaveBeenCalledWith("/internal/v1/organizations/org-1/representatives", {
      personHealthId: "hid-1",
      roleTitle: "Director",
      appointedBy: "admin-1",
    });
  });

  it("submits an organization for verification", () => {
    void submitVerification("org-1");
    expect(post).toHaveBeenCalledWith("/internal/v1/organizations/org-1/submit-verification");
  });

  it("verifies an organization with the verified representative ids", () => {
    void verifyOrganization({ organizationId: "org-1", verifiedRepresentativeIds: ["rep-1", "rep-2"] });
    expect(post).toHaveBeenCalledWith("/internal/v1/organizations/org-1/verify", {
      verifiedRepresentativeIds: ["rep-1", "rep-2"],
    });
  });

  it("lists representatives", () => {
    void fetchRepresentatives("org-1");
    expect(get).toHaveBeenCalledWith("/internal/v1/organizations/org-1/representatives");
  });
});
