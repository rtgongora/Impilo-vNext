import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SurgicalReferralsPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("SurgicalReferralsPage", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); });

  it("renders pending referrals and accepting funnels onto the waitlist", async () => {
    get.mockResolvedValue([
      { id: "r-1", patientId: "CPID-1", intendedProcedureZiboCode: "ZIBO-CHOLE", targetSpecialty: "GENERAL_SURGERY", clinicalPriority: "P2", decision: "PENDING" },
    ]);
    post.mockResolvedValueOnce({});
    render(<SurgicalReferralsPage />);

    await waitFor(() => expect(screen.getByText(/ZIBO-CHOLE/)).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith("/internal/v1/referrals/surgical/worklist?decision=PENDING");

    await userEvent.click(screen.getByRole("button", { name: /Accept/ }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/referrals/surgical/r-1/decide", { decision: "ACCEPTED" }));
    await waitFor(() => expect(screen.getByText(/now on the surgical waitlist/)).toBeInTheDocument());
  });

  it("shows an honest empty state", async () => {
    get.mockResolvedValueOnce([]);
    render(<SurgicalReferralsPage />);
    await waitFor(() => expect(screen.getByText(/No pending surgical referrals/)).toBeInTheDocument());
  });
});
