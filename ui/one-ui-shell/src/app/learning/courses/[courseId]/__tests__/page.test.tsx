import type { ReactNode } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import LearningCourseDetailPage from "../page";

/**
 * Phase 6B — vitest coverage for the live Impilo Fundo course-detail page.
 *
 * Mocks out the shell scaffolding, Next.js `useParams`, and the
 * `useFundoCourseStructure` hook so the page renders deterministically.
 */

const { structureState } = vi.hoisted(() => ({
  structureState: {
    data: undefined as
      | {
          data: {
            structure: {
              id: string;
              code: string;
              title: string;
              description?: string | null;
              category?: string | null;
              level?: string | null;
              status: string;
              cpdEligible: boolean;
              mandatory: boolean;
              estimatedDurationMinutes?: number | null;
              modules: Array<{
                id: string;
                title: string;
                description?: string | null;
                sequence: number;
                status: string;
                lessons: Array<{
                  id: string;
                  title: string;
                  contentType: string;
                  estimatedDurationMinutes?: number | null;
                  sequence: number;
                  required: boolean;
                  status: string;
                }>;
              }>;
            };
          };
        }
      | undefined,
    isLoading: false,
    isError: false,
  },
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ courseId: "course-1" }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    title,
    subtitle,
    children,
  }: {
    title: string;
    subtitle?: string;
    children: ReactNode;
  }) => (
    <section>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </section>
  ),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCourseStructure: () => structureState,
}));

vi.mock("@/hooks/queries/useFundoLms", () => ({
  useCreateFundoEnrolment: () => ({ mutate: vi.fn(), mutateAsync: vi.fn() }),
}));

vi.mock("@/components/learning/LearningSubjectPicker", () => ({
  useLearningSubject: () => ({ subjectType: "PROVIDER", subjectId: "SUBJECT-1" }),
}));

vi.mock("@/components/live/FundoLiveWebinarEmbed", () => ({
  FundoLiveWebinarEmbed: () => <div data-testid="fundo-live-embed">Live webinar</div>,
}));

vi.mock("@/components/learning/live/CourseLiveSessionsSection", () => ({
  CourseLiveSessionsSection: () => <div data-testid="course-live-sessions-mock" />,
}));

function setState(state: Partial<typeof structureState>) {
  Object.assign(structureState, state);
}

describe("Phase 6B — LearningCourseDetailPage", () => {
  afterEach(() => {
    setState({ data: undefined, isLoading: false, isError: false });
    cleanup();
  });

  it("renders the course structure with ordered modules and lessons", () => {
    setState({
      data: {
        data: {
          structure: {
            id: "course-1",
            code: "FUNDO-CTX-A",
            title: "Foundation A",
            description: "Orientation course",
            category: "ORIENTATION",
            level: "INTRODUCTORY",
            status: "PUBLISHED",
            cpdEligible: false,
            mandatory: true,
            estimatedDurationMinutes: 30,
            modules: [
              {
                id: "module-1",
                title: "Module One",
                description: "First module",
                sequence: 1,
                status: "PUBLISHED",
                lessons: [
                  {
                    id: "lesson-1",
                    title: "Lesson 1",
                    contentType: "TEXT",
                    estimatedDurationMinutes: 5,
                    sequence: 1,
                    required: true,
                    status: "PUBLISHED",
                  },
                  {
                    id: "lesson-2",
                    title: "Lesson 2 (Optional)",
                    contentType: "VIDEO",
                    estimatedDurationMinutes: 8,
                    sequence: 2,
                    required: false,
                    status: "PUBLISHED",
                  },
                ],
              },
            ],
          },
        },
      },
    });

    render(<LearningCourseDetailPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Foundation A" })).toBeInTheDocument();
    expect(screen.getByText("Native course: FUNDO-CTX-A")).toBeInTheDocument();
    expect(screen.getByText("1. Module One")).toBeInTheDocument();
    expect(screen.getByText("Lesson 1")).toBeInTheDocument();
    expect(screen.getByText("Lesson 2 (Optional)")).toBeInTheDocument();
    expect(screen.getByText("Optional")).toBeInTheDocument();
    expect(screen.getByText("Mandatory")).toBeInTheDocument();
  });

  it("renders an unavailable state when the upstream is unreachable", () => {
    setState({ isError: true });
    render(<LearningCourseDetailPage />);
    expect(screen.getByText(/currently unavailable/i)).toBeInTheDocument();
  });

  it("renders a loading state while the structure is fetching", () => {
    setState({ isLoading: true });
    render(<LearningCourseDetailPage />);
    expect(screen.getByText(/Loading course/i)).toBeInTheDocument();
  });
});
