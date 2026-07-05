import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NotificationsPage from "./page";

const markReadMutate = vi.fn();
const mutationMutate = vi.fn();

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

vi.mock("@/hooks/queries/useNotifications", () => ({
  useNotifications: () => ({
    data: {
      data: [
        // Flat NotificationResponse rows as served by the notification service
        {
          id: "n-1",
          templateKey: "QUEUE_CITIZEN_CALLED",
          channel: "IN_APP",
          to: "cpid-1",
          status: "SENT",
          attempts: 1,
          lastError: null,
          createdAt: "2026-07-04T08:00:00Z",
          sentAt: "2026-07-04T08:00:01Z",
          readAt: null,
        },
        {
          id: "n-2",
          templateKey: "APPOINTMENT_CITIZEN_CONFIRMED",
          channel: "IN_APP",
          to: "cpid-1",
          status: "SENT",
          attempts: 1,
          lastError: null,
          createdAt: "2026-07-03T10:00:00Z",
          sentAt: "2026-07-03T10:00:01Z",
          readAt: "2026-07-03T11:00:00Z",
        },
        {
          id: "n-3",
          templateKey: "QUEUE_CITIZEN_PRIORITISED",
          channel: "IN_APP",
          to: "cpid-1",
          status: "SENT",
          attempts: 1,
          lastError: null,
          createdAt: "2026-07-04T09:00:00Z",
          sentAt: null,
          readAt: null,
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
  useMarkNotificationRead: () => ({ mutate: markReadMutate, isPending: false }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  useMutation: () => ({ mutate: mutationMutate, isPending: false }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post: vi.fn() },
}));

describe("NotificationsPage (citizen inbox)", () => {
  beforeEach(() => {
    markReadMutate.mockReset();
    mutationMutate.mockReset();
  });

  it("renders QUEUE_CITIZEN_* and APPOINTMENT_CITIZEN_* template rows with labels", () => {
    render(<NotificationsPage />);

    expect(screen.getByText("It is your turn — please proceed")).toBeInTheDocument();
    expect(screen.getByText("Your place in the queue was prioritised")).toBeInTheDocument();
    expect(screen.getByText("Appointment confirmed")).toBeInTheDocument();
  });

  it("computes unread count from readAt and marks a notification read", async () => {
    const user = userEvent.setup();
    render(<NotificationsPage />);

    // n-1 and n-3 are unread (readAt null); n-2 is read.
    expect(screen.getByText("2 unread notifications")).toBeInTheDocument();

    const readButtons = screen.getAllByTitle("Mark as read");
    expect(readButtons).toHaveLength(2);
    await user.click(readButtons[0]);
    expect(markReadMutate).toHaveBeenCalledWith("n-1");
  });
});
