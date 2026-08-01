import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const beginOidcLogin = vi.hoisted(() => vi.fn());
vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
}));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams("returnTo=/home/profile") }));
vi.mock("@/components/AuthLayout", () => ({ AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/lib/auth/web-session", () => ({ beginOidcLogin }));

import BiometricScanLoginPage from "./page";

describe("biometric sign-in correction", () => {
  beforeEach(() => vi.clearAllMocks());

  it("explains that an ABIS identity match cannot authenticate", () => {
    render(<BiometricScanLoginPage />);
    expect(screen.getByText(/clinical biometric record can help confirm identity/i)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("continues with a native AAL2 passkey and preserves returnTo", async () => {
    render(<BiometricScanLoginPage />);
    await userEvent.click(screen.getByTestId("scan-signin"));
    expect(beginOidcLogin).toHaveBeenCalledWith({
      requiredAcr: "urn:impilo:aal2",
      returnTo: "/home/profile",
    });
  });
});
