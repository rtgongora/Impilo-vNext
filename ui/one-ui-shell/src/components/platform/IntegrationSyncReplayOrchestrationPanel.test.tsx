import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IntegrationSyncReplayOrchestrationPanel } from "./IntegrationSyncReplayOrchestrationPanel";

const replayMutate = vi.fn();

type RoutesMock = {
  data: { data: { id: string }[] } | undefined;
  isLoading: boolean;
  isError: boolean;
};

type DeadLettersMock = {
  data: { data: { content: { id: string; routeId: string }[] } } | undefined;
  isLoading: boolean;
  isError: boolean;
};

const hubMocks = vi.hoisted(() => {
  const state: { routes: RoutesMock; dead: DeadLettersMock } = {
    routes: {
      data: { data: [{ id: "r1" }] },
      isLoading: false,
      isError: false,
    },
    dead: {
      data: { data: { content: [{ id: "dl-9", routeId: "fhir-in" }] } },
      isLoading: false,
      isError: false,
    },
  };
  return state;
});

vi.mock("@/hooks/queries/useIntegrationHub", () => ({
  useIntegrationHubRoutes: () => hubMocks.routes,
  useIntegrationHubDeadLetters: () => hubMocks.dead,
  useReplayIntegrationDeadLetter: () => ({ mutate: replayMutate, isPending: false }),
}));

describe("IntegrationSyncReplayOrchestrationPanel", () => {
  beforeEach(() => {
    replayMutate.mockReset();
    hubMocks.routes = { data: { data: [{ id: "r1" }] }, isLoading: false, isError: false };
    hubMocks.dead = {
      data: { data: { content: [{ id: "dl-9", routeId: "fhir-in" }] } },
      isLoading: false,
      isError: false,
    };
  });

  it("shows replay control for dead letters", async () => {
    const user = userEvent.setup();
    render(<IntegrationSyncReplayOrchestrationPanel />);
    expect(screen.getByTestId("integration-sync-replay-orchestration-panel")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Replay/i }));
    expect(replayMutate).toHaveBeenCalledWith("dl-9", expect.any(Object));
  });

  it("renders honest empty hub message when no routes or dead letters", () => {
    hubMocks.routes = { data: { data: [] }, isLoading: false, isError: false };
    hubMocks.dead = {
      data: { data: { content: [] } },
      isLoading: false,
      isError: false,
    };
    render(<IntegrationSyncReplayOrchestrationPanel />);
    expect(screen.getByTestId("integration-deadletter-empty")).toHaveTextContent(/Empty hub/i);
  });

  it("renders unreachable message when both feeds fail", () => {
    hubMocks.routes = { data: undefined, isLoading: false, isError: true };
    hubMocks.dead = { data: undefined, isLoading: false, isError: true };
    render(<IntegrationSyncReplayOrchestrationPanel />);
    expect(screen.getByTestId("integration-deadletter-empty")).toHaveTextContent(/unreachable/i);
  });
});
