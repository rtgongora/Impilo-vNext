import { describe, it, expect, vi, beforeEach } from "vitest";
import { render } from "@testing-library/react";
import MyLifeShimPage from "./page";
import { resolveMyLifeTarget } from "./resolve-my-life-target";

const replace = vi.fn();
let searchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => searchParams,
}));

describe("/my-life shim", () => {
  beforeEach(() => {
    replace.mockClear();
    searchParams = new URLSearchParams();
  });

  it("resolves to the canonical personal landing", () => {
    expect(resolveMyLifeTarget(new URLSearchParams())).toBe("/home");
  });

  it("preserves intent carried in the query string", () => {
    expect(resolveMyLifeTarget(new URLSearchParams("tab=wellness&ref=visit"))).toBe(
      "/home?tab=wellness&ref=visit"
    );
  });

  it("redirects on mount", () => {
    render(<MyLifeShimPage />);
    expect(replace).toHaveBeenCalledWith("/home");
  });

  it("renders nothing itself", () => {
    const { container } = render(<MyLifeShimPage />);
    expect(container).toBeEmptyDOMElement();
  });
});
