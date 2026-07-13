import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AnnouncementsPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div><h1>{title}</h1>{children}</div>
  ),
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AnnouncementsPage />
    </QueryClientProvider>,
  );
}

describe("AnnouncementsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue({ data: { messageId: "m-1" } });
    get.mockImplementation((url: string) => {
      if (url.includes("/channels/discover")) {
        return Promise.resolve({ data: [{ conversationId: "chan-1", title: "Ward A", conversationType: "CHANNEL", status: "ACTIVE" }] });
      }
      if (url.includes("/conversations/chan-1/messages")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    });
  });

  it("discovers channels for a scope and broadcasts an announcement", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText("UUID"), "fac-123");
    await user.click(screen.getByRole("button", { name: /^Discover$/ }));

    await waitFor(() => expect(screen.getByText("Ward A")).toBeInTheDocument());
    expect(get.mock.calls.some((c) => String(c[0]).includes("/channels/discover?scope_type=FACILITY&scope_ref=fac-123"))).toBe(true);

    await user.click(screen.getByText("Ward A"));
    await user.type(screen.getByPlaceholderText(/Announcement to broadcast/), "Clinic closed tomorrow");
    await user.click(screen.getByRole("button", { name: /^Broadcast$/ }));

    await waitFor(() =>
      expect(post.mock.calls.some((c) => String(c[0]) === "/internal/v1/khuluma/channels/chan-1/broadcast")).toBe(true),
    );
    const call = post.mock.calls.find((c) => String(c[0]).includes("/channels/chan-1/broadcast"));
    expect((call?.[1] as { body?: string })?.body).toBe("Clinic closed tomorrow");
  });
});
