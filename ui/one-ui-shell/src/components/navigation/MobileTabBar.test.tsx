import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MobileTabBar } from "./MobileTabBar";

const toggleNavDrawer = vi.fn();

vi.mock("next/navigation", () => ({ usePathname: () => "/home" }));
vi.mock("@/hooks/useShellStore", () => ({
  useShellStore: (selector: (state: { toggleNavDrawer: () => void }) => unknown) =>
    selector({ toggleNavDrawer }),
}));

describe("MobileTabBar", () => {
  it("renders the primary thumb-reachable destinations", () => {
    render(<MobileTabBar />);

    expect(screen.getByRole("link", { name: /Home/i })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("link", { name: /Services/i })).toHaveAttribute("href", "/discover");
    expect(screen.getByRole("link", { name: /Nompilo/i })).toHaveAttribute("href", "/ask");
    expect(screen.getByRole("link", { name: /Wallet/i })).toHaveAttribute("href", "/wallet");
  });

  it("lights up the active destination for the current route", () => {
    render(<MobileTabBar />);
    // usePathname() is /home → the Home tab is the current page.
    expect(screen.getByRole("link", { name: /Home/i })).toHaveAttribute("aria-current", "page");
  });

  it("opens the zones drawer from the More tab — nothing is lost on mobile", async () => {
    render(<MobileTabBar />);

    await userEvent.click(screen.getByRole("button", { name: /More/i }));

    expect(toggleNavDrawer).toHaveBeenCalledOnce();
  });
});
