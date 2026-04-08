import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TelemedicineSessionPage from "./page";

const { push, replace, post, mutateAsync } = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  post: vi.fn(),
  mutateAsync: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ sessionId: "session-1" }),
  useRouter: () => ({ push, replace }),
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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post,
  },
}));

vi.mock("@/hooks/queries/useReferrals", () => ({
  useRespondReferral: () => ({
    mutateAsync,
  }),
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSessions: () => ({
    data: {
      data: [
        {
          id: "session-1",
          attributes: {
            encounter_id: "enc-1",
            patient_id: "patient-1",
            provider_id: "provider-1",
            facility_id: "facility-1",
            session_type: "VIDEO",
            status: "IN_PROGRESS",
            room_url: null,
            scheduled_at: "2026-04-08T08:30:00.000Z",
            started_at: "2026-04-08T09:00:00.000Z",
            ended_at: null,
            duration_seconds: null,
            notes: null,
            referral_id: "ref-9",
            created_at: "2026-04-08T08:00:00.000Z",
            updated_at: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useJoinTelemedicineSession: () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    data: undefined,
  }),
  useEndTelemedicineSession: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

describe("TelemedicineSessionPage", () => {
  beforeEach(() => {
    push.mockReset();
    replace.mockReset();
    post.mockReset();
    mutateAsync.mockReset();
    post.mockResolvedValue({});
    mutateAsync.mockResolvedValue({});
  });

  it("saves referral-linked completion notes with loop update metadata", async () => {
    const user = userEvent.setup();

    render(<TelemedicineSessionPage />);

    expect(screen.getByText("Loop-closure handoff")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Key findings from this consultation..."), "Patient improved after nebulisers.");
    await user.type(screen.getByPlaceholderText("Clinical assessment and recommended management plan..."), "Continue inhaled therapy and review tomorrow.");
    await user.selectOptions(screen.getByRole("combobox"), "REVIEW");
    await user.click(screen.getByRole("button", { name: "Save Completion Note" }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());

    const clinicalNoteCall = post.mock.calls.find(
      (call) => call[0] === "/internal/v1/clinical-notes",
    );

    expect(clinicalNoteCall?.[1]?.body).toContain("Referral loop update:");
    expect(clinicalNoteCall?.[1]?.body).toContain("Linked referral: ref-9");
    expect(clinicalNoteCall?.[1]?.body).toContain("Next workspace action: Review response and close the loop in Consults");

    expect(mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "ref-9",
        outcome: "FOLLOW_UP_REQUIRED",
        response_notes: expect.stringContaining("Response sent from teleconsult: Yes"),
      }),
    );

    expect(await screen.findByText("Note and returned response saved")).toBeInTheDocument();
  }, 30000);
});
