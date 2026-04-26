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

describe("MarketplaceVendorHomePage", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it("documents trusted-operator vendor mode and saves vendor id for the session", async () => {
    const user = userEvent.setup();
    render(<VendorHomePage />);

    expect(screen.getByRole("heading", { level: 1, name: /Vendor workspace/i })).toBeInTheDocument();
    expect(screen.getByText(/not a separate vendor login/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Vendor ID"), "vendor-test-1");
    await user.click(screen.getByRole("button", { name: /Save for session/i }));

    expect(sessionStorage.getItem("exp:commerce_vendor_id")).toBe("vendor-test-1");
    expect(screen.getByRole("link", { name: /Vendor orders/i })).toHaveAttribute(
      "href",
      "/marketplace/vendor/orders?vendorId=vendor-test-1",
    );
    expect(screen.getByRole("link", { name: /Commerce and payer integration map/i })).toHaveAttribute(
      "href",
      "/finance/commerce-integrations",
    );
  });
});
