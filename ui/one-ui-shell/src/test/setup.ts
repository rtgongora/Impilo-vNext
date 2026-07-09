import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

vi.mock("@/hooks/useInferredServiceSlug", () => ({
  useInferredServiceSlug: (explicit?: string) => explicit,
}));

// TierProvider (Future-Realism) reads matchMedia on mount — jsdom does not provide it.
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});
