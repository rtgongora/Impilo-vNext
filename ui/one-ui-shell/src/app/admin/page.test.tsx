import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AdminPage from "./page";

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

describe("AdminPage", () => {
  it("renders administration dashboard with governance subtitle", () => {
    render(<AdminPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Administration" })).toBeInTheDocument();
    expect(screen.getByText("TSHEPO Governance and platform administration")).toBeInTheDocument();
  });

  it("shows all admin section cards with correct links", () => {
    render(<AdminPage />);

    const sections = [
      { title: "Users", href: "/admin/users" },
      { title: "Roles", href: "/admin/roles" },
      { title: "Policies", href: "/admin/policies" },
      { title: "Audit", href: "/admin/audit" },
      { title: "Consent", href: "/admin/consent" },
      { title: "Devices", href: "/admin/devices" },
      { title: "Keys", href: "/admin/keys" },
      { title: "Federation", href: "/admin/federation" },
      { title: "Tenants", href: "/admin/tenants" },
      { title: "Break Glass", href: "/admin/break-glass" },
      { title: "Clinical Curation", href: "/admin/clinical-curation" },
    ];

    for (const section of sections) {
      expect(screen.getByText(section.title)).toBeInTheDocument();
      expect(screen.getByRole("link", { name: new RegExp(section.title) })).toHaveAttribute(
        "href",
        section.href,
      );
    }
  });
});
