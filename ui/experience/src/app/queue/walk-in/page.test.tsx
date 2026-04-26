import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import WalkInPage from "./page";

const { push, post } = vi.hoisted(() => ({
  push: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams("patientId=patient-1"),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/components/registry/VitoClientRegistrationWizard", () => ({
  VitoClientRegistrationWizard: () => <div data-testid="vito-registration-wizard-stub" />,
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/usePatients", () => ({
  usePatients: () => ({ data: { data: [] }, isLoading: false }),
  usePatient: () => ({
    data: {
      data: {
        id: "patient-1",
        attributes: {
          displayName: "Tariro Moyo",
          cpid: "CP-001",
          dateOfBirth: "1991-06-11",
          gender: "female",
        },
      },
    },
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post,
  },
}));

describe("WalkInPage", () => {
  beforeEach(() => {
    push.mockReset();
    post.mockReset();
    post.mockResolvedValue({ data: { id: "entry-1" } });
  });

  it("can receive a patient from search and create the queue entry in place", async () => {
    const user = userEvent.setup();

    render(<WalkInPage />);

    expect((await screen.findAllByText("Selected patient")).length).toBeGreaterThan(0);
    expect(screen.getByText("Tariro Moyo")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Add to Queue" }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/queue/entries",
        expect.objectContaining({
          patient_id: "patient-1",
          facility_id: "facility-1",
          queue_type: "WALK_IN",
        }),
      ),
    );
    expect(push).toHaveBeenCalledWith("/queue");
  });
});
