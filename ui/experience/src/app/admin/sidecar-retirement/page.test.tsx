import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SidecarRetirementPage from "./page";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

describe("SidecarRetirementPage", () => {
  it("lists absorbed and blocked sidecar capabilities in the Experience ledger", () => {
    render(<SidecarRetirementPage />);

    expect(screen.getByRole("heading", { level: 1, name: /Sidecar Retirement Ledger/i })).toBeInTheDocument();
    expect(screen.getByText(/Facility marketplace shell/i)).toBeInTheDocument();
    expect(screen.getByText(/National clinical knowledge curation queue/i)).toBeInTheDocument();
    expect(screen.getByText(/ui\/knowledge-admin:\//i)).toBeInTheDocument();
    expect(screen.getByText(/ui\/msika-web:\/catalogs/i)).toBeInTheDocument();
    expect(screen.getByText(/\/finance\/msika-governance/i)).toBeInTheDocument();
    expect(screen.getByText(/ui\/butano-web:\/ips,\/timeline,\/triggers/i)).toBeInTheDocument();
    expect(screen.getByText(/partially absorbed into Experience/i)).toBeInTheDocument();
    expect(screen.getAllByText(/absorbed into Experience/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/retired sidecar path/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Integration status/i })).toHaveAttribute(
      "href",
      "/admin/integration-status",
    );
  });
});
