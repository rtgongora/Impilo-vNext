import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RecognisedProviderChipView } from "./RecognisedProviderChip";
import type { RecognitionView } from "@/hooks/queries/useRecognition";

function view(overrides: Partial<RecognitionView> = {}): RecognitionView {
  return {
    recognised: true,
    recognitionClass: "LICENSED_PROVIDER",
    profession: "Medical Doctor",
    cadre: "GP",
    licenceStatus: "ACTIVE",
    ...overrides,
  };
}

describe("RecognisedProviderChipView", () => {
  it("labels a licensed provider as Recognised Health Provider", () => {
    render(<RecognisedProviderChipView recognition={view()} />);
    expect(screen.getByTestId("recognised-provider-chip")).toHaveTextContent(
      "Recognised Health Provider",
    );
  });

  it("labels workforce-only recognition as Recognised Health Worker", () => {
    render(
      <RecognisedProviderChipView
        recognition={view({ recognitionClass: "HEALTH_WORKFORCE", licenceStatus: null })}
      />,
    );
    expect(screen.getByTestId("recognised-provider-chip")).toHaveTextContent(
      "Recognised Health Worker",
    );
  });

  it("labels BOTH as Recognised Health Provider (provider class wins)", () => {
    render(<RecognisedProviderChipView recognition={view({ recognitionClass: "BOTH" })} />);
    expect(screen.getByTestId("recognised-provider-chip")).toHaveTextContent(
      "Recognised Health Provider",
    );
  });

  it("renders nothing when not recognised (fail-quiet, no negative badge)", () => {
    render(
      <RecognisedProviderChipView
        recognition={view({
          recognised: false,
          recognitionClass: null,
          profession: null,
          cadre: null,
          licenceStatus: null,
        })}
      />,
    );
    expect(screen.queryByTestId("recognised-provider-chip")).not.toBeInTheDocument();
  });

  it("renders nothing while data is absent (loading / upstream down)", () => {
    render(<RecognisedProviderChipView recognition={undefined} />);
    expect(screen.queryByTestId("recognised-provider-chip")).not.toBeInTheDocument();
  });
});
