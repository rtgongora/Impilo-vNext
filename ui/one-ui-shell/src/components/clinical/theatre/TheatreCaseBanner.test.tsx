import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TheatreCaseBanner } from "./TheatreCaseBanner";

describe("TheatreCaseBanner (§19 persistent context banner)", () => {
  it("is a sticky, always-visible header carrying patient identity + stage", () => {
    render(<TheatreCaseBanner patientId="CPID-1" procedureName="Appendectomy" status="IN_PROGRESS" />);
    const banner = screen.getByTestId("theatre-case-banner");
    expect(banner.className).toContain("sticky");
    expect(screen.getByTestId("banner-patient")).toHaveTextContent("CPID-1");
    expect(screen.getByTestId("banner-stage")).toHaveTextContent("In theatre");
  });

  it("surfaces confirmed allergies as a red safety chip (never hidden)", () => {
    render(<TheatreCaseBanner patientId="CPID-1" allergies={["Penicillin", "Latex"]} />);
    const chip = screen.getByTestId("banner-allergies");
    expect(chip).toHaveTextContent("Penicillin");
    expect(chip.className).toContain("red");
  });

  it("shows an explicit AMBER 'unconfirmed' flag when allergies are not confirmed (no fabrication)", () => {
    render(<TheatreCaseBanner patientId="CPID-1" />);
    const chip = screen.getByTestId("banner-allergies");
    expect(chip).toHaveTextContent(/unconfirmed/i);
    expect(chip.className).toContain("amber");
  });

  it("shows NKDA only when explicitly confirmed (distinct from unconfirmed)", () => {
    render(<TheatreCaseBanner patientId="CPID-1" noKnownAllergies />);
    expect(screen.getByTestId("banner-allergies")).toHaveTextContent(/NKDA/i);
  });

  it("surfaces surgical site/side, and flags an unmarked site (wrong-site prevention)", () => {
    const { rerender } = render(<TheatreCaseBanner patientId="CPID-1" surgicalSite="Knee" surgicalSide="Left" />);
    expect(screen.getByTestId("banner-site")).toHaveTextContent("Knee · Left");
    rerender(<TheatreCaseBanner patientId="CPID-1" />);
    const site = screen.getByTestId("banner-site");
    expect(site).toHaveTextContent(/not marked/i);
    expect(site.className).toContain("amber");
  });
});
