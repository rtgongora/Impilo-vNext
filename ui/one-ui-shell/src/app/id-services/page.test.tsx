import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import IdServicesPage from "./page";

const mockUseSearchParams = vi.fn();
const mockPost = vi.fn();
const mockGet = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockUseSearchParams(),
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

vi.mock("@/components/experience/RegistryPlaneContextBar", () => ({
  RegistryPlaneContextBar: () => <div>registry-context</div>,
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: (...args: unknown[]) => mockPost(...args),
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <IdServicesPage />
    </QueryClientProvider>,
  );
}

describe("IdServicesPage", () => {
  beforeEach(() => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
    mockPost.mockReset();
    mockGet.mockReset();
  });

  it("opens the requested tab from search params", () => {
    mockUseSearchParams.mockReturnValue(new URLSearchParams("tab=validate"));

    renderPage();

    expect(screen.getByRole("heading", { name: /Validate Health ID/i })).toBeInTheDocument();
  });

  it("uses the provider creation contract instead of the patient register endpoint", async () => {
    mockPost.mockResolvedValue({
      data: {
        providerPublicId: "VAR-2026-001",
        profession: "GENERAL_PRACTITIONER",
      },
    });

    renderPage();

    const providerCard = screen.getByTestId("generate-card-provider");
    const inputs = within(providerCard).getAllByRole("textbox");
    fireEvent.change(inputs[0], { target: { value: "Tariro" } });
    fireEvent.change(inputs[1], { target: { value: "Moyo" } });
    fireEvent.click(within(providerCard).getByRole("button", { name: /^Generate$/i }));

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        "/internal/v1/identity/provider/create",
        expect.objectContaining({
          givenName: "Tariro",
          familyName: "Moyo",
          profession: "GENERAL_PRACTITIONER",
        }),
      ),
    );

    expect(screen.getByText("VAR-2026-001")).toBeInTheDocument();
  });

  it("marks facility creation as unsupported instead of simulating success", () => {
    renderPage();

    const facilityCard = screen.getByTestId("generate-card-facility");
    expect(within(facilityCard).getByRole("button", { name: /Unsupported/i })).toBeDisabled();
    expect(within(facilityCard).getByText(/Facility identity creation is not proxied/i)).toBeInTheDocument();
  });
});
