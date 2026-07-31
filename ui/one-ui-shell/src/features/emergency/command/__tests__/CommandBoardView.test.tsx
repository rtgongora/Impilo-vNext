import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { CommandBoardView } from "../CommandBoardView";
import type { EmergencyCommandBoard } from "@/hooks/queries/useEmergencyEpisode";

const noop = () => {};

function board(overrides: Partial<EmergencyCommandBoard> = {}): EmergencyCommandBoard {
  return {
    items: {
      summary: { episodes_by_state: { OPEN_UNTRIAGED: 2 } },
      alerts: [],
      episodes: [],
    },
    failures: [],
    meta: { degraded: false, as_of: "2026-07-30T10:00:00.000Z" },
    ...overrides,
  };
}

describe("CommandBoardView", () => {
  it("renders the rest of the board when one source is blind, and names that source", () => {
    render(
      <CommandBoardView
        board={board({
          items: { summary: { episodes_by_state: { OPEN_UNTRIAGED: 2 } }, episodes: [] },
          failures: [
            {
              source: "alerts",
              error: "emergency_alerts_unavailable",
              message:
                "Emergency alerts could not be retrieved. Do not treat this as an absence of emergency alerts.",
            },
          ],
          meta: { degraded: true },
        })}
        isLoading={false}
        isError={false}
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );

    expect(screen.getByTestId("command-board")).toBeTruthy();
    expect(screen.getByTestId("command-board-degraded")).toBeTruthy();
    expect(screen.getByTestId("alert-queue-blind").textContent).toContain(
      "Do not treat this as an absence of emergency alerts",
    );
  });

  it("never shows a zero for a source it could not read", () => {
    // "0 critical alerts" and "the alert service did not answer" are the same pixels on a tile that
    // only knows how to display a number. On a safety board that difference is the whole point.
    render(
      <CommandBoardView
        board={board({
          items: { summary: { episodes_by_state: {} }, episodes: [] },
          failures: [
            { source: "alerts", error: "emergency_alerts_unavailable", message: "could not be read" },
          ],
          meta: { degraded: true },
        })}
        isLoading={false}
        isError={false}
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );

    const tile = screen.getByTestId("command-tile-Alert standing");
    expect(tile.textContent).not.toMatch(/\b0\b/);
    expect(screen.getByTestId("command-tile-Alert standing-blind")).toBeTruthy();
  });

  it("distinguishes a genuinely empty alert queue from an unreadable one", () => {
    render(
      <CommandBoardView
        board={board()}
        isLoading={false}
        isError={false}
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );
    expect(screen.getByTestId("alert-queue-empty").textContent).toContain(
      "No emergency alerts have been raised",
    );
    expect(screen.queryByTestId("alert-queue-blind")).toBeNull();
  });

  it("says the whole board is unreadable rather than rendering an empty one", () => {
    render(
      <CommandBoardView
        board={undefined}
        isLoading={false}
        isError
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );
    expect(screen.getByTestId("command-board-error").textContent).toContain(
      "Do not treat this as an absence of emergency activity",
    );
  });

  it("offers acknowledge, respond and close as three separate acts", () => {
    const onAcknowledge = vi.fn();
    const onClose = vi.fn();
    render(
      <CommandBoardView
        board={board({
          items: {
            summary: { episodes_by_state: {} },
            episodes: [],
            alerts: [
              {
                alert_id: "a-1",
                alert_type: "ACCEPTANCE_OVERDUE",
                severity: "CRITICAL",
                status: "OPEN",
                raised_at: "2026-07-30T09:00:00.000Z",
              },
            ],
          },
        })}
        isLoading={false}
        isError={false}
        onAcknowledge={onAcknowledge}
        onRespond={noop}
        onClose={onClose}
      />,
    );

    fireEvent.click(screen.getByTestId("alert-acknowledge"));
    expect(onAcknowledge).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByTestId("alert-close"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("will not re-acknowledge an alert already acknowledged, but still offers close", () => {
    render(
      <CommandBoardView
        board={board({
          items: {
            summary: { episodes_by_state: {} },
            episodes: [],
            alerts: [
              {
                alert_id: "a-2",
                alert_type: "HANDOVER_EXPIRED",
                severity: "HIGH",
                status: "OPEN",
                raised_at: "2026-07-30T09:00:00.000Z",
                acknowledged_at: "2026-07-30T09:05:00.000Z",
              },
            ],
          },
        })}
        isLoading={false}
        isError={false}
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );

    expect(screen.getByTestId("alert-acknowledge").hasAttribute("disabled")).toBe(true);
    expect(screen.getByTestId("alert-close").hasAttribute("disabled")).toBe(false);
  });

  it("says when a state count came from the loaded board rather than the facility summary", () => {
    render(
      <CommandBoardView
        board={board({
          items: {
            episodes: [{ episode_id: "e-1", state: "OPEN_UNTRIAGED" }],
            alerts: [],
          },
        })}
        isLoading={false}
        isError={false}
        onAcknowledge={noop}
        onRespond={noop}
        onClose={noop}
      />,
    );
    expect(screen.getByTestId("command-tally-source")).toBeTruthy();
  });
});
