import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import LearningNotificationsPage from "../page";

const { scheduleMutation, notificationsState } = vi.hoisted(() => ({
  scheduleMutation: { mutateAsync: vi.fn(async () => ({ data: { notification: { id: "n1" } } })) },
  notificationsState: {
    data: {
      data: {
        items: [{ id: "n-1", title: "Course due soon", eventCode: "course.due.soon", status: "PENDING" }],
      },
    },
    refetch: vi.fn(),
  },
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ title, children }: { title: string; children: ReactNode }) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}));

vi.mock("@/components/learning/LearningSubjectPicker", () => ({
  useLearningSubject: () => ({ subjectType: "PROVIDER", subjectId: "provider-1" }),
}));

vi.mock("@/hooks/queries/useFundoStudio", () => ({
  useFundoNotifications: () => notificationsState,
  useScheduleLearningNotification: () => scheduleMutation,
}));

describe("LearningNotificationsPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders notifications and schedules one", async () => {
    render(<LearningNotificationsPage />);
    expect(screen.getByRole("heading", { name: "Learning Notifications" })).toBeInTheDocument();
    expect(screen.getByText(/Course due soon/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Schedule" }));
    expect(scheduleMutation.mutateAsync).toHaveBeenCalled();
  });
});
