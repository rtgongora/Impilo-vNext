import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DaidzaiCommandPage from "./page";

const { get, post, searchParams } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  searchParams: { value: new URLSearchParams() },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParams.value,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("DaidzaiCommandPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    searchParams.value = new URLSearchParams();
  });

  it("renders real incidents from the BFF with triage category", async () => {
    get.mockResolvedValueOnce([
      {
        id: "inc-1",
        incidentReference: "INC-20260630-AAA",
        incidentType: "INDIVIDUAL",
        emergencyCategory: "CARDIAC",
        severity: "CRITICAL",
        triageCategory: "RED",
        status: "TRIAGED",
      },
    ]);
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText("INC-20260630-AAA")).toBeInTheDocument();
    });
    expect(screen.getByText("RED")).toBeInTheDocument();
    // Real nav links, not dead buttons
    expect(screen.getByRole("link", { name: "Dispatch" })).toHaveAttribute(
      "href",
      "/work/daidzai/dispatch"
    );
    expect(screen.getByRole("link", { name: "Disasters" })).toHaveAttribute(
      "href",
      "/work/daidzai/disasters"
    );
    expect(get).toHaveBeenCalledWith("/internal/v1/daidzai/incidents");
  });

  it("shows an honest empty state, not a fake dashboard", async () => {
    get.mockResolvedValueOnce([]);
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText(/No active emergency incidents/)).toBeInTheDocument();
    });
  });

  it("surfaces a real error instead of hiding it", async () => {
    get.mockRejectedValueOnce({ status: 500 });
    render(<DaidzaiCommandPage />);
    await waitFor(() => {
      expect(screen.getByText(/Could not load incidents/)).toBeInTheDocument();
    });
  });

  it("shows the Nhume escalation card and opens a real incident with link-back", async () => {
    searchParams.value = new URLSearchParams(
      "source=nhume&delivery=d-123&ref=NHM-42&priority=EMERGENCY",
    );
    get.mockResolvedValue([]);
    post.mockResolvedValueOnce({ id: "inc-9", incidentReference: "INC-9" });
    post.mockResolvedValueOnce({});

    const { default: userEvent } = await import("@testing-library/user-event");
    render(<DaidzaiCommandPage />);

    expect(await screen.findByText(/Escalation from Nhume delivery NHM-42/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Open incident/ }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/daidzai/incidents",
        expect.objectContaining({ severity: "CRITICAL" }),
      );
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/nhume/deliveries/d-123/links",
        { key: "daidzaiIncidentRef", ref: "inc-9" },
      );
    });
    expect(await screen.findByText(/Incident INC-9 opened/)).toBeInTheDocument();
    expect(screen.getByText(/written back onto the delivery/)).toBeInTheDocument();
  });

  it("reports an honest partial state when the link-back fails", async () => {
    searchParams.value = new URLSearchParams("source=nhume&delivery=d-77&ref=NHM-77&priority=URGENT");
    get.mockResolvedValue([]);
    post.mockResolvedValueOnce({ id: "inc-2", incidentReference: "INC-2" });
    post.mockRejectedValueOnce(new Error("nhume down"));

    const { default: userEvent } = await import("@testing-library/user-event");
    render(<DaidzaiCommandPage />);
    await userEvent.click(await screen.findByRole("button", { name: /Open incident/ }));

    expect(await screen.findByText(/link-back to the Nhume delivery failed/)).toBeInTheDocument();
  });
});
