import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ContactRegisterPage from "./page";

const requestMutate = vi.fn();
const verifyMutate = vi.fn();

vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => <a href={href} {...props}>{children}</a>,
}));
vi.mock("@/components/AuthLayout", () => ({ AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/intelligent/GatewayEscalationExplainer", () => ({ GatewayEscalationExplainer: () => <div /> }));
vi.mock("@/components/intelligent/NompiloHint", () => ({ NompiloHint: () => null }));
vi.mock("@/hooks/queries/useContactOtp", () => ({
  useRequestContactOtp: () => ({ mutate: requestMutate, isPending: false }),
  useVerifyContactOtp: () => ({ mutate: verifyMutate, isPending: false }),
}));

async function requestCode(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText("Your full name"), "Tariro Moyo");
  await user.type(screen.getByPlaceholderText("you@example.com"), "tariro@example.com");
  await user.click(screen.getByRole("checkbox", { name: /Privacy Policy/i }));
  await user.click(screen.getByRole("checkbox", { name: /Terms of Use/i }));
  await user.click(screen.getByRole("button", { name: /Send verification code/i }));
}

function otpSent() {
  requestMutate.mockImplementation((_payload, options) => options.onSuccess({
    data: { type: "contact-otp", attributes: {
      status: "SENT", channel: "EMAIL", maskedValue: "t***@example.com", expiresInSeconds: 300,
    } },
  }));
}

async function enterCode(user: ReturnType<typeof userEvent.setup>, code: string) {
  for (let index = 0; index < code.length; index += 1) {
    await user.type(screen.getByLabelText(`Digit ${index + 1}`), code[index]);
  }
}

describe("ContactRegisterPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("collects verified email and never renders a password field", () => {
    render(<ContactRegisterPage />);
    expect(screen.getByLabelText("Email address")).toHaveAttribute("type", "email");
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(screen.getByText(/handled only by Keycloak/i)).toBeInTheDocument();
  });

  it("requests an email OTP and then creates a native-action account without a password", async () => {
    otpSent();
    verifyMutate.mockImplementation((payload, options) => options.onSuccess({
      data: { type: "registration", attributes: {
        status: "REGISTERED", channel: "EMAIL", maskedValue: "t***@example.com",
      } },
    }));
    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await requestCode(user);
    expect(requestMutate).toHaveBeenCalledWith(
      { channel: "EMAIL", value: "tariro@example.com" }, expect.anything(),
    );
    await enterCode(user, "123456");
    await user.click(screen.getByRole("button", { name: /Verify & create account/i }));
    const payload = verifyMutate.mock.calls[0][0];
    expect(payload).toMatchObject({ value: "tariro@example.com", code: "123456", purpose: "REGISTER" });
    expect(payload).not.toHaveProperty("password");
    expect(await screen.findByText("Account created")).toBeInTheDocument();
    expect(screen.getByText(/secure Keycloak email/i)).toBeInTheDocument();
  });

  it("fails honestly when the verification code is rejected", async () => {
    otpSent();
    verifyMutate.mockImplementation((_payload, options) => options.onError({
      status: 401, error: { code: "OTP_INVALID" },
    }));
    const user = userEvent.setup();
    render(<ContactRegisterPage />);
    await requestCode(user);
    await waitFor(() => expect(screen.getByText("Verify your contact")).toBeInTheDocument());
    await enterCode(user, "000000");
    await user.click(screen.getByRole("button", { name: /Verify & create account/i }));
    expect(await screen.findByText(/Incorrect code/i)).toBeInTheDocument();
  });
});
