import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ContactRegisterPage from "./page";

const push = vi.fn();
const setAuth = vi.fn();
const deriveFromRoles = vi.fn();
const hydrate = vi.fn();
const acceptConsent = vi.fn();
const acceptPolicyConsentMutate = vi.fn();

// Mutation doubles — each test can override the impl per case.
const requestMutate = vi.fn();
const verifyMutate = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

// Guidance lane component makes a network call — stub it out.
vi.mock("@/components/intelligent/GatewayEscalationExplainer", () => ({
  GatewayEscalationExplainer: () => <div data-testid="explainer" />,
}));

vi.mock("@/components/intelligent/NompiloHint", () => ({
  NompiloHint: () => null,
}));

vi.mock("@/hooks/queries/useContactOtp", () => ({
  useRequestContactOtp: () => ({ mutate: requestMutate, isPending: false }),
  useVerifyContactOtp: () => ({ mutate: verifyMutate, isPending: false }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ setAuth }),
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: Object.assign(
    () => ({ acceptConsent }),
    { getState: () => ({ hydrate }) },
  ),
  CURRENT_CONSENT_VERSION: "2026-04-11",
}));

vi.mock("@/hooks/queries/usePolicyConsent", () => ({
  useAcceptPolicyConsent: () => ({ mutate: acceptPolicyConsentMutate, isPending: false }),
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: Object.assign(vi.fn(), {
    getState: () => ({ deriveFromRoles }),
  }),
}));

async function fillStep1(user: ReturnType<typeof userEvent.setup>, contact = "tariro@example.com") {
  await user.type(screen.getByPlaceholderText("Your full name"), "Tariro Moyo");
  await user.type(screen.getByPlaceholderText("+263... or you@example.com"), contact);
  await user.click(screen.getByRole("checkbox", { name: /Privacy Policy/i }));
  await user.click(screen.getByRole("checkbox", { name: /Terms of Use/i }));
  await user.click(screen.getByRole("button", { name: /Send verification code/i }));
}

