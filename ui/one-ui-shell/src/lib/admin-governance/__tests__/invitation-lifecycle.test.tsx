import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { InvitationLifecycleActions } from "@/components/administration-governance/InvitationLifecycleActions";
import { InvitationStatusBadge } from "@/components/administration-governance/InvitationStatusBadge";
import { ImportRowReviewTable } from "@/components/administration-governance/ImportRowReviewTable";
import {
  invitationIsUsable,
  invitationStatusLabel,
  normalizeInvitationView,
  shouldShowRetry,
} from "@/lib/admin-governance/invitation-lifecycle";

describe("invitation lifecycle helpers", () => {
  it("labels sent invitations clearly", () => {
    expect(invitationStatusLabel("sent")).toBe("Sent");
  });

  it("shows pending_backend honestly", () => {
    const invitation = normalizeInvitationView({ status: "pending_backend", auditStatus: "pending_backend" });
    expect(invitation.status).toBe("pending_backend");
    expect(invitation.auditStatus).toBe("pending_backend");
  });

  it("allows retry only when policy flags allow it", () => {
    expect(shouldShowRetry(normalizeInvitationView({ status: "failed", canRetry: true }))).toBe(true);
    expect(shouldShowRetry(normalizeInvitationView({ status: "sent", canRetry: false }))).toBe(false);
  });

  it("marks expired invitations unusable", () => {
    const invitation = normalizeInvitationView({ status: "expired", expired: true });
    expect(invitationIsUsable(invitation)).toBe(false);
  });

  it("marks revoked invitations unusable", () => {
    expect(invitationIsUsable(normalizeInvitationView({ status: "revoked" }))).toBe(false);
  });
});

describe("InvitationStatusBadge", () => {
  it("renders sent invitation state", () => {
    render(<InvitationStatusBadge invitation={normalizeInvitationView({ status: "sent", invitationId: "inv-1" })} />);
    expect(screen.getByText("Sent")).toBeInTheDocument();
  });

  it("renders expired badge", () => {
    render(
      <InvitationStatusBadge
        invitation={normalizeInvitationView({ status: "expired", expired: true, expiresAt: "2020-01-01T00:00:00Z" })}
      />,
    );
    expect(screen.getAllByText("Expired").length).toBeGreaterThan(0);
  });
});

describe("InvitationLifecycleActions", () => {
  it("shows retry for failed invitations when allowed", () => {
    render(
      <InvitationLifecycleActions
        invitation={normalizeInvitationView({ status: "failed", canRetry: true, canResend: true })}
        onResend={async () => undefined}
      />,
    );
    expect(screen.getByText("Retry delivery")).toBeInTheDocument();
  });

  it("hides resend for revoked invitations", () => {
    render(
      <InvitationLifecycleActions
        invitation={normalizeInvitationView({ status: "revoked", canResend: true, canRevoke: true })}
        onResend={async () => undefined}
        onRevoke={async () => undefined}
      />,
    );
    expect(screen.queryByText("Resend invitation")).not.toBeInTheDocument();
    expect(screen.getByText(/Revoked invitations cannot be used/i)).toBeInTheDocument();
  });
});

describe("ImportRowReviewTable", () => {
  it("renders import row invitation status", () => {
    render(
      <ImportRowReviewTable
        rows={[
          {
            id: "row-1",
            rowNumber: 1,
            outcome: "invitation_sent",
            normalizedPayloadJson: JSON.stringify({ email: "uploader@org.example" }),
            invitation: { status: "sent", invitationId: "inv-2", canRevoke: true },
          },
        ]}
      />,
    );
    expect(screen.getByText("uploader@org.example")).toBeInTheDocument();
    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("invitation_sent")).toBeInTheDocument();
  });
});

describe("authorised representative invitation status expectations", () => {
  it("does not claim activation from sent invitation alone", () => {
    const invitation = normalizeInvitationView({ status: "sent", auditStatus: "pending_backend" });
    expect(invitation.status).not.toBe("activated");
  });
});
