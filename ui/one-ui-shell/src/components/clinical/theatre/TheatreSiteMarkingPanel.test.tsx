import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreSiteMarkingPanel } from "./TheatreSiteMarkingPanel";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));

vi.mock("@/features/body-map/BodyMapField", () => ({
  BodyMapField: ({
    onChange,
    "data-testid": tid,
  }: {
    onChange: (findings: Array<{ regionId: string; laterality?: string; locationCode: { system: string; code: string; display?: string } }>) => void;
    "data-testid"?: string;
  }) => (
    <button
      type="button"
      data-testid={tid}
      onClick={() =>
        onChange([
          {
            regionId: "inguinal-canal-left",
            laterality: "left",
            locationCode: { system: "http://snomed.info/sct", code: "x", display: "Left inguinal canal" },
          },
        ])
      }
    >
      Mark site
    </button>
  ),
}));

describe("TheatreSiteMarkingPanel", () => {
  const onConfirmed = vi.fn();

  beforeEach(() => {
    post.mockReset();
    onConfirmed.mockReset();
  });

  it("shows unconfirmed state when case has no confirmation", () => {
    render(<TheatreSiteMarkingPanel caseId="c-1" onConfirmed={onConfirmed} />);
    expect(screen.getByTestId("site-marking-unconfirmed")).toBeInTheDocument();
    expect(screen.queryByTestId("site-marking-confirmed")).not.toBeInTheDocument();
  });

  it("shows current confirmation from case detail", () => {
    render(
      <TheatreSiteMarkingPanel
        caseId="c-1"
        laterality="LEFT"
        anatomicalSite="Left inguinal canal"
        siteSideConfirmedBy="actor-surgeon"
        siteSideConfirmedAt="2026-07-31T01:00:00Z"
        onConfirmed={onConfirmed}
      />,
    );
    const confirmed = screen.getByTestId("site-marking-confirmed");
    expect(confirmed).toHaveTextContent("Left inguinal canal");
    expect(confirmed).toHaveTextContent("LEFT");
    expect(confirmed).toHaveTextContent("actor-surgeon");
    expect(screen.queryByTestId("site-marking-unconfirmed")).not.toBeInTheDocument();
  });

  it("requires laterality before confirm", async () => {
    render(<TheatreSiteMarkingPanel caseId="c-1" onConfirmed={onConfirmed} />);
    expect(screen.getByTestId("site-marking-confirm")).toBeDisabled();
    await userEvent.selectOptions(screen.getByTestId("site-marking-laterality"), "RIGHT");
    expect(screen.getByTestId("site-marking-confirm")).not.toBeDisabled();
  });

  it("posts laterality and anatomical site from the body map", async () => {
    post.mockResolvedValueOnce({ laterality: "LEFT" });
    render(<TheatreSiteMarkingPanel caseId="c-1" onConfirmed={onConfirmed} />);

    await userEvent.click(screen.getByTestId("site-marking-body-map"));
    // Body-map laterality "left" should prefill the closed vocab selector.
    expect(screen.getByTestId("site-marking-laterality")).toHaveValue("LEFT");
    await userEvent.click(screen.getByTestId("site-marking-confirm"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/site-side", {
        laterality: "LEFT",
        anatomicalSite: expect.stringMatching(/inguinal|Left/i),
      }),
    );
    expect(onConfirmed).toHaveBeenCalled();
    expect(screen.getByTestId("site-marking-success")).toBeInTheDocument();
  });

  it("surfaces a write failure without clearing an existing confirmation", async () => {
    post.mockRejectedValueOnce({ status: 502, error: { message: "inpatient unreachable" } });
    render(
      <TheatreSiteMarkingPanel
        caseId="c-1"
        laterality="LEFT"
        anatomicalSite="Left hip"
        siteSideConfirmedBy="actor-surgeon"
        siteSideConfirmedAt="2026-07-31T01:00:00Z"
        onConfirmed={onConfirmed}
      />,
    );

    await userEvent.selectOptions(screen.getByTestId("site-marking-laterality"), "RIGHT");
    await userEvent.click(screen.getByTestId("site-marking-confirm"));

    await waitFor(() => expect(screen.getByTestId("site-marking-failed")).toBeInTheDocument());
    expect(screen.getByTestId("site-marking-failed")).toHaveTextContent(/inpatient unreachable|NOT confirmed/);
    // Prior confirmation from case detail must still be visible — failure never looks unmarked.
    expect(screen.getByTestId("site-marking-confirmed")).toHaveTextContent("Left hip");
    expect(screen.getByTestId("site-marking-confirmed")).toHaveTextContent("LEFT");
    expect(onConfirmed).not.toHaveBeenCalled();
  });
});
