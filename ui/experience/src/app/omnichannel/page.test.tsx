import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OmnichannelPage from "./page";

const { get, post, searchParamsValue } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  searchParamsValue: { current: new URLSearchParams("tab=sms") },
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

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><OmnichannelPage /></QueryClientProvider>);
}

describe("OmnichannelPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    searchParamsValue.current = new URLSearchParams("tab=sms");
  });

  it("honors the sms deep link and creates journeys through the omnichannel endpoint", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/omnichannel/sms-journeys") return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({ data: { id: "journey-1" } });

    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("button", { name: /SMS Journeys/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /New journey/i }));
    await user.type(screen.getByPlaceholderText("Journey name"), "Appointment reminder");
    await user.type(screen.getByPlaceholderText("Trigger event"), "APPOINTMENT_REMINDER");
    await user.type(screen.getByPlaceholderText("Message template"), "Visit tomorrow");
    await user.click(screen.getByRole("button", { name: /Create journey/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith("/internal/v1/omnichannel/sms-journeys", expect.objectContaining({
        name: "Appointment reminder",
        triggerEvent: "APPOINTMENT_REMINDER",
        messageTemplate: "Visit tomorrow",
      }));
    });
  }, 10000);

  it("replaces fake ai telemetry with governed links and real counts", async () => {
    searchParamsValue.current = new URLSearchParams("tab=ai-agent");
    get.mockImplementation((url: string) => {
      if (url === "/internal/v1/omnichannel/channels") return Promise.resolve({ data: [{ id: "cfg-1", channel_type: "SMS", name: "SMS", is_active: true }] });
      if (url === "/internal/v1/omnichannel/callbacks") return Promise.resolve({ data: [{ id: "cb-1", status: "PENDING" }] });
      if (url === "/internal/v1/omnichannel/disclosure-rules") return Promise.resolve({ data: [{ id: "rule-1", channel_type: "SMS", data_category: "CLINICAL", disclosure_level: "SUMMARY" }] });
      if (url === "/internal/v1/omnichannel/sms-journeys") return Promise.resolve({ data: [{ id: "sms-1", name: "Reminder", trigger_event: "APPOINTMENT_REMINDER", message_template: "Hi", is_active: true }] });
      return Promise.resolve({ data: [] });
    });

    renderPage();

    expect(await screen.findByRole("link", { name: /AI Governance Hub/i })).toHaveAttribute("href", "/ai-governance");
    expect(screen.getByRole("link", { name: /Review disclosure rules/i })).toHaveAttribute("href", "/omnichannel?tab=disclosure");
    expect(screen.queryByText("AI Interaction Log")).not.toBeInTheDocument();
  });
});
