import { describe, expect, it } from "vitest";

import {
  humanise,
  orderAlerts,
  severityRank,
  standingOf,
  tallyEpisodeStates,
} from "../boardModel";

describe("severity ordering", () => {
  it("sorts an unrecognised severity last rather than dropping the row", () => {
    // pct is free to add a severity without redeploying the shell. The worst outcome must be a row
    // in an unhelpful position, never a row that vanished from a safety board.
    expect(severityRank("SOMETHING_NEW")).toBeGreaterThan(severityRank("LOW"));
    expect(severityRank(null)).toBeGreaterThan(severityRank("SOMETHING_NEW"));
  });

  it("puts an old unacknowledged critical alert at the top", () => {
    const ordered = orderAlerts([
      { alert_id: "c", severity: "LOW", status: "OPEN", raised_at: "2026-07-30T09:00:00Z" },
      { alert_id: "a", severity: "CRITICAL", status: "OPEN", raised_at: "2026-07-30T08:00:00Z" },
      { alert_id: "b", severity: "CRITICAL", status: "OPEN", raised_at: "2026-07-30T08:30:00Z" },
    ]);
    expect(ordered.map((a) => a.alert_id)).toEqual(["a", "b", "c"]);
  });

  it("sinks closed alerts below every open one regardless of severity", () => {
    const ordered = orderAlerts([
      { alert_id: "closed-critical", severity: "CRITICAL", status: "CLOSED", raised_at: "2026-07-30T07:00:00Z" },
      { alert_id: "open-low", severity: "LOW", status: "OPEN", raised_at: "2026-07-30T09:00:00Z" },
    ]);
    expect(ordered[0].alert_id).toBe("open-low");
  });
});

describe("alert standing", () => {
  it("counts acknowledged, responded and closed separately", () => {
    // Collapsing these into "handled" is what lets an alert someone merely glanced at look
    // identical to one that was actually resolved.
    const standing = standingOf([
      { alert_id: "1", status: "OPEN" },
      { alert_id: "2", status: "OPEN", acknowledged_at: "2026-07-30T08:00:00Z" },
      {
        alert_id: "3",
        status: "OPEN",
        acknowledged_at: "2026-07-30T08:00:00Z",
        responded_at: "2026-07-30T08:05:00Z",
      },
      { alert_id: "4", status: "CLOSED", closed_at: "2026-07-30T08:30:00Z" },
    ]);

    expect(standing).toEqual({
      unacknowledged: 1,
      acknowledgedNotResponded: 1,
      respondedStillOpen: 1,
      closed: 1,
    });
  });
});

describe("episode state tallies", () => {
  it("prefers the server's own summary", () => {
    const result = tallyEpisodeStates({
      summary: { episodes_by_state: { OPEN_UNTRIAGED: 3, OPEN_IN_TREATMENT: 5 } },
      episodes: [{ episode_id: "1", state: "OPEN_UNTRIAGED" }],
    });
    expect(result.source).toBe("server-summary");
    expect(result.tallies[0]).toEqual({ state: "OPEN_IN_TREATMENT", count: 5 });
  });

  it("says when a count came from the client board, not the facility", () => {
    // A count derived from a partially-loaded board is not the facility's true count and the caller
    // has to be able to say so on screen.
    const result = tallyEpisodeStates({
      summary: null,
      episodes: [
        { episode_id: "1", state: "OPEN_UNTRIAGED" },
        { episode_id: "2", state: "OPEN_UNTRIAGED" },
      ],
    });
    expect(result.source).toBe("client-board");
    expect(result.tallies).toEqual([{ state: "OPEN_UNTRIAGED", count: 2 }]);
  });

  it("reports no source at all rather than an empty count that reads as zero episodes", () => {
    expect(tallyEpisodeStates({ summary: null, episodes: [] }).source).toBe("none");
  });
});

describe("humanise", () => {
  it("reformats without inventing vocabulary", () => {
    expect(humanise("OPEN_AWAITING_ACCEPTANCE")).toBe("Open awaiting acceptance");
    expect(humanise(null)).toBe("—");
  });
});
