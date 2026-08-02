/**
 * Checkpoint 6 — the three trust outcomes must never look or sound the same.
 *
 * Assertions here use plain DOM reads rather than jest-dom matchers: in this worktree the
 * shared node_modules does not register `@testing-library/jest-dom/vitest` matchers (the
 * untouched StepUpPrompt.test.tsx fails the same way), and these checks must not depend on it.
 */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TRUST_CONTRACT_VERSION_V1 } from "../../../../../contracts/trust-decision/v1";
import type { TrustChallenge } from "@/lib/trust/challenge";
import { TrustChallengeNotice } from "./TrustChallengeNotice";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));

const deny: TrustChallenge = {
  kind: "refusal",
  decision: "DENY",
  outcome: {
    contract_version: TRUST_CONTRACT_VERSION_V1,
    decision: "DENY",
    reason_code: "RBAC_NO_MATCHING_RULE",
    user_message_key: "trust.deny.generic",
    decision_id: "dec-0f3a-secret",
    policy_version: "policy-2026.07.31",
    support_reference: "SUP-1234",
  },
};

const consent: TrustChallenge = {
  kind: "actionable",
  decision: "CONSENT_REQUIRED",
  outcome: {
    contract_version: TRUST_CONTRACT_VERSION_V1,
    decision: "CONSENT_REQUIRED",
    reason_code: "CONSENT_REQUIRED",
    user_message_key: "trust.consent.required",
    required_action: "OBTAIN_CONSENT",
  },
};

const unavailable: TrustChallenge = {
  kind: "unavailable",
  decision: "TEMPORARILY_UNAVAILABLE",
  outcome: {
    contract_version: TRUST_CONTRACT_VERSION_V1,
    decision: "TEMPORARILY_UNAVAILABLE",
    reason_code: "PDP_UNREACHABLE",
  },
};

