import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ShellTopAccountActions } from "./ShellTopAccountActions";

vi.mock("./ShellNotificationTray", () => ({
  ShellNotificationTray: () => <span data-testid="notif-tray">tray</span>,
}));

describe("ShellTopAccountActions", () => {
  it("renders alerts, notification tray, profile, and sign out in the top strip", () => {
    render(<ShellTopAccountActions />);

    expect(screen.getByTestId("shell-top-account-actions")).toBeInTheDocument();
    expect(screen.getByText("Alerts")).toBeInTheDocument();
    expect(screen.getByTestId("notif-tray")).toBeInTheDocument();
    expect(screen.getByLabelText("Profile")).toBeInTheDocument();
    expect(screen.getByLabelText("Sign out")).toBeInTheDocument();
  });
});
