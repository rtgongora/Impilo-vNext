import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React, { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const otpMocks = vi.hoisted(() => ({
  requestContactOtp: vi.fn(),
  verifyContactRegister: vi.fn(),
}));
const authMocks = vi.hoisted(() => ({ establishFromTokenResponse: vi.fn() }));

vi.mock("../../services/contactOtpService", () => otpMocks);
vi.mock("@impilo/mobile-auth", () => ({ useAuth: () => authMocks }));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    Button: ({ title, onPress, disabled, testID }: { title: string; onPress: () => void; disabled?: boolean; testID?: string }) =>
      React.createElement("button", { disabled, onClick: onPress, "data-testid": testID }, title),
    TextField: ({ value, onChangeText, testID, placeholder }: { value: string; onChangeText: (v: string) => void; testID?: string; placeholder?: string }) =>
      React.createElement("input", {
        value,
        placeholder,
        "data-testid": testID,
        onChange: (e: { target: { value: string } }) => onChangeText(e.target.value),
        onInput: (e: { target: { value: string } }) => onChangeText(e.target.value),
      }),
    OtpCodeInput: ({ value, onChangeText, testID }: { value: string; onChangeText: (v: string) => void; testID?: string }) =>
      React.createElement("input", {
        value,
        "data-testid": `${testID}-field`,
        onChange: (e: { target: { value: string } }) => onChangeText(e.target.value),
        onInput: (e: { target: { value: string } }) => onChangeText(e.target.value),
      }),
    isOtpComplete: (v: string) => v.replace(/\D/g, "").length === 6,
  };
});

import { ContactSignUpScreen } from "./ContactSignUpScreen";
import { appStore } from "../../stores/appStore";

function render(el: React.ReactElement) {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => root.render(el));
  return { container, root };
}
function testId(c: HTMLElement, id: string): HTMLElement {
  const node = c.querySelector(`[data-testid="${id}"]`);
  if (!node) throw new Error(`missing ${id}`);
  return node as HTMLElement;
}
function type(input: HTMLElement, value: string) {
  act(() => {
    (input as HTMLInputElement).value = value;
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}
function click(el: HTMLElement) {
  act(() => el.dispatchEvent(new MouseEvent("click", { bubbles: true })));
}
async function flush() {
  await act(async () => { await Promise.resolve(); await Promise.resolve(); });
}

describe("ContactSignUpScreen (R1 contact-first OTP onboarding)", () => {
  let mounted: { root: Root; container: HTMLElement };

  beforeEach(() => {
    vi.clearAllMocks();
    appStore.getState().setOnboardingScreen("contact-signup");
  });
  afterEach(() => {
    act(() => mounted?.root.unmount());
  });

  it("requests an OTP then verifies + registers and establishes a live session", async () => {
    otpMocks.requestContactOtp.mockResolvedValue({ status: "SENT", channel: "PHONE", maskedValue: "07•••567", expiresInSeconds: 300 });
    otpMocks.verifyContactRegister.mockResolvedValue({
      kind: "session",
      accessToken: "acc-tok",
      refreshToken: null,
      expiresIn: 28800,
      user: { id: "u1" },
    });

    mounted = render(React.createElement(ContactSignUpScreen, { onBack: vi.fn() }));
    const c = mounted.container;

    type(testId(c, "contact-signup-name"), "Tariro Moyo");
    type(testId(c, "contact-signup-contact"), "0771234567");
    click(testId(c, "contact-signup-send"));
    await flush();

    expect(otpMocks.requestContactOtp).toHaveBeenCalledWith("PHONE", "0771234567");

    // Now on the verify step
    type(testId(c, "contact-signup-otp-field"), "123456");
    type(testId(c, "contact-signup-password"), "Sup3rSecret!!");
    click(testId(c, "contact-signup-verify"));
    await flush();

    expect(otpMocks.verifyContactRegister).toHaveBeenCalledWith(
      expect.objectContaining({ value: "0771234567", code: "123456", fullName: "Tariro Moyo" }),
    );
    expect(authMocks.establishFromTokenResponse).toHaveBeenCalledWith({
      access_token: "acc-tok",
      refresh_token: null,
      expires_in: 28800,
    });
    expect(appStore.getState().onboardingScreen).toBe("assurance");
  });
});
