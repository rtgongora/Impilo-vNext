/**
 * Patient Lookup Tests — Service layer tests for patient search.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
};

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import { searchPatients, getPatient, searchPatientsByNationalId } from "../../services/patientService";

describe("Patient Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("searches patients by query", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "pat-1",
            type: "Patient",
            attributes: {
              cpid: "CPID-001",
              given_name: "John",
              family_name: "Doe",
              date_of_birth: "1990-01-15",
              sex: "M",
              national_id: "63-123456-A-42",
              phone: "+263771234567",
              status: "ACTIVE",
              facility_id: "fac-1",
              created_at: "2026-03-01T00:00:00Z",
              updated_at: "2026-03-15T00:00:00Z",
            },
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await searchPatients("John");
    expect(result.patients).toHaveLength(1);
    expect(result.patients[0].givenName).toBe("John");
    expect(result.patients[0].familyName).toBe("Doe");
    expect(result.patients[0].cpid).toBe("CPID-001");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      expect.stringContaining("/internal/v1/patients?search=John")
    );
  });

  it("gets a single patient by ID", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "pat-1",
          type: "Patient",
          attributes: {
            cpid: "CPID-001",
            given_name: "John",
            family_name: "Doe",
            date_of_birth: "1990-01-15",
            sex: "M",
            national_id: "63-123456-A-42",
            phone: "+263771234567",
            status: "ACTIVE",
            facility_id: "fac-1",
            created_at: "2026-03-01T00:00:00Z",
            updated_at: "2026-03-15T00:00:00Z",
          },
        },
      },
    });

    const patient = await getPatient("pat-1");
    expect(patient.id).toBe("pat-1");
    expect(patient.nationalId).toBe("63-123456-A-42");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/patients/pat-1");
  });

  it("searches by national ID", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [],
        meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } },
      },
    });

    const result = await searchPatientsByNationalId("63-000000-A-00");
    expect(result).toHaveLength(0);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      expect.stringContaining("search=63-000000-A-00")
    );
  });
});
