import { describe, it, expect, vi, beforeEach } from "vitest";
import { render } from "@testing-library/react";
import MyLifeShimPage from "./page";

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

  // Asserted through the render path rather than by importing the resolver: a page module may not
  // export anything App Router does not recognise, or `next build` rejects it. Going through the
  // mount also proves the redirect fires, which a direct call to the resolver never did.
  it("resolves to the canonical personal landing", () => {
    render(<MyLifeShimPage />);
    expect(replace).toHaveBeenCalledWith("/home");
  });

  it("preserves intent carried in the query string", () => {
    searchParams = new URLSearchParams("tab=wellness&ref=visit");
    render(<MyLifeShimPage />);
    expect(replace).toHaveBeenCalledWith("/home?tab=wellness&ref=visit");
  });

  it("renders nothing itself", () => {
    const { container } = render(<MyLifeShimPage />);
    expect(container).toBeEmptyDOMElement();
  });
});
