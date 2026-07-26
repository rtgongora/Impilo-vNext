import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient, ApiError } from "@impilo/mobile-api-client";
import {
  openPartograph,
  getActivePartograph,
  getPartograph,
  addPartographPoint,
  closePartograph,
  openCtgSession,
  getActiveCtgSession,
  addCtgAnnotation,
} from "./maternityService";

vi.mock("@impilo/mobile-api-client", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
  };
});

function ok<T>(data: T) {
  return { data: { data }, status: 200, correlationId: "c1", headers: {} };
}

beforeEach(() => {
  vi.mocked(apiClient.get).mockReset();
  vi.mocked(apiClient.post).mockReset();
  vi.mocked(apiClient.patch).mockReset();
});

describe("partograph — the 200-vs-502 distinction (contract §4.1, §4.2)", () => {
  it("treats partograph_active: false as a real answer, not an error", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", partograph_active: false }));
    const result = await getActivePartograph("P1");
    expect(result).toEqual({ partographActive: false, patientId: "P1" });
  });

  it("returns the session (with its embedded progress) when one is open", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ session_id: "S1", patient_id: "P1", status: "ACTIVE", progress: { status: "LEFT_OF_ALERT" } }),
    );
    const result = await getActivePartograph("P1");
    expect(result.partographActive).toBe(true);
    if (result.partographActive) {
      expect(result.session.session_id).toBe("S1");
      expect(result.session.progress.status).toBe("LEFT_OF_ALERT");
    }
  });

  it("propagates a PCT_UNAVAILABLE failure rather than collapsing it into 'no session'", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(getActivePartograph("P1")).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });

  it("builds the active-session query with patientId and optional encounterId", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", partograph_active: false }));
    await getActivePartograph("P1", "E1");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/active?patientId=P1&encounterId=E1",
    );
  });
});

describe("partograph — session lifecycle and observation writes", () => {
  it("openPartograph posts camelCase identifiers", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ session_id: "S1", patient_id: "P1", status: "ACTIVE" }));
    await openPartograph({ patientId: "P1", encounterId: "E1" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/maternity/partograph/sessions", {
      patientId: "P1",
      journeyId: undefined,
      encounterId: "E1",
      labourPhase: undefined,
    });
  });

  it("getPartograph unwraps the session, its points and progress", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ session_id: "S1", points: [{ observation_id: "O1" }], progress: { status: "INSUFFICIENT_DATA" } }),
    );
    const detail = await getPartograph("S1");
    expect(detail.points).toHaveLength(1);
    expect(detail.progress.status).toBe("INSUFFICIENT_DATA");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/maternity/partograph/sessions/S1");
  });

  it("addPartographPoint sends exactly the answers it is given — nothing added, nothing carried forward", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ point: { observation_id: "O1" }, progress: { status: "LEFT_OF_ALERT" } }),
    );
    const answers = { observedAt: "2026-07-26T10:00:00Z", phase: "ACTIVE_LABOUR", cervicalDilationCm: 5 };
    const result = await addPartographPoint("S1", answers);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/S1/points",
      answers,
    );
    expect(result.progress.status).toBe("LEFT_OF_ALERT");
  });

  it("addPartographPoint returns the assessment alongside the point (contract §4.3)", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ point: { observation_id: "O2" }, progress: { status: "AT_OR_RIGHT_OF_ACTION" } }),
    );
    const result = await addPartographPoint("S1", { observedAt: "x", phase: "ACTIVE_LABOUR" });
    expect(result.point.observation_id).toBe("O2");
    expect(result.progress.status).toBe("AT_OR_RIGHT_OF_ACTION");
  });

  it("closePartograph uses PATCH, accepting the verb the shell has always sent", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(ok({ session_id: "S1", status: "CLOSED" }));
    await closePartograph("S1", { outcome: "DELIVERED" });
    expect(apiClient.patch).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/S1/close",
      { outcome: "DELIVERED" },
    );
  });
});

describe("CTG — the same 200-vs-502 distinction applies", () => {
  it("treats ctg_active: false as a real answer", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", ctg_active: false }));
    const result = await getActiveCtgSession("P1");
    expect(result).toEqual({ ctgActive: false, patientId: "P1" });
  });

  it("propagates a PCT_UNAVAILABLE failure rather than reporting an empty trace", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(getActiveCtgSession("P1")).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });

  it("openCtgSession posts camelCase identifiers", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ session_id: "S2", patient_id: "P1", status: "ACTIVE" }));
    await openCtgSession({ patientId: "P1" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/maternity/ctg/sessions", {
      patientId: "P1",
      journeyId: undefined,
      encounterId: undefined,
      monitoringMode: undefined,
      deviceId: undefined,
    });
  });

  it("addCtgAnnotation never derives severity — it sends exactly what the clinician chose", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ annotation_id: "A1", category: "LATE_DECELERATION", severity: "ABNORMAL" }),
    );
    const answers = { recordedAt: "2026-07-26T10:00:00Z", channel: "FHR", category: "LATE_DECELERATION", severity: "ABNORMAL" };
    const annotation = await addCtgAnnotation("S2", answers);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/maternity/ctg/sessions/S2/annotations",
      answers,
    );
    expect(annotation.severity).toBe("ABNORMAL");
  });
});
