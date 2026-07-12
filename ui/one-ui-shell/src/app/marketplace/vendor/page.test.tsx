import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import VendorHomePage from "./page";

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
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const { identityData } = vi.hoisted(() => ({ identityData: { current: null as { vendorId: string } | null } }));

vi.mock("@/hooks/queries/useCommerceVendor", () => ({
  useVendorIdentity: () => ({ data: identityData.current, isLoading: false }),
}));

// Operator persona so the manual override section renders.
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { hasRole: (r: string) => boolean }) => unknown) =>
    selector({ hasRole: (r: string) => r === "MARKETPLACE_OPERATOR" }),
}));

describe("MarketplaceVendorHomePage", () => {
  beforeEach(() => {
    sessionStorage.clear();
    identityData.current = null;
  });

  it("lets an operator save a vendor-id override and deep-links vendor orders", async () => {
    const user = userEvent.setup();
    render(<VendorHomePage />);

    expect(screen.getByRole("heading", { level: 1, name: /Vendor workspace/i })).toBeInTheDocument();
    expect(screen.getByText(/Operator override/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Vendor ID override"), "vendor-test-1");
    await user.click(screen.getByRole("button", { name: /Save for session/i }));

    expect(sessionStorage.getItem("exp:commerce_vendor_id")).toBe("vendor-test-1");
    expect(screen.getByRole("link", { name: /Open vendor orders/i })).toHaveAttribute(
      "href",
      "/marketplace/vendor/orders?vendorId=vendor-test-1",
    );
  });

  it("auto-binds a resolved vendor identity without any manual entry", () => {
    identityData.current = { vendorId: "vendor-bound-9" };
    render(<VendorHomePage />);

    expect(screen.getByText(/vendor-bound-9/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open vendor orders/i })).toHaveAttribute(
      "href",
      "/marketplace/vendor/orders?vendorId=vendor-bound-9",
    );
  });
});
