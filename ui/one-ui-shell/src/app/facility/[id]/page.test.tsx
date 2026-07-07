// @vitest-environment jsdom
import type { ReactNode } from "react";
import { render, screen, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => cleanup());

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "fac-uuid-1" }),
}));

vi.mock("next/link", () => ({
  default: ({ children }: { children: ReactNode }) => <span>{children}</span>,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/FeatureMaturityBadge", () => ({
  FeatureMaturityBadge: () => <span>badge</span>,
}));

// Facility core fetch (the page's direct useQuery) resolves to a real facility.
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: {
        id: "fac-uuid-1",
        type: "facility",
        attributes: {
          name: "Chitungwiza Central Hospital",
          code: "ZW-HCH-001",
          facilityType: "HOSPITAL",
          status: "ACTIVE",
          capabilities: [],
          district: "Chitungwiza",
          province: "Harare",
          staffCount: 0,
          workspaceCount: 0,
          bedCapacity: 0,
          occupiedBeds: 0,
          workspaces: [],
          services: [],
        },
      },
    },
    isLoading: false,
    error: null,
  }),
}));

vi.mock("@/hooks/queries/useFacilityImports", () => ({
  useFacilityProvenance: () => ({ data: undefined }),
}));

// The composite (legitimacy) read fails closed — TUSO unavailable (502).
const legitState = { data: undefined as unknown, isError: true, error: new Error("Request failed: 502") };
vi.mock("@/hooks/queries/useFacilityStatusComposite", () => ({
  useFacilityStatusComposite: () => legitState,
}));

import FacilityDetailPage from "./page";

describe("Facility detail — legitimacy fail-closed (E3)", () => {
  it("renders an explicit TUSO-unavailable notice instead of silently omitting the panel", () => {
    render(<FacilityDetailPage />);

    const notice = screen.getByTestId("facility-legitimacy-unavailable");
    expect(notice).toBeTruthy();
    expect(notice.textContent).toContain("TUSO");
    expect(notice.textContent).toContain("withheld");
    // The real panel (and its verdict badge) must NOT render on a fail-closed read.
    expect(screen.queryByTestId("facility-platform-access-verdict")).toBeNull();
  });
});
