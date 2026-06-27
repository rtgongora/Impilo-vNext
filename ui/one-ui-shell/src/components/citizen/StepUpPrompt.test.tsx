import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { StepUpPrompt } from "./StepUpPrompt";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

describe("StepUpPrompt (G-CZO-04)", () => {
  beforeEach(() => post.mockReset());

  it("drives challenge → verify → onCompleted against the BFF step-up endpoints", async () => {
    post
      .mockResolvedValueOnce({ data: { challengeId: "c-1", status: "PENDING" } })
      .mockResolvedValueOnce({ data: { challengeId: "c-1", status: "COMPLETED" } });
    const onCompleted = vi.fn();

    render(<StepUpPrompt methods={["MFA"]} onCompleted={onCompleted} onCancel={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /send code/i }));

    const codeInput = await screen.findByLabelText("Verification code");
    fireEvent.change(codeInput, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /^verify$/i }));

    await waitFor(() => expect(onCompleted).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenNthCalledWith(1, "/internal/v1/step-up/challenge", { challengeType: "MFA" });
    expect(post).toHaveBeenNthCalledWith(2, "/internal/v1/step-up/verify", {
      challengeId: "c-1",
      verificationCode: "123456",
    });
  });

  it("shows an error and does not complete when the code is rejected", async () => {
    post
      .mockResolvedValueOnce({ data: { challengeId: "c-1", status: "PENDING" } })
      .mockRejectedValueOnce({ status: 400 });
    const onCompleted = vi.fn();

    render(<StepUpPrompt methods={["MFA"]} onCompleted={onCompleted} onCancel={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /send code/i }));
    const codeInput = await screen.findByLabelText("Verification code");
    fireEvent.change(codeInput, { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: /^verify$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/not accepted/i);
    expect(onCompleted).not.toHaveBeenCalled();
  });
});
