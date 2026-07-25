import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PublicVisualAsset } from "./PublicVisualAsset";

describe("PublicVisualAsset", () => {
  afterEach(() => {
    document.documentElement.classList.remove("impilo-low-bandwidth");
  });

  it("keeps useful content visible when an image fails", () => {
    const { container } = render(
      <PublicVisualAsset
        src="/missing.webp"
        alt="Health worker"
        fallback={<span>Public services remain available</span>}
      />,
    );

    fireEvent.error(screen.getByRole("img", { name: "Health worker" }));

    expect(container.firstElementChild).toHaveAttribute("data-image-state", "fallback");
    expect(screen.getByText("Low-data view")).toBeInTheDocument();
    expect(screen.getByText("Public services remain available")).toBeInTheDocument();
  });

  it("does not request the visual when Impilo low-bandwidth mode is enabled", async () => {
    document.documentElement.classList.add("impilo-low-bandwidth");
    const { container } = render(
      <PublicVisualAsset
        src="/experience/hero/hero-clinic-nurse.webp"
        alt="A clinic team"
        fallback={<span>Public services remain available</span>}
      />,
    );

    await waitFor(() =>
      expect(container.firstElementChild).toHaveAttribute("data-image-state", "fallback"),
    );
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("Public services remain available")).toBeInTheDocument();
  });
});
