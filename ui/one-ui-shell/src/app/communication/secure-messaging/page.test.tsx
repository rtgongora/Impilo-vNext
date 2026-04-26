import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SecureMessagingPage from "./page";

const { get, post, searchParamsValue } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  searchParamsValue: { current: new URLSearchParams("channelId=channel-1") },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParamsValue.current,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div>,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { id: string; displayName: string } }) => unknown) =>
    selector({ user: { id: "user-1", displayName: "Tariro Moyo" } }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><SecureMessagingPage /></QueryClientProvider>);
}

describe("SecureMessagingPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    searchParamsValue.current = new URLSearchParams("channelId=channel-1");
  });

  it("loads channels and the selected thread from communication endpoints", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/communication/messages/channels") {
        return Promise.resolve({ data: [{ id: "channel-1", name: "Ward consult", channel_type: "DIRECT", last_message_at: "2026-04-09T08:00:00.000Z" }] });
      }
      if (url === "/internal/v1/communication/messages/channels/channel-1/messages") {
        return Promise.resolve({ data: [{ id: "msg-1", channel_id: "channel-1", sender_id: "user-2", sender_name: "Dr. Moyo", content: "Please review the ECG.", message_type: "TEXT", created_at: "2026-04-09T08:05:00.000Z" }] });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    expect((await screen.findAllByText("Ward consult")).length).toBeGreaterThan(0);
    expect(await screen.findByText("Please review the ECG.")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/communication/messages/channels");
    expect(get).toHaveBeenCalledWith("/internal/v1/communication/messages/channels/channel-1/messages");
  });

  it("sends messages through the real communication messages endpoint", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/communication/messages/channels") {
        return Promise.resolve({ data: [{ id: "channel-1", name: "Ward consult", channel_type: "DIRECT", last_message_at: "2026-04-09T08:00:00.000Z" }] });
      }
      if (url === "/internal/v1/communication/messages/channels/channel-1/messages") {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({ data: { id: "msg-2" } });

    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByPlaceholderText("Type a message..."), "We are reviewing now");
    await user.click(screen.getByRole("button", { name: /Send message/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/communication/messages", expect.objectContaining({
        channelId: "channel-1",
        senderId: "user-1",
        senderName: "Tariro Moyo",
        content: "We are reviewing now",
      }));
    });
  });
});
