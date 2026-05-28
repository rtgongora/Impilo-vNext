import { beforeEach, describe, expect, it, vi } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

import {
  approveLocalityProposal,
  createFacilityApplication,
  createRegistryImportJob,
  executeRegistryImportJob,
  fetchFacilityDashboardSummary,
  listTrustConsents,
  resolveTerminologyArtifact,
  searchFacilityRegistry,
  searchProductRegistry,
} from "../../services/registryOperationsService";

describe("registryOperationsService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uses facility lifecycle BFF routes for reads and commands", async () => {
    mockApiClient.get.mockResolvedValueOnce({ data: { data: { items: [{ facilityId: 1, name: "Clinic" }] } } });
    mockApiClient.get.mockResolvedValueOnce({ data: { data: { activeFacilities: 1 } } });
    mockApiClient.post.mockResolvedValueOnce({ data: { data: { applicationId: "app-1" } } });

    const facilities = await searchFacilityRegistry({ search: "Clinic", size: 5 });
    const dashboard = await fetchFacilityDashboardSummary();
    const application = await createFacilityApplication({ applicantName: "Operator" });

    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/facility-registry/facilities?search=Clinic&size=5");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/facility-registry/dashboard/summary");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/facility-registry/applications", {
      applicantName: "Operator",
    });
    expect(facilities[0].name).toBe("Clinic");
    expect(dashboard).toEqual({ activeFacilities: 1 });
    expect(application).toEqual({ applicationId: "app-1" });
  });

  it("runs locality, intake/import, product, terminology, and trust routes", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "row-1" }] } });
    mockApiClient.post.mockResolvedValue({ data: { data: { accepted: true } } });

    await approveLocalityProposal("12", "approved");
    await createRegistryImportJob({ targetRegistry: "FACILITY" });
    await executeRegistryImportJob("job-1", true);
    await searchProductRegistry("para");
    await resolveTerminologyArtifact("http://impilo.test/CodeSystem/demo", "v1");
    await listTrustConsents("CPID-1");

    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/registry/localities/proposals/12/approve", {
      reviewerNote: "approved",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/registry-intake/import-jobs", {
      targetRegistry: "FACILITY",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/registry-intake/import-jobs/job-1/execute", {
      dryRun: true,
    });
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/product-registry/search?q=para&size=10&page=0");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/registry/zibo/artifacts/resolve?canonicalUrl=http%3A%2F%2Fimpilo.test%2FCodeSystem%2Fdemo&version=v1",
    );
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/admin/trust/consents?subjectId=CPID-1&size=10");
  });
});
