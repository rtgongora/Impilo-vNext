import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import OrganizationAdminLandingPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
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

const authUser = { roles: ["FACILITY_ADMIN"], actorType: "OPERATOR" as const };

vi.mock("@/hooks/useAuthStore", () => {
  function useAuthStore(selector: (s: { user: typeof authUser | null }) => unknown) {
    return selector({ user: authUser });
  }
  useAuthStore.getState = () => ({ user: authUser });
  return { useAuthStore };
});

const rehydrate = vi.fn();
vi.mock("@/hooks/useOperationalContextStore", () => {
  function useOperationalContextStore(selector: (s: { rehydrateFromSession: () => void }) => unknown) {
    return selector({ rehydrateFromSession: rehydrate });
  }
  useOperationalContextStore.getState = () => ({
    setOperationalMode: vi.fn(),
    setOrganizationAdminSurface: vi.fn(),
  });
  return { useOperationalContextStore };
});

describe("OrganizationAdminLandingPage", () => {
  it("frames organization administration separately from registry governance", () => {
    render(<OrganizationAdminLandingPage />);

    expect(screen.getByRole("heading", { name: "Organization administration" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Registry administration/i })).toHaveAttribute(
      "href",
      "/registry-admin",
    );
    expect(screen.getByRole("link", { name: /Facility & ward administration/i })).toHaveAttribute(
      "href",
      "/organization-admin/facility?from=organization-admin",
    );
    expect(screen.getByRole("link", { name: /Staffing & scheduling/i })).toHaveAttribute(
      "href",
      "/organization-admin/staffing?from=organization-admin",
    );
  });
});
