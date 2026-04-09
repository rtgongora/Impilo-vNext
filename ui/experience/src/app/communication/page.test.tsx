import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CommunicationPage from "./page";

const { get, post, searchParamsValue } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  searchParamsValue: { current: new URLSearchParams() },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParamsValue.current,
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
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
  return render(<QueryClientProvider client={client}><CommunicationPage /></QueryClientProvider>);
}

describe("CommunicationPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    searchParamsValue.current = new URLSearchParams();
  });

  it("links message channels into the real secure-messaging workspace", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/communication/messages/channels") {
        return Promise.resolve({ data: [{ id: "channel-1", name: "Ward consult", channel_type: "DIRECT", participants: ["u1", "u2"], last_message_at: "2026-04-09T08:00:00.000Z" }] });
      }
      if (url === "/internal/v1/communication/pages") return Promise.resolve({ data: [] });
      if (url === "/internal/v1/omnichannel/callbacks") return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });

    renderPage();

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith("/internal/v1/communication/messages/channels");
    }, { timeout: 10000 });
    const threadLink = await screen.findByRole("link", { name: /Ward consult/i }, { timeout: 10000 });
    expect(threadLink).toHaveAttribute(
      "href",
      "/communication/secure-messaging?channelId=channel-1",
    );
  }, 10000);

  it("sends pages through the real communication pages endpoint", async () => {
    searchParamsValue.current = new URLSearchParams("tab=pages");
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/communication/messages/channels") return Promise.resolve({ data: [] });
      if (url === "/internal/v1/communication/pages") return Promise.resolve({ data: [] });
      if (url === "/internal/v1/omnichannel/callbacks") return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({ data: { id: "page-1", status: "SENT" } });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: /Send page/i }));
    await user.type(screen.getByPlaceholderText("Recipient ID"), "provider-7");
    await user.type(screen.getByPlaceholderText("Recipient name"), "Dr. Moyo");
    await user.type(screen.getByPlaceholderText("Page message..."), "Please review Bed 4");
    await user.click(screen.getAllByRole("button", { name: /^Send page$/i })[1]);

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/communication/pages", expect.objectContaining({
        senderId: "user-1",
        senderName: "Tariro Moyo",
        recipientId: "provider-7",
        recipientName: "Dr. Moyo",
        message: "Please review Bed 4",
      }));
    });
  });

  it("replaces fake call controls with telemedicine and callback routing", async () => {
    searchParamsValue.current = new URLSearchParams("tab=calls");
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/communication/messages/channels") return Promise.resolve({ data: [] });
      if (url === "/internal/v1/communication/pages") return Promise.resolve({ data: [] });
      if (url === "/internal/v1/omnichannel/callbacks") {
        return Promise.resolve({ data: [
          { id: "cb-1", status: "PENDING" },
          { id: "cb-2", status: "COMPLETED" },
        ] });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage();

    expect(await screen.findByRole("link", { name: /Open telemedicine/i })).toHaveAttribute("href", "/telemedicine");
    expect(screen.getByRole("link", { name: /Open callback queue/i })).toHaveAttribute("href", "/omnichannel?tab=callbacks");
    await waitFor(() => {
      expect(screen.queryByText(/Loading callback queue/i)).not.toBeInTheDocument();
    });
    expect(screen.getByText("Pending callbacks")).toBeInTheDocument();
    expect(screen.getByText("Completed callbacks")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Start Voice Call/i })).not.toBeInTheDocument();
  });
});
