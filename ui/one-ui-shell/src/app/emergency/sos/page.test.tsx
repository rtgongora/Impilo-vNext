import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EmergencySosPage from "./page";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
const { push } = vi.hoisted(() => ({ push: vi.fn() }));

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
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("EmergencySosPage", () => {
  beforeEach(() => {
    post.mockReset();
    push.mockReset();
    // no geolocation in jsdom
    // @ts-expect-error test env
    global.navigator.geolocation = undefined;
  });

  it("sends a real SOS and routes to tracking", async () => {
    post.mockResolvedValueOnce({ id: "req-9", requestReference: "SOS-20260630-X" });
    const user = userEvent.setup();
    render(<EmergencySosPage />);

    await user.click(screen.getByRole("button", { name: /Send SOS now/ }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/daidzai/requests",
        expect.objectContaining({ requesterType: "CITIZEN", emergencyCategory: "MEDICAL" })
      )
    );
    await waitFor(() => expect(push).toHaveBeenCalledWith("/emergency/track/req-9"));
  });

  it("surfaces a real error if the SOS fails (no silent success)", async () => {
    post.mockRejectedValueOnce({ status: 503 });
    const user = userEvent.setup();
    render(<EmergencySosPage />);
    await user.click(screen.getByRole("button", { name: /Send SOS now/ }));
    await waitFor(() => expect(screen.getByText(/HTTP 503|Could not send/)).toBeInTheDocument());
    expect(push).not.toHaveBeenCalled();
  });
});
