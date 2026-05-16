import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RoleJourneyNavigation } from "@/components/navigation/RoleJourneyNavigation";
import { NompiloGlobalCommandBar } from "@/components/intelligent/NompiloGlobalCommandBar";
import { AccessibilityToolbar } from "@/components/accessibility/AccessibilityToolbar";

const mockUsePathname = vi.fn();
const mockFocusSearchPalette = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { roles: string[] } }) => unknown) =>
    selector({ user: { roles: ["CLINICIAN"] } }),
}));

vi.mock("@/hooks/useShellStore", () => ({
  useShellStore: (selector: (state: { focusSearchPalette: () => void }) => unknown) =>
    selector({ focusSearchPalette: mockFocusSearchPalette }),
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

  it("renders nompilo global command bar and opens palette", () => {
    mockUsePathname.mockReturnValue("/client-journey");
    render(<NompiloGlobalCommandBar />);
    expect(screen.getByText("Nompilo Command Layer")).toBeInTheDocument();
    fireEvent.focus(
      screen.getByPlaceholderText(
        "Ask Nompilo... Search services, providers, records, reports, learning, support",
      ),
    );
    expect(mockFocusSearchPalette).toHaveBeenCalled();
  });

  it("renders accessibility toolbar toggles", () => {
    mockUsePathname.mockReturnValue("/home");
    render(<AccessibilityToolbar />);
    fireEvent.click(screen.getByRole("button", { name: "High Contrast" }));
    expect(document.documentElement.classList.contains("impilo-high-contrast")).toBe(true);
    expect(screen.getByRole("button", { name: "Read Aloud" })).toBeInTheDocument();
  });
});

