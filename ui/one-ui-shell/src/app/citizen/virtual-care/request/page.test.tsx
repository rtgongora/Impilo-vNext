import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CitizenVirtualCareRequestPage from "./page";

const { directoryState, createState } = vi.hoisted(() => ({
  directoryState: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  createState: {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: undefined as unknown,
  },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("vh=vh-national-telemedicine"),
}));
vi.mock("@/hooks/queries/useVirtualCare", () => ({
  useVirtualHospitals: () => directoryState,
  useCreateVirtualCareRequest: () => createState,
}));

describe("CitizenVirtualCareRequestPage", () => {
  beforeEach(() => {
    createState.mutate.mockReset();
    createState.isError = false;
    directoryState.isLoading = false;
    directoryState.isError = false;
    directoryState.data = {
      data: {
        virtualHospitals: [
          {
            id: "vh-national-telemedicine",
            name: "National Telemedicine Hospital",
            availability: "REQUESTABLE",
            poolId: "GENERAL_TELEMEDICINE",
          },
          {
            id: "vh-mental-health",
            name: "Virtual Mental Health Unit",
            availability: "COMING_SOON",
          },
        ],
      },
    };
  });

  it("preselects the deep-linked hospital and only offers requestable ones", () => {
    render(<CitizenVirtualCareRequestPage />);

    const select = screen.getByRole("combobox") as HTMLSelectElement;
    expect(select.value).toBe("vh-national-telemedicine");
    // COMING_SOON hospitals are not requestable and never appear as options.
    expect(
      screen.queryByRole("option", { name: "Virtual Mental Health Unit" }),
    ).not.toBeInTheDocument();
  });

  it("blocks a video request until consent is captured, then submits the full payload", async () => {
    const user = userEvent.setup();
    render(<CitizenVirtualCareRequestPage />);

    await user.type(
      screen.getByPlaceholderText(/describe your symptoms/i),
      "Persistent cough for two weeks",
    );
    // Video is the default modality — consent still missing, so submit is disabled.
    expect(screen.getByRole("button", { name: /submit request/i })).toBeDisabled();

    await user.click(screen.getByRole("checkbox"));
    const submit = screen.getByRole("button", { name: /submit request/i });
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() =>
      expect(createState.mutate).toHaveBeenCalledWith(
        {
          virtualHospitalId: "vh-national-telemedicine",
          reason: "Persistent cough for two weeks",
          modality: "video",
          consentGranted: true,
        },
        expect.anything(),
      ),
    );
  });

  it("does not require consent for a secure-message request", async () => {
    const user = userEvent.setup();
    render(<CitizenVirtualCareRequestPage />);

    await user.type(screen.getByPlaceholderText(/describe your symptoms/i), "Refill question");
    await user.click(screen.getByRole("radio", { name: "Secure message" }));

    expect(screen.getByRole("button", { name: /submit request/i })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: /submit request/i }));

    await waitFor(() =>
      expect(createState.mutate).toHaveBeenCalledWith(
        expect.objectContaining({ modality: "message" }),
        expect.anything(),
      ),
    );
  });

  it("shows the honest queued confirmation after a successful submit", async () => {
    const user = userEvent.setup();
    createState.mutate.mockImplementation((_input, opts) =>
      opts?.onSuccess?.({
        data: {
          requestId: "ref-77",
          status: "SUBMITTED",
          virtualHospital: { id: "vh-national-telemedicine", name: "National Telemedicine Hospital" },
          poolId: "GENERAL_TELEMEDICINE",
          message: "Queued for National Telemedicine Hospital — a provider will accept your request.",
        },
      }),
    );
    render(<CitizenVirtualCareRequestPage />);

    await user.type(screen.getByPlaceholderText(/describe your symptoms/i), "Rash on arm");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: /submit request/i }));

    expect(await screen.findByText("Request queued")).toBeInTheDocument();
    expect(
      screen.getByText(/Queued for National Telemedicine Hospital — a provider will accept your request\./),
    ).toBeInTheDocument();
    expect(screen.getByText(/ref-77/)).toBeInTheDocument();
  });
});
