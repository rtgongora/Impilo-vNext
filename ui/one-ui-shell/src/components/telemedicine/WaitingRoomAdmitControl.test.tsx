import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WaitingRoomAdmitControl } from "./WaitingRoomAdmitControl";

const { admitMutate, denyMutate, waitingState } = vi.hoisted(() => ({
  admitMutate: vi.fn(),
  denyMutate: vi.fn(),
  waitingState: {
    current: { data: { data: { waiting: [] as Array<Record<string, string>> } }, isLoading: false },
  },
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineWaitingRoom: () => waitingState.current,
  useAdmitTelemedicineParticipant: () => ({ mutate: admitMutate, isPending: false }),
  useDenyTelemedicineParticipant: () => ({ mutate: denyMutate, isPending: false }),
}));

describe("WaitingRoomAdmitControl", () => {
  beforeEach(() => {
    admitMutate.mockReset();
    denyMutate.mockReset();
    waitingState.current = { data: { data: { waiting: [] } }, isLoading: false };
  });

  it("shows an empty waiting room with a zero badge", () => {
    render(<WaitingRoomAdmitControl sessionId="s-1" />);

    expect(screen.getByTestId("waiting-room-admit-control")).toBeInTheDocument();
    expect(screen.getByTestId("waiting-room-count")).toHaveTextContent("0");
    expect(screen.getByText(/no one is waiting to join/i)).toBeInTheDocument();
  });

  it("lists waiting participants and admits by identity", async () => {
    const user = userEvent.setup();
    waitingState.current = {
      data: {
        data: {
          waiting: [
            {
              identity: "pat-1",
              displayName: "Tatenda Moyo",
              role: "PATIENT",
              state: "WAITING",
              requestedAt: "2026-07-05T08:00:00.000Z",
            },
          ],
        },
      },
      isLoading: false,
    };

    render(<WaitingRoomAdmitControl sessionId="s-1" />);

    expect(screen.getByTestId("waiting-room-count")).toHaveTextContent("1");
    expect(screen.getByTestId("waiting-room-entry")).toHaveTextContent("Tatenda Moyo");

    await user.click(screen.getByRole("button", { name: /admit/i }));
    expect(admitMutate).toHaveBeenCalledTimes(1);
    expect(admitMutate.mock.calls[0][0]).toEqual({ identity: "pat-1" });
  });

  it("denies by identity with a reason", async () => {
    const user = userEvent.setup();
    waitingState.current = {
      data: {
        data: {
          waiting: [
            {
              identity: "pat-2",
              displayName: "Care Partner",
              role: "CAREGIVER",
              state: "WAITING",
              requestedAt: "2026-07-05T08:05:00.000Z",
            },
          ],
        },
      },
      isLoading: false,
    };

    render(<WaitingRoomAdmitControl sessionId="s-1" />);

    await user.click(screen.getByRole("button", { name: /deny/i }));
    expect(denyMutate).toHaveBeenCalledTimes(1);
    expect(denyMutate.mock.calls[0][0]).toEqual({
      identity: "pat-2",
      reason: "Denied by consulting provider",
    });
  });
});
