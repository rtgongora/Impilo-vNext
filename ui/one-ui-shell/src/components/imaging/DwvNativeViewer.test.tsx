import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { DwvNativeViewer } from "@/components/imaging/DwvNativeViewer";

vi.mock("@/lib/imaging/loadDwv", () => ({
  loadDwvModule: () => Promise.reject(new Error("DWV is only available in the browser")),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    getBlob: vi.fn(),
  },
}));

describe("DwvNativeViewer", () => {
  it("shows an error when DWV cannot initialise in non-browser test env", async () => {
    render(
      <DwvNativeViewer studyUid="1.2.3" seriesUid="1.2.4" sopInstanceUid="1.2.5" />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(/DWV is only available in the browser|Failed to initialise DWV viewer/i);
  });

  it("renders the DWV layer host container", () => {
    render(
      <DwvNativeViewer studyUid="1.2.3" seriesUid="1.2.4" sopInstanceUid="1.2.5" />,
    );
    expect(screen.getByTestId("dwv-native-layer-host")).toBeInTheDocument();
  });
});
