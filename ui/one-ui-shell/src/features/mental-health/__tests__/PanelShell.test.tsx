import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PanelShell } from "../PanelShell";

/**
 * The whole point of this component is that a clinician can tell three situations apart. These
 * tests fail if any two of them start looking the same.
 */
describe("PanelShell", () => {
  const base = {
    title: "Restraint",
    subject: "this patient's restraint events",
    isLoading: false,
    isError: false,
    isEmpty: false,
    emptyMessage: "No restraint has been recorded for this patient.",
    "data-testid": "panel",
  };

  it("says nothing was recorded when the read succeeded and returned nothing", () => {
    render(
      <PanelShell {...base} isEmpty>
        <p>rows</p>
      </PanelShell>,
    );

    expect(screen.getByTestId("panel-empty")).toHaveTextContent(
      "No restraint has been recorded for this patient.",
    );
    expect(screen.queryByTestId("panel-unreadable")).toBeNull();
  });

  it("says the record could not be read when the read failed, and does not claim it is empty", () => {
    render(
      <PanelShell {...base} isError isEmpty error={{ error: "mh_unavailable", message: "no route to host" }}>
        <p>rows</p>
      </PanelShell>,
    );

    // isEmpty is also true here — an errored query has no rows — and the failure must still win.
    expect(screen.getByTestId("panel-unreadable")).toHaveTextContent("no route to host");
    expect(screen.queryByTestId("panel-empty")).toBeNull();
  });

  it("quotes the service's own words rather than a generic failure", () => {
    render(
      <PanelShell
        {...base}
        isError
        isEmpty
        error={{ error: { code: "HTTP_ERROR", message: "an open involuntary episode already exists" } }}
      />,
    );

    expect(screen.getByTestId("panel-unreadable")).toHaveTextContent(
      "an open involuntary episode already exists",
    );
  });

  it("names what could not be read when the service said nothing quotable", () => {
    render(<PanelShell {...base} isError isEmpty error={new Error("boom")} />);

    expect(screen.getByTestId("panel-unreadable")).toHaveTextContent(
      "Do not treat this as an absence of this patient's restraint events",
    );
  });

  it("keeps the recording controls available even when the read failed", () => {
    // A clinician who cannot read the history can still be in the middle of an episode of
    // restraint. Hiding the form behind a failed GET would make a read outage a write outage.
    render(
      <PanelShell {...base} isError isEmpty error={new Error("boom")} actions={<button>Record</button>} />,
    );

    expect(screen.getByRole("button", { name: "Record" })).toBeInTheDocument();
  });

  it("shows loading distinctly from both empty and unreadable", () => {
    render(<PanelShell {...base} isLoading isEmpty />);

    expect(screen.getByTestId("panel-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("panel-empty")).toBeNull();
    expect(screen.queryByTestId("panel-unreadable")).toBeNull();
  });
});
