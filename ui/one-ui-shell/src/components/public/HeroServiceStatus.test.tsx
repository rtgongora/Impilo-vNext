import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HeroServiceStatus, summarise } from "./HeroServiceStatus";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn() },
}));

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

const group = (name: string, state: string, monitored: boolean) => ({
  group: name,
  description: name,
  state,
  monitored,
});

describe("HeroServiceStatus", () => {
  beforeEach(() => get.mockReset());

  /**
   * The estate's real payload today: the feed answers, but nothing behind it is
   * watched. This is the case that tempts a fabricated green dot, so it is the
   * first test — an absence of alarms is not evidence of health.
   */
  it("never claims operational when the live feed monitors nothing", async () => {
    get.mockResolvedValue({
      updatedAt: "2026-08-05T03:57:28Z",
      live: true,
      groups: [
        group("Find care & facility directory", "UNKNOWN", false),
        group("Emergency help", "UNKNOWN", false),
      ],
    });

    render(<HeroServiceStatus />);

    expect(await screen.findByText("Live monitoring not reporting yet")).toBeInTheDocument();
    expect(screen.getByText(/2 citizen service groups listed · none currently monitored/)).toBeInTheDocument();
    expect(screen.queryByText(/operational/i)).not.toBeInTheDocument();
  });

  it("reports operational only when groups are genuinely monitored", async () => {
    get.mockResolvedValue({
      updatedAt: "2026-08-05T03:57:28Z",
      live: true,
      groups: [
        group("Find care", "OPERATIONAL", true),
        group("Emergency help", "OPERATIONAL", true),
        group("Payments", "UNKNOWN", false),
      ],
    });

    render(<HeroServiceStatus />);

    expect(await screen.findByText("All monitored services operational")).toBeInTheDocument();
    // The unmonitored third group is counted honestly, not folded into the green claim.
    expect(screen.getByText(/2 of 3 service groups monitored/)).toBeInTheDocument();
  });

  it("surfaces the worst state rather than the majority", async () => {
    get.mockResolvedValue({
      updatedAt: "2026-08-05T03:57:28Z",
      live: true,
      groups: [
        group("Find care", "OPERATIONAL", true),
        group("Records", "OPERATIONAL", true),
        group("Payments", "DEGRADED", true),
      ],
    });

    render(<HeroServiceStatus />);

    expect(await screen.findByText("1 of 3 services degraded")).toBeInTheDocument();
    expect(screen.queryByText(/all monitored services operational/i)).not.toBeInTheDocument();
  });

  /**
   * Asserted on the pure summariser rather than through a render.
   *
   * Measured harness landmine: in a file whose `beforeEach` calls `mockClear()`/
   * `mockReset()`, a rejection raised from a MOUNT effect surfaces as an unhandled
   * error and fails the test before any assertion runs — an empty `beforeEach` in the
   * same file passes, and the same render passes in a file without that hook. It is
   * the reset-in-hook combination, not this component: PublicNoticesBoard.test.tsx
   * carries the same hook and sidesteps it by failing on a click instead. There is no
   * click here, so the unavailable path is asserted where its logic actually lives.
   */
  it("distinguishes an unreachable feed from healthy services", () => {
    const summary = summarise(null, true);

    expect(summary.phase).toBe("unavailable");
    expect(summary.label).toBe("Service status unavailable");
    expect(summary.detail).toMatch(/does not mean services are down/i);
    expect(summary.label).not.toMatch(/operational/i);
    expect(summary.dot).not.toMatch(/emerald|green/);
  });

  it("treats a feed that answered-but-monitors-nothing as distinct from a failure", () => {
    const answered = summarise(
      { updatedAt: "2026-08-05T03:57:28Z", live: true, groups: [group("Find care", "UNKNOWN", false)] },
      false,
    );
    const failed = summarise(null, true);

    expect(answered.phase).toBe("unmonitored");
    expect(failed.phase).toBe("unavailable");
    expect(answered.label).not.toBe(failed.label);
  });

  it("always links to the full status board", async () => {
    get.mockResolvedValue({ updatedAt: "2026-08-05T03:57:28Z", live: true, groups: [] });

    render(<HeroServiceStatus />);

    expect(await screen.findByTestId("hero-service-status")).toHaveAttribute("href", "/status");
  });
});
