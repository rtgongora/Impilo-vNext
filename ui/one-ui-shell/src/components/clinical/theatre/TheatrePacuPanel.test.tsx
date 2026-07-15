import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatrePacuPanel } from "./TheatrePacuPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

function mockGets(readiness: Record<string, unknown>, observations: unknown[] = []) {
  get.mockImplementation((u: string) => {
    if (u.endsWith("/pacu/readiness")) return Promise.resolve({ data: readiness });
    if (u.endsWith("/pacu/observations")) return Promise.resolve({ data: observations });
    return Promise.resolve({ data: [] });
  });
}

describe("TheatrePacuPanel (Aldrete recovery depth + discharge-readiness gate)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("shows the readiness gate BLOCKED when the band is not LOW", async () => {
    mockGets({ ready: false, band: "MODERATE", aldrete_score: 7, interpretation: "Continue PACU monitoring" });
    render(<TheatrePacuPanel caseId="c-1" />);
    expect(await screen.findByTestId("pacu-readiness-blocked")).toHaveTextContent(/MODERATE/i);
  });

  it("shows the readiness gate READY when the band is LOW (Aldrete ≥ 9)", async () => {
    mockGets({ ready: true, band: "LOW", aldrete_score: 9, interpretation: "Ready for PACU discharge to ward" });
    render(<TheatrePacuPanel caseId="c-1" />);
    expect(await screen.findByTestId("pacu-readiness-ready")).toHaveTextContent(/LOW/i);
  });

  it("records an observation carrying the Aldrete score against the real endpoint", async () => {
    mockGets({ band: "UNSCORED", ready: false }, []);
    post.mockResolvedValue({ data: { aldrete_score: 8 } });
    render(<TheatrePacuPanel caseId="c-7" />);
    await screen.findByText(/No PACU observations recorded/i);
    fireEvent.change(screen.getByTestId("pacu-aldrete"), { target: { value: "8" } });
    fireEvent.click(screen.getByTestId("pacu-record"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-7/pacu/observations", expect.objectContaining({ aldreteScore: 8 })),
    );
  });

  it("sends the readiness override flag on a gated discharge when checked", async () => {
    mockGets({ band: "MODERATE", ready: false }, []);
    post.mockResolvedValue({ data: {} });
    render(<TheatrePacuPanel caseId="c-9" />);
    await screen.findByTestId("pacu-readiness-blocked");
    fireEvent.click(screen.getByTestId("pacu-override"));
    fireEvent.click(screen.getByTestId("pacu-discharge"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-9/pacu/discharge", expect.objectContaining({ destination: "WARD", readinessOverride: true })),
    );
  });
});
