import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RegistryHubPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

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

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RegistryHubPage />
    </QueryClientProvider>,
  );
}

describe("RegistryHubPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { accepted: true } });
  });

  it("submits guided patient identity registration without raw JSON payload boxes", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.queryByDisplayValue(/"givenName"/i)).not.toBeInTheDocument();
    const familyInputs = screen.getAllByLabelText("Family name");
    await user.clear(familyInputs[0]);
    await user.type(familyInputs[0], "Citizen");
    await user.click(screen.getByRole("button", { name: /Register patient identity/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/identity/patient/register",
        expect.objectContaining({
          givenName: "Example",
          familyName: "Citizen",
          dateOfBirth: "1990-01-01",
          idType: "patient",
        }),
      );
    });
    expect(screen.getAllByText(/not queued offline/i).length).toBeGreaterThan(0);
  });

  it("guards recovery verification before calling the live command", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.clear(screen.getByLabelText("Recovery ID"));
    await user.click(screen.getByRole("button", { name: /Verify patient recovery/i }));

    expect(post).not.toHaveBeenCalledWith(
      "/internal/v1/identity/patient/recovery/verify",
      expect.anything(),
    );
    expect(screen.getByText(/Missing required field\(s\): recoveryId/i)).toBeInTheDocument();
  });
});
