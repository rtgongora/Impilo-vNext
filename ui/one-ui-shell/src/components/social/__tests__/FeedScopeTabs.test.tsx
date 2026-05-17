import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { FeedScopeTabs } from "../FeedScopeTabs";

describe("FeedScopeTabs", () => {
  it("renders default scope tabs", () => {
    render(<FeedScopeTabs active="home" onChange={() => undefined} />);
    expect(screen.getByText("Home")).toBeInTheDocument();
    expect(screen.getByText("Wellness")).toBeInTheDocument();
    expect(screen.getByText("Public health")).toBeInTheDocument();
    expect(screen.getByText("Saved")).toBeInTheDocument();
  });

  it("calls onChange with the selected scope id", () => {
    const onChange = vi.fn();
    render(<FeedScopeTabs active="home" onChange={onChange} />);
    fireEvent.click(screen.getByText("Wellness"));
    expect(onChange).toHaveBeenCalledWith("wellness");
  });

  it("restricts tabs when scopes are provided", () => {
    render(<FeedScopeTabs active="home" onChange={() => undefined} scopes={["home", "saved"]} />);
    expect(screen.getByText("Home")).toBeInTheDocument();
    expect(screen.getByText("Saved")).toBeInTheDocument();
    expect(screen.queryByText("Wellness")).not.toBeInTheDocument();
  });
});
