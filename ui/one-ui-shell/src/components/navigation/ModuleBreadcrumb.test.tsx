import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { ModuleBreadcrumb } from "./ModuleBreadcrumb";

let mockPathname = "/home";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

describe("ModuleBreadcrumb", () => {
  beforeEach(() => {
    cleanup();
  });

  it("returns null when the trail has one entry (Home only)", () => {
    mockPathname = "/home";
    const { container } = render(<ModuleBreadcrumb />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders both a desktop and a mobile breadcrumb nav for a deep route", () => {
    // A real registry route with a parent so the trail has >1 entry.
    mockPathname = "/facility";
    render(<ModuleBreadcrumb />);
    const navs = screen.getAllByRole("navigation", { name: "Breadcrumb" });
    // One desktop (md:flex) + one mobile (md:hidden) variant.
    expect(navs.length).toBe(2);
  });

  it("mobile variant marks the current location with aria-current=page", () => {
    mockPathname = "/facility";
    render(<ModuleBreadcrumb />);
    const current = screen.getAllByText((_, el) => el?.getAttribute("aria-current") === "page");
    expect(current.length).toBeGreaterThanOrEqual(1);
  });
});
