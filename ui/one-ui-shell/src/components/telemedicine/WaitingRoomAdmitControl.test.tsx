import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WaitingRoomAdmitControl } from "./WaitingRoomAdmitControl";

type PoolContext = {
  poolId: string;
  depth: number;
  oldestWaitingMinutes: number | null;
  estimatedWaitMinutes: number;
};

type WaitingRoomState = {
  data: {
    data: {
      waiting: Array<Record<string, string>>;
      poolContext?: PoolContext | null;
    };
  };
  isLoading: boolean;
};

const { admitMutate, denyMutate, waitingState } = vi.hoisted(() => ({
  admitMutate: vi.fn(),
  denyMutate: vi.fn(),
  waitingState: {
    current: { data: { data: { waiting: [] } }, isLoading: false } as WaitingRoomState,
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

  it("renders the pool-queue context banner when poolContext is present", () => {
    waitingState.current = {
      data: {
        data: {
          waiting: [],
          poolContext: {
            poolId: "pool-cardio",
            depth: 4,
            oldestWaitingMinutes: 12,
            estimatedWaitMinutes: 7,
          },
        },
      },
      isLoading: false,
    };

    render(<WaitingRoomAdmitControl sessionId="s-pool" />);

    const banner = screen.getByTestId("waiting-room-pool-context");
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent("4 waiting");
    expect(banner).toHaveTextContent("~7 min est. wait");
    expect(banner).toHaveTextContent("longest wait 12 min");
  });

  it("omits the longest-wait clause when oldestWaitingMinutes is null", () => {
    waitingState.current = {
      data: {
        data: {
          waiting: [],
          poolContext: {
            poolId: "pool-cardio",
            depth: 2,
            oldestWaitingMinutes: null,
            estimatedWaitMinutes: 3,
          },
        },
      },
      isLoading: false,
    };

    render(<WaitingRoomAdmitControl sessionId="s-pool" />);

    const banner = screen.getByTestId("waiting-room-pool-context");
    expect(banner).toHaveTextContent("2 waiting");
    expect(banner).not.toHaveTextContent("longest wait");
  });

  it("does not render the pool-context banner for non-pool sessions", () => {
    waitingState.current = { data: { data: { waiting: [] } }, isLoading: false };

    render(<WaitingRoomAdmitControl sessionId="s-1" />);

    expect(screen.queryByTestId("waiting-room-pool-context")).not.toBeInTheDocument();
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
