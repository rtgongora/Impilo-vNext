import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import MvumoRegistryPage from "./page";

const mutateCreate = vi.fn();
const mutateUpdate = vi.fn();
const mutateConsent = vi.fn();

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

vi.mock("@/components/common/QueryResultPanel", () => ({
  QueryResultPanel: () => <div data-testid="templates-panel" />,
}));

vi.mock("@/hooks/queries/useMvumoAdmin", () => ({
  useMvumoAdminTemplates: () => ({
    data: { data: [{ templateKey: "registry-assisted-consent", title: "Registry consent" }] },
    isPending: false,
    isError: false,
    error: null,
  }),
  useCreateMvumoTemplate: () => ({ mutate: mutateCreate, isPending: false }),
  useUpdateMvumoTemplate: () => ({ mutate: mutateUpdate, isPending: false }),
  useCreateMvumoConsentRequest: () => ({ mutate: mutateConsent, isPending: false }),
}));

describe("MvumoRegistryPage", () => {
  it("renders template governance and consent request sections", () => {
    render(<MvumoRegistryPage />);
    expect(screen.getByText("Template governance")).toBeInTheDocument();
    expect(screen.getByText("Consent request initiation")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create template/i })).toBeInTheDocument();
  });

  it("submits consent request when subject and template are provided", async () => {
    const user = userEvent.setup();
    render(<MvumoRegistryPage />);

    await user.type(screen.getByLabelText(/Subject patient ref/i), "pat-001");
    await user.click(screen.getByRole("button", { name: /Create consent request/i }));

    expect(mutateConsent).toHaveBeenCalled();
  });
});
