import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CommerceIntegrationsPage from "./page";

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

describe("CommerceIntegrationsPage", () => {
  it("documents canonical Experience routes and lists missing MSIKA/MusheX BFF contracts", () => {
    render(<CommerceIntegrationsPage />);

    expect(screen.getByRole("heading", { level: 1, name: /Commerce & payer stack/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^\/marketplace$/i })).toHaveAttribute("href", "/marketplace");
    expect(screen.getByRole("link", { name: /^\/coverage$/i })).toHaveAttribute("href", "/coverage");
    const settlementLinks = screen.getAllByRole("link", { name: /^\/finance\/settlements$/i });
    expect(settlementLinks.length).toBeGreaterThanOrEqual(1);
    expect(settlementLinks[0]).toHaveAttribute("href", "/finance/settlements");
    expect(screen.getAllByRole("link", { name: /^\/finance\/reconciliation$/i })[0]).toHaveAttribute(
      "href",
      "/finance/reconciliation",
    );
    expect(screen.getAllByRole("link", { name: /^\/finance\/refunds$/i })[0]).toHaveAttribute("href", "/finance/refunds");
    expect(screen.getAllByRole("link", { name: /^\/finance\/payer-ops$/i })[0]).toHaveAttribute(
      "href",
      "/finance/payer-ops",
    );
    expect(screen.getByRole("link", { name: /^\/marketplace\/vendor$/i })).toHaveAttribute("href", "/marketplace/vendor");
    expect(screen.getByText(/ui\/msika-web/i)).toBeInTheDocument();
    expect(screen.getByText(/ui\/mushex-finance-console/i)).toBeInTheDocument();
    expect(screen.getAllByText(/\/internal\/v1\/product-registry\//i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/\/internal\/v1\/commerce\//i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/\/internal\/v1\/mushex\//i).length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: /Finance dashboard/i })).toHaveAttribute("href", "/finance");
  });
});
