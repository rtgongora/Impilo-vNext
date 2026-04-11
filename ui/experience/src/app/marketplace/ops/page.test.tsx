import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MarketplaceOpsPage from "./page";

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

describe("MarketplaceOpsPage", () => {
  it("renders explicit unsupported state and links to map", () => {
    render(<MarketplaceOpsPage />);
    expect(screen.getByRole("heading", { level: 1, name: /Marketplace ops/i })).toBeInTheDocument();
    expect(screen.getByText(/Unsupported/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /See integration map/i })).toHaveAttribute(
      "href",
      "/finance/commerce-integrations",
    );
  });
});

