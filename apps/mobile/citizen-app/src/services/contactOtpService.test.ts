import { describe, it, expect, vi, beforeEach } from "vitest";

const publicClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ publicApiClient: publicClient }));

import { requestContactOtp, verifyContactRegister } from "./contactOtpService";

describe("contactOtpService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("requests an OTP on the anonymous auth lane and unwraps the envelope", async () => {
    publicClient.post.mockResolvedValue({
      data: { data: { type: "contact-otp", attributes: { status: "SENT", channel: "PHONE", maskedValue: "07•••567", expiresInSeconds: 300 } } },
    });
    const res = await requestContactOtp("PHONE", " 0771234567 ");
    expect(publicClient.post).toHaveBeenCalledWith("/internal/v1/auth/contact/otp/request", {
      channel: "PHONE",
      value: "0771234567",
    });
    expect(res.maskedValue).toBe("07•••567");
    expect(res.expiresInSeconds).toBe(300);
  });

  it("verifies + registers with purpose REGISTER and returns a session on the auth_token envelope", async () => {
    publicClient.post.mockResolvedValue({
      data: {
        data: {
          type: "auth_token",
          attributes: {
            token: "acc-tok",
            expiresIn: 28800,
            user: { id: "u1", roles: ["CITIZEN"], actorType: "CITIZEN" },
          },
        },
      },
    });
    const res = await verifyContactRegister({ value: "0771234567", code: "123456", password: "Sup3rSecret!!", fullName: "Tariro Moyo" });
    expect(publicClient.post).toHaveBeenCalledWith(
      "/internal/v1/auth/contact/otp/verify",
      expect.objectContaining({ purpose: "REGISTER", code: "123456", firstName: "Tariro", lastName: "Moyo", fullName: "Tariro Moyo" }),
    );
    expect(res).toMatchObject({ kind: "session", accessToken: "acc-tok", refreshToken: null, expiresIn: 28800 });
  });

  it("returns login_required when the BFF did not auto-login", async () => {
    publicClient.post.mockResolvedValue({
      data: { data: { type: "registration", attributes: { status: "REGISTERED", message: "Account created. Please sign in." } } },
    });
    const res = await verifyContactRegister({ value: "u@e.com", code: "654321", password: "Sup3rSecret!!", fullName: "Solo" });
    expect(res).toEqual({ kind: "login_required", message: "Account created. Please sign in." });
  });
});