describe("TrustChallengeNotice — the three outcomes must never look the same", () => {
  it("renders DENY as a blocking refusal with no way forward on offer", () => {
    render(<TrustChallengeNotice challenge={deny} onDismiss={vi.fn()} onRetry={vi.fn()} />);

    const panel = screen.getByRole("alertdialog");
    expect(panel.getAttribute("data-kind")).toBe("refusal");
    expect(panel.getAttribute("aria-modal")).toBe("true");
    expect(panel.textContent).toMatch(/don't have access/i);
    expect(panel.textContent).toMatch(/will not change this/i);
    expect(screen.queryByRole("button", { name: /try again/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /send code/i })).toBeNull();
    // The only control is the safe exit.
    expect(screen.getAllByRole("button")).toHaveLength(1);
    expect(screen.getByRole("button", { name: /go back/i })).toBeTruthy();
  });

  it("renders CONSENT_REQUIRED as an actionable dialog that says what to do next", () => {
    render(<TrustChallengeNotice challenge={consent} onDismiss={vi.fn()} />);

    const panel = screen.getByRole("dialog");
    expect(panel.getAttribute("data-kind")).toBe("actionable");
    expect(panel.getAttribute("data-decision")).toBe("CONSENT_REQUIRED");
    expect(panel.textContent).toMatch(/nobody has been asked/i);
    expect(panel.textContent).toMatch(/ask the person to grant consent/i);
    // Not a refusal, and not an outage.
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(panel.textContent).not.toMatch(/will not change this/i);
    expect(panel.textContent).not.toMatch(/not a permissions problem/i);
  });

  it("renders TEMPORARILY_UNAVAILABLE as a non-blocking, retryable status", () => {
    const onRetry = vi.fn();
    render(<TrustChallengeNotice challenge={unavailable} onDismiss={vi.fn()} onRetry={onRetry} />);

    const panel = screen.getByRole("status");
    expect(panel.getAttribute("data-kind")).toBe("unavailable");
    expect(panel.getAttribute("aria-live")).toBe("polite");
    expect(panel.hasAttribute("aria-modal")).toBe(false);
    expect(panel.textContent).toMatch(/not a permissions problem/i);
    expect(panel.textContent).not.toMatch(/don't have access/i);
    screen.getByRole("button", { name: /try again/i }).click();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("gives the three outcomes distinct roles and distinct words", () => {
    const seen = [deny, consent, unavailable].map((challenge) => {
      const { container, unmount } = render(
        <TrustChallengeNotice challenge={challenge} onDismiss={vi.fn()} />,
      );
      const panel = container.querySelector("[data-testid=trust-challenge]");
      const result = { role: panel?.getAttribute("role"), text: container.textContent ?? "" };
      unmount();
      return result;
    });

    expect(new Set(seen.map((s) => s.role)).size).toBe(3);
    expect(new Set(seen.map((s) => s.text)).size).toBe(3);
  });

  it("is announced and focusable, not a div-only overlay", () => {
    const { container } = render(<TrustChallengeNotice challenge={consent} onDismiss={vi.fn()} />);
    const panel = container.querySelector("[data-testid=trust-challenge]") as HTMLElement;

    expect(panel.getAttribute("role")).toBe("dialog");
    expect(panel.getAttribute("aria-labelledby")).toBeTruthy();
    expect(panel.getAttribute("aria-describedby")).toBeTruthy();
    expect(document.getElementById(panel.getAttribute("aria-labelledby")!)).toBeTruthy();
    expect(document.getElementById(panel.getAttribute("aria-describedby")!)).toBeTruthy();
    expect(document.activeElement).toBe(panel);
  });
});

describe("TrustChallengeNotice — what must never reach a person", () => {
  it("never renders reason_code, decision_id or policy_version", () => {
    const { container } = render(<TrustChallengeNotice challenge={deny} onDismiss={vi.fn()} />);
    const text = container.textContent ?? "";

    expect(text).not.toContain("RBAC_NO_MATCHING_RULE");
    expect(text).not.toContain("dec-0f3a-secret");
    expect(text).not.toContain("policy-2026.07.31");
    // support_reference IS user-facing.
    expect(text).toContain("SUP-1234");
  });

  it("never renders a raw reason_code even when the message key is unknown", () => {
    const unknownKey: TrustChallenge = {
      kind: "actionable",
      decision: "AUTHORITY_REQUIRED",
      outcome: {
        contract_version: TRUST_CONTRACT_VERSION_V1,
        decision: "AUTHORITY_REQUIRED",
        reason_code: "LICENCE_SUSPENDED_7X",
        user_message_key: "trust.some.key.the.shell.has.never.seen",
      },
    };
    const { container } = render(
      <TrustChallengeNotice challenge={unknownKey} onDismiss={vi.fn()} />,
    );

    expect(container.textContent ?? "").not.toContain("LICENCE_SUSPENDED_7X");
    expect(screen.getByRole("dialog").textContent).toMatch(/professional authority/i);
  });
});

describe("TrustChallengeNotice — offers only what the outcome permits", () => {
  it("offers exactly the context options the outcome carries", () => {
    const onSelectContext = vi.fn();
    const contextChallenge: TrustChallenge = {
      kind: "actionable",
      decision: "CONTEXT_REQUIRED",
      outcome: {
        contract_version: TRUST_CONTRACT_VERSION_V1,
        decision: "CONTEXT_REQUIRED",
        context_options: ["PARIRENYATWA_OPD", "HARARE_CENTRAL_ED"],
      },
    };

    render(
      <TrustChallengeNotice
        challenge={contextChallenge}
        onDismiss={vi.fn()}
        onSelectContext={onSelectContext}
      />,
    );

    screen.getByRole("button", { name: /parirenyatwa opd/i }).click();
    expect(onSelectContext).toHaveBeenCalledWith("PARIRENYATWA_OPD");
    // Exactly the two permitted options plus the safe exit — nothing invented.
    expect(screen.getAllByRole("button")).toHaveLength(3);
  });

  it("reuses StepUpPrompt for STEP_UP_REQUIRED, offering only the permitted methods", () => {
    const stepUp: TrustChallenge = {
      kind: "actionable",
      decision: "STEP_UP_REQUIRED",
      outcome: {
        contract_version: TRUST_CONTRACT_VERSION_V1,
        decision: "STEP_UP_REQUIRED",
        user_message_key: "trust.step_up.required",
        allowed_authentication_methods: ["SMS_OTP"],
      },
    };

    render(<TrustChallengeNotice challenge={stepUp} onDismiss={vi.fn()} onResolved={vi.fn()} />);

    const select = screen.getByLabelText("Verification method") as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toEqual(["SMS_OTP"]);
    expect(screen.getByRole("button", { name: /send code/i })).toBeTruthy();
  });
});
