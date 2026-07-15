import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BankReconciliationPage from "./page";
import { apiClient } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));

const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <BankReconciliationPage />
    </QueryClientProvider>,
  );
}

describe("BankReconciliationPage", () => {
  beforeEach(() => post.mockReset());

  it("matches statement lines and shows MATCHED / UNMATCHED with reasons", async () => {
    post.mockResolvedValue({
      data: [
        { bankRef: "BANK-TX-1", referenceCode: "IMP-ABCD2345", result: "MATCHED", detail: null },
        { bankRef: "BANK-TX-2", referenceCode: "IMP-EFGH6789", result: "UNMATCHED", detail: "amount mismatch: expected 20 got 19.99" },
      ],
    });

    renderPage();
    const textarea = screen.getByPlaceholderText(/ZIPIT FRM J MOYO/i);
    await userEvent.type(
      textarea,
      "ZIPIT REF IMP-ABCD2345 | 50 | BANK-TX-1\nZIPIT REF IMP-EFGH6789 | 19.99 | BANK-TX-2",
    );
    await userEvent.click(screen.getByRole("button", { name: /match/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/finance/statement-match",
        expect.objectContaining({ lines: expect.any(Array) }),
      ),
    );
    expect(await screen.findByText("MATCHED")).toBeInTheDocument();
    expect(screen.getByText("UNMATCHED")).toBeInTheDocument();
    expect(screen.getByText(/amount mismatch/i)).toBeInTheDocument();
    expect(screen.getByText("1/2 matched")).toBeInTheDocument();
  });
});
