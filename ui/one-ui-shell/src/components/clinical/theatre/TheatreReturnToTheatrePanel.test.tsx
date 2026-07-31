import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreReturnToTheatrePanel } from "./TheatreReturnToTheatrePanel";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));

describe("TheatreReturnToTheatrePanel", () => {
  const onRecorded = vi.fn();

  beforeEach(() => {
    post.mockReset();
    onRecorded.mockReset();
  });

  it("is hidden when the case is not in a returnable status", () => {
    const { container } = render(
      <TheatreReturnToTheatrePanel caseId="c-1" status="BOOKED" returns={[]} onRecorded={onRecorded} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("lists prior returns from case detail", () => {
    render(
      <TheatreReturnToTheatrePanel
        caseId="c-1"
        status="PACU"
        returns={[
          {
            id: "r-1",
            seq: 1,
            complication_category: "HAEMORRHAGE",
            reason: "Bleeding at the anastomosis",
            planned: false,
            returned_by: "dr-a",
          },
        ]}
        onRecorded={onRecorded}
      />,
    );
    const list = screen.getByTestId("return-to-theatre-list");
    expect(list).toHaveTextContent("HAEMORRHAGE");
    expect(list).toHaveTextContent("Bleeding at the anastomosis");
    expect(list).toHaveTextContent("unplanned");
  });

  it("requires reason and category before submit", async () => {
    render(
      <TheatreReturnToTheatrePanel caseId="c-1" status="PACU" returns={[]} onRecorded={onRecorded} />,
    );
    expect(screen.getByTestId("return-to-theatre-submit")).toBeDisabled();
    await userEvent.type(screen.getByTestId("return-to-theatre-reason"), "Sepsis needing washout");
    expect(screen.getByTestId("return-to-theatre-submit")).toBeDisabled();
    await userEvent.selectOptions(screen.getByTestId("return-to-theatre-category"), "SEPSIS");
    expect(screen.getByTestId("return-to-theatre-submit")).not.toBeDisabled();
  });

  it("posts reason, category and planned flag", async () => {
    post.mockResolvedValueOnce({ status: "READY_FOR_THEATRE" });
    render(
      <TheatreReturnToTheatrePanel caseId="c-1" status="RECOVERED" returns={[]} onRecorded={onRecorded} />,
    );
    await userEvent.type(screen.getByTestId("return-to-theatre-reason"), "Anastomotic leak");
    await userEvent.selectOptions(screen.getByTestId("return-to-theatre-category"), "ANASTOMOTIC_LEAK");
    await userEvent.click(screen.getByTestId("return-to-theatre-submit"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/return-to-theatre", {
        reason: "Anastomotic leak",
        complicationCategory: "ANASTOMOTIC_LEAK",
        planned: false,
      }),
    );
    expect(onRecorded).toHaveBeenCalled();
  });

  it("locks planned second look to the planned flag", async () => {
    post.mockResolvedValueOnce({});
    render(
      <TheatreReturnToTheatrePanel caseId="c-1" status="PACU" returns={[]} onRecorded={onRecorded} />,
    );
    await userEvent.type(screen.getByTestId("return-to-theatre-reason"), "Staged washout");
    await userEvent.click(screen.getByTestId("return-to-theatre-planned"));
    expect(screen.getByTestId("return-to-theatre-category")).toHaveValue("PLANNED_SECOND_LOOK");
    await userEvent.click(screen.getByTestId("return-to-theatre-submit"));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/return-to-theatre", {
        reason: "Staged washout",
        complicationCategory: "PLANNED_SECOND_LOOK",
        planned: true,
      }),
    );
  });

  it("surfaces a write failure without claiming success", async () => {
    post.mockRejectedValueOnce({ status: 502, error: { message: "inpatient unreachable" } });
    render(
      <TheatreReturnToTheatrePanel caseId="c-1" status="PACU" returns={[]} onRecorded={onRecorded} />,
    );
    await userEvent.type(screen.getByTestId("return-to-theatre-reason"), "Bleeding");
    await userEvent.selectOptions(screen.getByTestId("return-to-theatre-category"), "HAEMORRHAGE");
    await userEvent.click(screen.getByTestId("return-to-theatre-submit"));

    await waitFor(() => expect(screen.getByTestId("return-to-theatre-failed")).toBeInTheDocument());
    expect(screen.getByTestId("return-to-theatre-failed")).toHaveTextContent(/inpatient unreachable|NOT recorded/);
    expect(onRecorded).not.toHaveBeenCalled();
  });
});
