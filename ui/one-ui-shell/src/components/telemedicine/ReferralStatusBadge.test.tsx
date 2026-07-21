import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  ReferralStatusBadge,
  REFERRAL_STATUS_DESCRIPTORS,
  referralStatusDescriptor,
} from "./ReferralStatusBadge";

describe("ReferralStatusBadge", () => {
  it("maps every lifecycle status to its label and tone", () => {
    for (const [status, descriptor] of Object.entries(REFERRAL_STATUS_DESCRIPTORS)) {
      const { unmount } = render(<ReferralStatusBadge status={status} />);
      const badge = screen.getByTestId("referral-status-badge");
      expect(badge).toHaveTextContent(descriptor.label);
      expect(badge).toHaveAttribute("data-tone", descriptor.tone);
      unmount();
    }
  });

  it("assigns the documented tone families", () => {
    expect(referralStatusDescriptor("ACCEPTED").tone).toBe("active");
    expect(referralStatusDescriptor("SUBMITTED").tone).toBe("waiting");
    expect(referralStatusDescriptor("AWAITING_RESULTS").tone).toBe("waiting");
    expect(referralStatusDescriptor("COMPLETED").tone).toBe("terminal");
    expect(referralStatusDescriptor("CLOSED").tone).toBe("terminal");
    expect(referralStatusDescriptor("DECLINED").tone).toBe("exception");
    expect(referralStatusDescriptor("ENTERED_IN_ERROR").tone).toBe("exception");
  });

  it("is case-insensitive on the incoming status", () => {
    expect(referralStatusDescriptor("accepted").label).toBe("Accepted");
    expect(referralStatusDescriptor("accepted").tone).toBe("active");
  });

  it("degrades an unknown status to a neutral prettified chip without throwing", () => {
    render(<ReferralStatusBadge status="SOME_FUTURE_STATE" />);
    const badge = screen.getByTestId("referral-status-badge");
    expect(badge).toHaveTextContent("Some future state");
    expect(badge).toHaveAttribute("data-tone", "unknown");
  });

  it("renders an Unknown label for a blank/null status", () => {
    expect(referralStatusDescriptor(null).label).toBe("Unknown");
    expect(referralStatusDescriptor("").label).toBe("Unknown");
  });
});
