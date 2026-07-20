import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SettlementsPage from "./page";

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

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SettlementsPage />
    </QueryClientProvider>,
  );
}

describe("FinanceSettlementsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/finance/settlements/S-1") {
        return Promise.resolve({ settlementId: "S-1", status: "COMPUTED" });
      }
      return Promise.resolve({});
    });
    post.mockImplementation((url: string, body?: unknown) => {
      if (url === "/internal/v1/finance/settlements/run") {
        return Promise.resolve({ settlementId: "S-1", ...((body as object) ?? {}) });
      }
      if (url === "/internal/v1/finance/settlements/S-1/release-payouts") {
        return Promise.resolve({ settlementId: "S-1", status: "RELEASED" });
      }
      return Promise.resolve({});
    });
  });

  it("runs settlement, loads detail, and releases payouts", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("heading", { level: 1, name: /Settlements/i })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Run settlement/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/settlements/run", expect.objectContaining({
        periodStart: expect.any(String),
        periodEnd: expect.any(String),
      }));
    });

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/finance/settlements/S-1");
    });

    await user.click(screen.getByRole("button", { name: /Release payouts/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/settlements/S-1/release-payouts");
    });
  });

  it("threads a releasing-officer biometric step-up into the release body", async () => {
    const user = userEvent.setup();
    post.mockImplementation((url: string, body?: unknown) => {
      if (url === "/internal/v1/finance/settlements/run") {
        return Promise.resolve({ settlementId: "S-1", ...((body as object) ?? {}) });
      }
      if (url === "/internal/v1/identity/biometric/abis/extract") {
        return Promise.resolve({ ok: true, templateBase64: "TPL==" });
      }
      if (url === "/internal/v1/finance/settlements/S-1/release-payouts") {
        return Promise.resolve({ settlementId: "S-1", status: "RELEASED" });
      }
      return Promise.resolve({});
    });
    renderPage();

    await user.click(screen.getByRole("button", { name: /Run settlement/i }));
    await waitFor(() => expect(get).toHaveBeenCalledWith("/internal/v1/finance/settlements/S-1"));

    await user.type(screen.getByLabelText("Releasing officer reference"), "officer-3");
    const file = new File(["x"], "print.png", { type: "image/png" });
    fireEvent.change(screen.getByTestId("biometric-probe-file"), { target: { files: [file] } });
    await waitFor(() => expect(screen.getByTestId("biometric-captured")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /Release payouts/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/finance/settlements/S-1/release-payouts", {
        biometricSubjectRef: "officer-3",
        biometricModality: "FINGERPRINT",
        biometricProbeBase64: "TPL==",
      });
    });
  });
});
