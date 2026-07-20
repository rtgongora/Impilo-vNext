import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdjudicationConsole from "./page";

vi.mock("next/link", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
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

const queueMock = { data: undefined as unknown, isLoading: false, isError: false };
const statsMock = { data: undefined as unknown };

vi.mock("@/hooks/queries/useAbisAdjudication", () => ({
  useAdjudicationQueue: () => queueMock,
  useAbisStats: () => statsMock,
}));

describe("AdjudicationConsole", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statsMock.data = {
      templates: { active: 12 },
      adjudication: { OPEN: 3, IN_REVIEW: 1, RESOLVED: 5 },
    };
    queueMock.data = {
      content: [
        {
          id: 42,
          subjectRef: "probe-1",
          modality: "FINGERPRINT",
          reason: "ENROLMENT",
          status: "OPEN",
          candidateCount: 2,
          topScore: 0.93,
          decision: null,
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    };
  });

  it("renders the queue and stats strip", () => {
    render(<AdjudicationConsole />);
    expect(screen.getByTestId("stats-strip")).toBeTruthy();
    expect(screen.getByTestId("case-row-42")).toBeTruthy();
    expect(screen.getByTestId("case-row-42").textContent).toContain("FINGERPRINT");
    expect(screen.getByTestId("case-row-42").textContent).toContain("93.0%");
  });

  it("shows an empty state when there are no cases", () => {
    queueMock.data = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
    render(<AdjudicationConsole />);
    expect(screen.getByTestId("queue-empty")).toBeTruthy();
  });
});
