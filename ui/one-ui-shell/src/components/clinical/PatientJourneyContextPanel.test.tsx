import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PatientJourneyContextPanel } from "./PatientJourneyContextPanel";

/**
 * experience-bff empty-200 honesty sweep — UI half.
 *
 * The panel's empty state describes itself as "an honest empty state — not a simulated journey".
 * That sentence is only true when all six sources answered. These tests pin the difference
 * between a journey that is genuinely empty and one that could not be read.
 */

interface QueryLike {
  data: { data: unknown[] } | undefined;
  isError: boolean;
}

const ok: QueryLike = { data: { data: [] }, isError: false };
const failed: QueryLike = { data: undefined, isError: true };

const encounters = vi.fn((): QueryLike => ok);
const referrals = vi.fn((): QueryLike => ok);
const telemedicine = vi.fn((): QueryLike => ok);
const admissions = vi.fn((): QueryLike => ok);
const queue = vi.fn((): QueryLike => ok);
const labOrders = vi.fn((): QueryLike => ok);

vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => encounters() }));
vi.mock("@/hooks/queries/useReferrals", () => ({ useReferrals: () => referrals() }));
vi.mock("@/hooks/queries/useTelemedicine", () => ({ useTelemedicineSessions: () => telemedicine() }));
vi.mock("@/hooks/queries/useInpatient", () => ({ useAdmissions: () => admissions() }));
vi.mock("@/hooks/queries/useQueue", () => ({ useQueueEntries: () => queue() }));
vi.mock("@/hooks/queries/useLabOrders", () => ({ useLabOrders: () => labOrders() }));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "fac-1", name: "Central Clinic" } }),
}));
vi.mock("@/hooks/useWorkspaceStore", () => ({
  useWorkspaceStore: (selector: (s: { workspace: null }) => unknown) => selector({ workspace: null }),
}));
vi.mock("@/hooks/useShiftStore", () => ({ useShiftStore: () => ({ shift: null }) }));

describe("PatientJourneyContextPanel honesty", () => {
  it("claims an honest empty journey only when every source answered", () => {
    render(<PatientJourneyContextPanel patientId="p-1" />);

    expect(screen.getByText(/This is an honest empty state/i)).toBeInTheDocument();
  });

  it("names the unreadable source instead of asserting six absences", () => {
    encounters.mockReturnValueOnce(failed);
    labOrders.mockReturnValueOnce(failed);

    render(<PatientJourneyContextPanel patientId="p-1" />);

    expect(screen.queryByText(/This is an honest empty state/i)).not.toBeInTheDocument();
    const notice = screen.getByText(/Could not read:/i);
    expect(notice).toHaveTextContent("encounters");
    expect(notice).toHaveTextContent("lab orders");
  });

  it("marks the compact strip incomplete rather than recommending a next action silently", () => {
    queue.mockReturnValueOnce(failed);

    render(<PatientJourneyContextPanel patientId="p-1" variant="compact" />);

    expect(screen.getByText(/Journey incomplete/i)).toBeInTheDocument();
  });
});
