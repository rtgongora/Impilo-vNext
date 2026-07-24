import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ContinueWithoutSignIn, safePublicHref } from "./ContinueWithoutSignIn";
import { ReasonedSignInPrompt } from "./ReasonedSignInPrompt";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

describe("public access choices", () => {
  beforeEach(() => push.mockReset());

  it("keeps continue-without links inside the public gateway", () => {
    expect(safePublicHref("/welcome/wellness")).toBe("/welcome/wellness");
    expect(safePublicHref("/admin")).toBe("/welcome");
    render(<ContinueWithoutSignIn href="/admin" />);
    expect(screen.getByTestId("continue-without-sign-in")).toHaveAttribute("href", "/welcome");
  });

  it("explains sign-in and preserves the public intent", () => {
    render(
      <ReasonedSignInPrompt
        pillar="cover-and-payments"
        goal="view-membership"
        destination="/ruvimbo/member"
        publicReturn="/welcome/coverage"
        reason="Sign in to view information linked to your membership."
      />,
    );

    expect(screen.getByText(/linked to your membership/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: /sign in and securely continue/i }));
    expect(push).toHaveBeenCalledOnce();
    const destination = push.mock.calls[0]?.[0] as string;
    expect(destination).toMatch(/^\/auth\/login\?gwi=/);
  });
});
