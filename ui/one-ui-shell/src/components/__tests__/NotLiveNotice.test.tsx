import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NotLiveNotice } from "@/components/common/NotLiveNotice";

describe("NotLiveNotice", () => {
  it("renders the default honest demo-data message", () => {
    render(<NotLiveNotice />);
    expect(screen.getByRole("note")).toHaveTextContent(/Not live yet/i);
    expect(screen.getByRole("note")).toHaveTextContent(/demo/i);
  });

  it("renders custom children", () => {
    render(<NotLiveNotice>Purchase orders are demo data</NotLiveNotice>);
    expect(screen.getByRole("note")).toHaveTextContent("Purchase orders are demo data");
  });
});
