import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RoleJourneyNavigation } from "@/components/navigation/RoleJourneyNavigation";
import { AccessibilityToolbar } from "@/components/accessibility/AccessibilityToolbar";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { AppLayout } from "@/components/AppLayout";
import { visibleShellCommands } from "@/lib/shell/app-registry";

const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector?: (state: Record<string, unknown>) => unknown) => {
    const state = {
      user: { roles: ["CLINICIAN"], displayName: "Test User", email: "test@example.com" },
      isAuthenticated: true,
    };
    return selector ? selector(state) : state;
  },
}));

vi.mock("@/hooks/useShellStore", () => ({
  useShellStore: (selector: (state: { toggleNavDrawer: () => void }) => unknown) =>
    selector({ toggleNavDrawer: vi.fn() }),
}));

vi.mock("@/providers/ExperienceEntryProvider", () => ({
  useExperienceEntry: () => ({
    facility: null,
    workspace: null,
    shiftActive: false,
    availableOperationalModes: ["my_life"],
    operationalMode: "my_life",
    setOperationalMode: vi.fn(),
  }),
}));

vi.mock("@/components/experience/OperationalContextStrip", () => ({
  OperationalContextStrip: () => null,
}));

vi.mock("@/components/navigation/ModuleBreadcrumb", () => ({
  ModuleBreadcrumb: () => null,
}));

vi.mock("@/components/navigation/ExperienceSidebar", () => ({
  ExperienceSidebar: () => null,
}));

vi.mock("@/components/clinical/ClinicalSupportStrip", () => ({
  ClinicalSupportStrip: () => null,
}));

vi.mock("@/components/ui/DictationButton", () => ({
  DictationButton: ({ onValueChange }: { onValueChange: (value: string) => void }) => (
    <button type="button" onClick={() => onValueChange("dictated value")}>
      Dictation
    </button>
  ),
}));

describe("journey shell components", () => {
  it("renders provider role-aware navigation", () => {
    mockUsePathname.mockReturnValue("/provider-workspace");
    render(<RoleJourneyNavigation />);
    expect(screen.getByRole("link", { name: "My Work" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Queues" })).toBeInTheDocument();
  });

  it("keeps hero auth logo within mobile Nompilo size budget (≤48px)", () => {
    const { container } = render(<ImpiloBrandLogo variant="hero" />);
    const image = container.querySelector("img");
    expect(image).toBeTruthy();
    expect(image?.className).toContain("h-10");
    expect(image?.className).toContain("sm:h-12");
    expect(image?.className).toContain("lg:h-14");
  });

  it("does not render Nompilo page chrome in AppLayout", () => {
    mockUsePathname.mockReturnValue("/home");
    render(
      <AppLayout>
        <div>content</div>
      </AppLayout>,
    );
    expect(screen.queryByText("Nompilo Command Layer")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Nompilo — contextual help")).not.toBeInTheDocument();
    expect(screen.getByText("content")).toBeInTheDocument();
  });

  it("exposes Ask Nompilo via Ctrl+K shell commands", () => {
    const commands = visibleShellCommands(() => true);
    const ask = commands.find((command) => command.id === "cmd-ask");
    expect(ask?.label).toBe("Ask Nompilo");
    expect(ask?.keywords).toContain("nompilo");
    expect(ask?.action).toEqual({ type: "navigate", href: "/ask" });
  });

  it("renders accessibility toolbar toggles", () => {
    mockUsePathname.mockReturnValue("/home");
    render(<AccessibilityToolbar />);
    fireEvent.click(screen.getByRole("button", { name: "High Contrast" }));
    expect(document.documentElement.classList.contains("impilo-high-contrast")).toBe(true);
    expect(screen.getByRole("button", { name: "Read Aloud" })).toBeInTheDocument();
  });
});
