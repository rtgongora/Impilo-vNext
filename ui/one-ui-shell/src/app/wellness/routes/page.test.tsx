import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RoutesPage from "@/app/wellness/routes/page";
import { logWellnessActivity } from "@/lib/citizen-wellness-api";

const logWellnessActivityMock = vi.mocked(logWellnessActivity);

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: React.ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/lib/citizen-wellness-api", () => ({
  fetchWellnessDiscoverServices: vi.fn().mockResolvedValue([
    {
      id: "svc-1",
      name: "City Park Run",
      category: "fitness",
      rating: 4.5,
      facilityName: "City Park",
      description: "Outdoor running track",
    },
  ]),
  logWellnessActivity: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "cpid-1000" } }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RoutesPage />
    </QueryClientProvider>,
  );
}

describe("Wellness routes page", () => {
  it("renders discover catalogue without synthetic route metadata", async () => {
    renderPage();
    expect(await screen.findByRole("heading", { name: "Routes & Places" })).toBeInTheDocument();
    expect(await screen.findByText("City Park Run")).toBeInTheDocument();
    expect(screen.queryByText(/coming soon/i)).not.toBeInTheDocument();
    expect(screen.getByText(/not yet exposed by the wellness API/i)).toBeInTheDocument();
  });

  it("logs a planned visit through the wellness activity BFF", async () => {
    renderPage();
    expect(await screen.findByText("City Park Run")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /log planned visit/i }));
    await waitFor(() => expect(logWellnessActivityMock).toHaveBeenCalled());
    expect(logWellnessActivityMock.mock.calls[0][0]).toMatchObject({
      patientId: "cpid-1000",
      activeMinutes: 30,
    });
    expect(await screen.findByRole("button", { name: /visit logged/i })).toBeDisabled();
  });
});
