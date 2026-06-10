import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

vi.mock("@/hooks/useInferredServiceSlug", () => ({
  useInferredServiceSlug: (explicit?: string) => explicit,
}));