describe("ContactRegisterPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the step-1 contact form", () => {
    render(<ContactRegisterPage />);
    expect(screen.getByText("Create your account")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Your full name")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("+263... or you@example.com")).toBeInTheDocument();
  });

  it("requires privacy + terms before the code can be requested", () => {
    render(<ContactRegisterPage />);
    const submit = screen.getByRole("button", { name: /Send verification code/i });
    expect(submit).toBeDisabled();
  });

  it("transitions to step 2 after a successful OTP request", async () => {
    requestMutate.mockImplementation((_payload, opts) =>
      opts.onSuccess({
        data: { type: "contact-otp", attributes: { status: "SENT", channel: "EMAIL", maskedValue: "t***@example.com", expiresInSeconds: 300 } },
      }),
    );
    const user = userEvent.setup();
    render(<ContactRegisterPage />);

    await fillStep1(user);

    expect(requestMutate).toHaveBeenCalledWith(
      { channel: "EMAIL", value: "tariro@example.com" },
      expect.anything(),
    );
    await waitFor(() => expect(screen.getByText("Verify your contact")).toBeInTheDocument());
    expect(screen.getByText(/t\*\*\*@example\.com/)).toBeInTheDocument();
  });

  it("auto-logs-in on a 200 auth_token verify and routes through resolving", async () => {
    requestMutate.mockImplementation((_p, opts) =>
      opts.onSuccess({ data: { type: "contact-otp", attributes: { status: "SENT", channel: "EMAIL", maskedValue: "t***@example.com", expiresInSeconds: 300 } } }),
    );
    verifyMutate.mockImplementation((_p, opts) =>
      opts.onSuccess({
        data: {
          type: "auth_token",
          attributes: {
            token: "jwt-123",
            expiresAt: "2026-07-16T00:00:00Z",
            user: { id: "u-1", email: "tariro@example.com", displayName: "t***@example.com", roles: ["CITIZEN"], actorType: "CITIZEN", method: "email-otp" },
          },
        },
      }),
    );

    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await fillStep1(user);
    await waitFor(() => expect(screen.getByText("Verify your contact")).toBeInTheDocument());

    const boxes = screen.getAllByRole("textbox");
    for (let i = 0; i < 6; i++) await user.type(boxes[i], String(i + 1));
    await user.type(screen.getByPlaceholderText(/At least 12 characters/i), "SecurePass123!");
    await user.type(screen.getByPlaceholderText("Repeat password"), "SecurePass123!");
    await user.click(screen.getByRole("button", { name: /Verify & create account/i }));

    expect(setAuth).toHaveBeenCalledWith(
      expect.objectContaining({ id: "u-1", roles: ["CITIZEN"], actorType: "CITIZEN" }),
      "jwt-123",
      null,
      "2026-07-16T00:00:00Z",
    );
    expect(deriveFromRoles).toHaveBeenCalledWith(["CITIZEN"]);
    expect(acceptPolicyConsentMutate).toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith("/auth/resolving");
  });

  it("shows the account-created screen on a 201 REGISTERED verify", async () => {
    requestMutate.mockImplementation((_p, opts) =>
      opts.onSuccess({ data: { type: "contact-otp", attributes: { status: "SENT", channel: "PHONE", maskedValue: "+263****89", expiresInSeconds: 300 } } }),
    );
    verifyMutate.mockImplementation((_p, opts) =>
      opts.onSuccess({ data: { type: "registration", attributes: { status: "REGISTERED", channel: "PHONE", maskedValue: "+263****89", message: "Account created." } } }),
    );

    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await fillStep1(user, "+263771234589");
    await waitFor(() => expect(screen.getByText("Verify your contact")).toBeInTheDocument());

    const boxes = screen.getAllByRole("textbox");
    for (let i = 0; i < 6; i++) await user.type(boxes[i], "1");
    await user.type(screen.getByPlaceholderText(/At least 12 characters/i), "SecurePass123!");
    await user.type(screen.getByPlaceholderText("Repeat password"), "SecurePass123!");
    await user.click(screen.getByRole("button", { name: /Verify & create account/i }));

    await waitFor(() => expect(screen.getByText("Account created")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: /Go to sign in/i })).toHaveAttribute("href", "/auth/login");
    expect(setAuth).not.toHaveBeenCalled();
  });

  it("maps a 429 rate-limit on request to an inline error", async () => {
    requestMutate.mockImplementation((_p, opts) =>
      opts.onError({ status: 429, error: { code: "RATE_LIMITED", message: "slow down" } }),
    );
    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await fillStep1(user);

    expect(screen.getByText(/Too many code requests/i)).toBeInTheDocument();
    // Stayed on step 1.
    expect(screen.getByText("Create your account")).toBeInTheDocument();
  });

  it("maps an OTP_INVALID verify to an inline error and clears the code", async () => {
    requestMutate.mockImplementation((_p, opts) =>
      opts.onSuccess({ data: { type: "contact-otp", attributes: { status: "SENT", channel: "EMAIL", maskedValue: "t***@example.com", expiresInSeconds: 300 } } }),
    );
    verifyMutate.mockImplementation((_p, opts) =>
      opts.onError({ status: 401, error: { code: "OTP_INVALID", message: "Incorrect code." } }),
    );

    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await fillStep1(user);
    await waitFor(() => expect(screen.getByText("Verify your contact")).toBeInTheDocument());

    const boxes = screen.getAllByRole("textbox");
    for (let i = 0; i < 6; i++) await user.type(boxes[i], "9");
    await user.type(screen.getByPlaceholderText(/At least 12 characters/i), "SecurePass123!");
    await user.type(screen.getByPlaceholderText("Repeat password"), "SecurePass123!");
    await user.click(screen.getByRole("button", { name: /Verify & create account/i }));

    expect(screen.getByText(/Incorrect code/i)).toBeInTheDocument();
    // Code boxes cleared for re-entry.
    expect(screen.getAllByRole("textbox")[0]).toHaveValue("");
  });
});
