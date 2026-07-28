import { render, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ProviderWorkspaceShimPage from "./page";

const { searchParamsState, replaceMock } = vi.hoisted(() => ({
  searchParamsState: { value: "" },
  replaceMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () => new URLSearchParams(searchParamsState.value),
}));

afterEach(() => cleanup());

beforeEach(() => {
  searchParamsState.value = "";
  replaceMock.mockReset();
});

describe("/provider-workspace shim (Phase F6)", () => {
  it("redirects to /work when no tab is specified — this route's historical behaviour", () => {
    render(<ProviderWorkspaceShimPage />);

    expect(replaceMock).toHaveBeenCalledWith("/work");
  });

  it("redirects to /work when tab=work is explicit", () => {
    searchParamsState.value = "tab=work";

    render(<ProviderWorkspaceShimPage />);

    expect(replaceMock).toHaveBeenCalledWith("/work");
  });

  it("preserves non-tab intent (e.g. queue) onto the /work redirect", () => {
    searchParamsState.value = "tab=work&queue=opd";

    render(<ProviderWorkspaceShimPage />);

    expect(replaceMock).toHaveBeenCalledWith("/work?queue=opd");
  });

  it("redirects to /professional for tab=professional", () => {
    searchParamsState.value = "tab=professional";

    render(<ProviderWorkspaceShimPage />);

    expect(replaceMock).toHaveBeenCalledWith("/professional");
  });

  it("redirects to /professional/{section} when a section is specified", () => {
    searchParamsState.value = "tab=professional&section=licence";

    render(<ProviderWorkspaceShimPage />);

    expect(replaceMock).toHaveBeenCalledWith("/professional/licence");
  });

  it("renders nothing of its own — the shim's whole job is resolving intent and redirecting", () => {
    const { container } = render(<ProviderWorkspaceShimPage />);

    expect(container).toBeEmptyDOMElement();
  });
});
