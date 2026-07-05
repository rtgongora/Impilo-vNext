import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import VirtualHospitalDirectoryPage from "./page";
import { ALL_VIRTUAL_HOSPITALS } from "@/lib/telemedicine/virtual-hospitals";

describe("/work/telemedicine/virtual-hospitals directory", () => {
  it("lists every configured virtual hospital", () => {
    render(<VirtualHospitalDirectoryPage />);
    for (const name of [
      "National Telemedicine Hospital",
      "Virtual Specialist Hospital",
      "Virtual Maternal and Newborn Unit",
      "Virtual Mental Health Unit",
      "Harare Provincial Virtual Hospital",
      "Midlands Provincial Virtual Hospital",
    ]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
  });

  it("filters by level", async () => {
    const user = userEvent.setup();
    render(<VirtualHospitalDirectoryPage />);
    await user.click(screen.getByRole("button", { name: "Provincial" }));
    expect(screen.getByText("Harare Provincial Virtual Hospital")).toBeInTheDocument();
    expect(screen.queryByText("National Telemedicine Hospital")).not.toBeInTheDocument();
  });

  it("shows honest substrate badges and never an operational claim", () => {
    render(<VirtualHospitalDirectoryPage />);
    expect(screen.getAllByText("Routable today via teleconsult routing").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Configured — awaiting backend substrate").length).toBeGreaterThan(0);
    expect(screen.queryAllByText(/^Operational$/)).toHaveLength(0);
  });

  it("links each card to its detail page", () => {
    render(<VirtualHospitalDirectoryPage />);
    const first = ALL_VIRTUAL_HOSPITALS[0];
    const card = screen.getByText(first.name).closest("a");
    expect(card).toHaveAttribute("href", `/work/telemedicine/virtual-hospitals/${first.id}`);
  });
});
