/**
 * Personal Flow Tests — Profile, appointments, prescriptions, lab results, coverage.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import { fetchProfile, updateProfile, fetchConsents, updateConsent, deleteAccount } from "../../services/profileService";
import { fetchAppointments, fetchAppointment, requestAppointment, cancelAppointment, checkInAppointment } from "../../services/appointmentService";
import { fetchPrescriptions, fetchPrescription, requestRefill } from "../../services/prescriptionService";
import { fetchLabResults, fetchLabResult } from "../../services/labResultService";
import { fetchCoverage, fetchCoverageDetail } from "../../services/coverageService";

describe("Profile Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches citizen profile", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          attributes: {
            cpid: "CPID-1000",
            givenName: "Tatenda",
            familyName: "Moyo",
            dateOfBirth: "1992-06-15",
            sex: "F",
            phone: "+263771234567",
            email: "tatenda@example.com",
            preferredLanguage: "en",
          },
        },
      },
    });

    const profile = await fetchProfile();
    expect(profile.cpid).toBe("CPID-1000");
    expect(profile.givenName).toBe("Tatenda");
    expect(profile.familyName).toBe("Moyo");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/profile");
  });

  it("updates citizen profile", async () => {
    mockApiClient.patch.mockResolvedValue({
      data: {
        data: {
          attributes: {
            cpid: "CPID-1000",
            givenName: "Tatenda",
            familyName: "Moyo",
            phone: "+263779999999",
            email: "new@example.com",
          },
        },
      },
    });

    const profile = await updateProfile({ phone: "+263779999999", email: "new@example.com" });
    expect(profile.phone).toBe("+263779999999");
    expect(mockApiClient.patch).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/profile",
      { phone: "+263779999999", email: "new@example.com" }
    );
  });

  it("fetches consent preferences", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { id: "consent-1", category: "data_sharing", description: "Share with providers", granted: true },
          { id: "consent-2", category: "marketing", description: "Health campaigns", granted: false },
        ],
      },
    });

    const consents = await fetchConsents();
    expect(consents).toHaveLength(2);
    expect(consents[0].granted).toBe(true);
    expect(consents[1].granted).toBe(false);
  });

  it("updates a consent preference", async () => {
    mockApiClient.patch.mockResolvedValue({
      data: {
        data: { id: "consent-2", category: "marketing", description: "Health campaigns", granted: true },
      },
    });

    const consent = await updateConsent("consent-2", true);
    expect(consent.granted).toBe(true);
    expect(mockApiClient.patch).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/profile/consents/consent-2",
      { granted: true }
    );
  });

  it("deletes citizen account", async () => {
    mockApiClient.delete.mockResolvedValue({});

    await deleteAccount();
    expect(mockApiClient.delete).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/profile/account"
    );
  });
});

describe("Appointment Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches paginated appointments", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "apt-1",
            facilityName: "Harare Central",
            appointmentType: "GENERAL",
            status: "CONFIRMED",
            scheduledAt: "2026-03-20T09:00:00Z",
            reason: "Check-up",
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchAppointments({ status: "CONFIRMED" });
    expect(result.items).toHaveLength(1);
    expect(result.items[0].status).toBe("CONFIRMED");
    expect(result.totalElements).toBe(1);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/appointments?status=CONFIRMED"
    );
  });

  it("fetches single appointment", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "apt-1",
          facilityName: "Harare Central",
          appointmentType: "GENERAL",
          status: "CONFIRMED",
          scheduledAt: "2026-03-20T09:00:00Z",
        },
      },
    });

    const apt = await fetchAppointment("apt-1");
    expect(apt.id).toBe("apt-1");
    expect(apt.facilityName).toBe("Harare Central");
  });

  it("requests a new appointment", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "apt-2",
          facilityName: "Chitungwiza Hospital",
          appointmentType: "SPECIALIST",
          status: "REQUESTED",
          scheduledAt: "2026-03-25T10:00:00Z",
        },
      },
    });

    const apt = await requestAppointment({
      facilityId: "fac-1",
      appointmentType: "SPECIALIST",
      preferredDate: "2026-03-25",
      reason: "Referral follow-up",
    });
    expect(apt.status).toBe("REQUESTED");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/appointments",
      { facilityId: "fac-1", appointmentType: "SPECIALIST", preferredDate: "2026-03-25", reason: "Referral follow-up" }
    );
  });

  it("cancels an appointment", async () => {
    mockApiClient.post.mockResolvedValue({});

    await cancelAppointment("apt-1", "No longer needed");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/appointments/apt-1/cancel",
      { reason: "No longer needed" }
    );
  });

  it("checks in through shared citizen BFF endpoint", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          appointment_id: "apt-1",
          status: "CHECKED_IN",
          encounter_id: "9001",
        },
        meta: {
          encounter_id: "9001",
          core_transaction_id: "encounter:9001",
        },
      },
    });

    const result = await checkInAppointment("apt-1");
    expect(result.data.status).toBe("CHECKED_IN");
    expect(result.meta.core_transaction_id).toBe("encounter:9001");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/appointments/apt-1/check-in",
    );
  });
});

describe("Prescription Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches prescriptions", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "rx-1",
            medicationName: "Metformin 500mg",
            dosage: "1 tablet twice daily",
            status: "ACTIVE",
            prescribedAt: "2026-03-01",
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchPrescriptions();
    expect(result.items).toHaveLength(1);
    expect(result.items[0].medicationName).toBe("Metformin 500mg");
  });

  it("fetches single prescription", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "rx-1",
          medicationName: "Metformin 500mg",
          dosage: "1 tablet twice daily",
          status: "ACTIVE",
          refillsRemaining: 3,
        },
      },
    });

    const rx = await fetchPrescription("rx-1");
    expect(rx.refillsRemaining).toBe(3);
  });

  it("requests a refill", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { refillId: "refill-1", status: "REFILL_REQUESTED" } },
    });

    const result = await requestRefill("rx-1");
    expect(result.status).toBe("REFILL_REQUESTED");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/prescriptions/rx-1/refill",
      {}
    );
  });
});

describe("Lab Results Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches lab results", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "lr-1",
            testName: "Complete Blood Count",
            status: "FINAL",
            collectedAt: "2026-03-10T08:00:00Z",
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchLabResults();
    expect(result.items).toHaveLength(1);
    expect(result.items[0].testName).toBe("Complete Blood Count");
  });

  it("fetches a single result", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "lr-1",
          testName: "Complete Blood Count",
          status: "FINAL",
          values: [{ name: "WBC", value: "5.5", unit: "10^9/L", referenceRange: "4.0-11.0" }],
        },
      },
    });

    const result = await fetchLabResult("lr-1");
    expect(result.testName).toBe("Complete Blood Count");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/results/lr-1"
    );
  });
});

describe("Coverage Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches coverage plans", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "cov-1",
            planName: "NHIF Basic",
            provider: "NHIF",
            status: "ACTIVE",
            memberNumber: "NHIF-12345",
          },
        ],
      },
    });

    const result = await fetchCoverage();
    expect(result).toHaveLength(1);
    expect(result[0].planName).toBe("NHIF Basic");
  });

  it("fetches a single coverage plan", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "cov-1",
          planName: "NHIF Basic",
          provider: "NHIF",
          status: "ACTIVE",
          memberNumber: "NHIF-12345",
          validFrom: "2026-01-01",
          validUntil: "2026-12-31",
        },
      },
    });

    const plan = await fetchCoverageDetail("cov-1");
    expect(plan.memberNumber).toBe("NHIF-12345");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/coverage/cov-1"
    );
  });
});
