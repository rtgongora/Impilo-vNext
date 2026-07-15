import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreCommoditiesPanel } from "./TheatreCommoditiesPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreCommoditiesPanel (Lane 2 — implant / instrument-set / controlled-drug)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((u: string) => {
      if (u.endsWith("/implants")) return Promise.resolve({ data: [{ id: "im1", udi: "UDI-9", device_type: "HIP", status: "IMPLANTED" }] });
      return Promise.resolve({ data: [] });
    });
  });

  it("reads the three registers from their real endpoints", async () => {
    render(<TheatreCommoditiesPanel caseId="c-1" />);
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/implants");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/instrument-sets");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/controlled-drugs");
    expect(await screen.findByText("UDI-9")).toBeInTheDocument();
  });

  it("records an implant via the real implants endpoint (UDI required)", async () => {
    post.mockResolvedValue({ data: { udi: "UDI-123", status: "IMPLANTED" } });
    render(<TheatreCommoditiesPanel caseId="c-2" />);
    await screen.findByText(/No instrument sets issued/i);
    fireEvent.change(screen.getByTestId("implant-udi"), { target: { value: "UDI-123" } });
    fireEvent.click(screen.getByText("Record implant"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-2/implants", expect.objectContaining({ udi: "UDI-123" })));
  });

  it("requires a witness before an ADMINISTER controlled-drug entry can be recorded", async () => {
    render(<TheatreCommoditiesPanel caseId="c-3" />);
    await screen.findByText(/No controlled-drug entries/i);
    fireEvent.change(screen.getByTestId("cd-item"), { target: { value: "MORPH-10" } });
    expect((screen.getByText("Record") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByTestId("cd-witness"), { target: { value: "nurse-2" } });
    expect((screen.getByText("Record") as HTMLButtonElement).disabled).toBe(false);
  });
});
