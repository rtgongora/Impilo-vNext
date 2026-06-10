import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ModuleCardIcon } from "@/components/branding/ModuleCardIcon";
import { Database } from "lucide-react";

describe("ModuleCardIcon", () => {
  it("renders service logo when slug is provided", () => {
    render(<ModuleCardIcon serviceSlug="fundo" icon={Database} color="bg-cyan-100" />);
    expect(screen.getByRole("img", { name: "Fundo logo" })).toBeInTheDocument();
  });

  it("falls back to lucide icon when no slug", () => {
    const { container } = render(<ModuleCardIcon icon={Database} color="bg-cyan-100" />);
    expect(container.querySelector("svg")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
});
