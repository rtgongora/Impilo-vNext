import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MyDocumentsPage from "./page";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const hooks = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}));

vi.mock("@/hooks/queries/usePersonalDocuments", () => ({
  usePersonalDocuments: () => ({
    data: {
      data: [
        {
          id: "doc-1",
          title: "Discharge summary",
          originalFilename: "discharge-summary.pdf",
          documentTypeCode: "DISCHARGE_SUMMARY",
          mimeType: "application/pdf",
          lifecycleState: "ACTIVE",
          createdAt: "2026-04-10T09:00:00Z",
          fileSize: 20480,
        },
        {
          id: "doc-2",
          title: "Lab report",
          originalFilename: "lab-report.pdf",
          documentTypeCode: "LAB_REPORT",
          mimeType: "application/pdf",
          lifecycleState: "AVAILABLE",
          createdAt: "2026-04-01T09:00:00Z",
          fileSize: 10240,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  usePersonalDocumentDownloadUrl: () => ({
    mutateAsync: hooks.mutateAsync,
    isPending: false,
  }),
}));

describe("MyDocumentsPage", () => {
  beforeEach(() => {
    hooks.mutateAsync.mockReset();
  });

  it("renders the Experience-backed document vault and claim handoff", () => {
    render(<MyDocumentsPage />);

    expect(screen.getByRole("heading", { level: 1, name: /My Documents/i })).toBeInTheDocument();
    expect(screen.getByText(/Canonical Experience document vault/i)).toBeInTheDocument();
    expect(screen.getByText("Discharge summary")).toBeInTheDocument();
    expect(screen.getByText("lab-report.pdf")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open claim flow/i })).toHaveAttribute("href", "/share/claim");
  });

  it("prepares a download link on demand", async () => {
    const user = userEvent.setup();
    hooks.mutateAsync.mockResolvedValue({
      data: {
        url: "https://downloads.example/doc-1",
      },
    });

    render(<MyDocumentsPage />);

    await user.click(screen.getAllByRole("button", { name: /Prepare download/i })[0]);

    await waitFor(() => {
      expect(hooks.mutateAsync).toHaveBeenCalledWith("doc-1");
    });

    expect(screen.getByRole("link", { name: /Download document/i })).toHaveAttribute(
      "href",
      "https://downloads.example/doc-1",
    );
  });
});
