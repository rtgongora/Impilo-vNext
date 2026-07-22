import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TeleconsultPriorConsultsPanel } from "./TeleconsultPriorConsultsPanel";
import type { TelemedicineSession } from "@/hooks/queries/useTelemedicine";

const { sessionsState } = vi.hoisted(() => ({
  sessionsState: {
    current: {
      data: { data: [] as TelemedicineSession[] },
      isLoading: false,
      isError: false,
    },
  },
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSessions: () => sessionsState.current,
}));

function session(id: string, overrides: Partial<TelemedicineSession["attributes"]> = {}): TelemedicineSession {
  return {
    id,
    type: "TelemedicineSession",
    attributes: {
      encounter_id: null,
      patient_id: "CPID-1",
      provider_id: null,
      facility_id: null,
      session_type: "TELECONSULT",
      status: "CLOSED",
      room_url: null,
      scheduled_at: null,
      started_at: "2026-01-01T10:00:00Z",
      ended_at: null,
      duration_seconds: null,
      notes: null,
      referral_id: null,
      created_at: "2026-01-01T09:00:00Z",
      updated_at: "2026-01-01T11:00:00Z",
      ...overrides,
    },
  };
}

describe("TeleconsultPriorConsultsPanel", () => {
  beforeEach(() => {
    sessionsState.current = { data: { data: [] }, isLoading: false, isError: false };
  });

  it("renders the empty state honestly", () => {
    render(<TeleconsultPriorConsultsPanel patientCpid="CPID-1" currentSessionId="s-now" />);
    expect(screen.getByTestId("teleconsult-prior-empty")).toBeInTheDocument();
  });

  it("excludes the current session and lists prior consults", () => {
    sessionsState.current.data.data = [
      session("s-now"),
      session("s-old", { status: "RESPONDED" }),
    ];
    render(<TeleconsultPriorConsultsPanel patientCpid="CPID-1" currentSessionId="s-now" />);
    const list = screen.getByTestId("teleconsult-prior-list");
    expect(list).toHaveTextContent("RESPONDED");
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
  });

  it("shows the error state", () => {
    sessionsState.current = { data: { data: [] }, isLoading: false, isError: true };
    render(<TeleconsultPriorConsultsPanel patientCpid="CPID-1" />);
    expect(screen.getByTestId("teleconsult-prior-error")).toBeInTheDocument();
  });
});
