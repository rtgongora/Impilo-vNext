import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CredentialsPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

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
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { id: string } }) => unknown) =>
    selector({ user: { id: "person-1" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

vi.mock("@/hooks/queries/useLicenses", () => ({
  useProviderLicenses: () => ({
    data: {
      data: [
        {
          licenseNumber: "LIC-001",
          cadre: "Doctor",
          status: "ACTIVE",
          issuedBy: "Medical Council",
          validFrom: "2025-01-01",
          validTo: "2027-01-01",
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@/hooks/queries/useCpd", () => ({
  useProviderCpd: () => ({
    data: {
      data: {
        currentCycle: {
          startDate: "2025-01-01",
          endDate: "2027-12-31",
          earnedPoints: 12,
          requiredPoints: 30,
        },
      },
    },
  }),
}));

vi.mock("@/hooks/queries/usePersonalCredentials", () => ({
  usePersonalCredentials: () => ({
    data: {
      data: [
        {
          credentialId: "cred-1",
          subjectName: "Tariro Moyo",
          credentialType: "HEALTH_SUMMARY",
          title: "Secure Health Summary",
          issuedBy: "VITO",
          status: "ACTIVE",
          validFrom: "2026-01-01",
          validTo: "2027-01-01",
          verificationUrl: "https://verify.example/cred-1",
          createdAt: "2026-01-01T00:00:00Z",
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CredentialsPage />
    </QueryClientProvider>,
  );
}

describe("CredentialsPage", () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({ data: [] });
  });

  it("shows the digital credential vault alongside professional credentials", () => {
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Credentials & CPD/i })).toBeInTheDocument();
    expect(screen.getByText(/Digital credential vault/i)).toBeInTheDocument();
    expect(screen.getByText("Secure Health Summary")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Download PDF/i })).toHaveAttribute(
      "href",
      "/internal/v1/credentials/cred-1/pdf",
    );
    expect(screen.getByRole("link", { name: /Verify/i })).toHaveAttribute("href", "https://verify.example/cred-1");
  });
});
