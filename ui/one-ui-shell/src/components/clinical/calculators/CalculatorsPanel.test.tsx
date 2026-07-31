import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CalculatorsPanel } from "./CalculatorsPanel";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const CATALOGUE = {
  data: {
    calculators: [
      {
        id: "egfr-ckd-epi-2021",
        name: "eGFR (CKD-EPI 2021)",
        category: "Renal",
        summary: "Estimated glomerular filtration rate.",
        content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
      },
    ],
    total: 1,
  },
};

const DESCRIPTOR = {
  data: {
    id: "egfr-ckd-epi-2021",
    name: "eGFR (CKD-EPI 2021)",
    category: "Renal",
    summary: "Estimated glomerular filtration rate.",
    source_citation: "Inker LA, et al. N Engl J Med 2021. Primary text not vendored in this system.",
    content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
    caveats: ["Not valid while the creatinine is still moving, as in acute kidney injury."],
    inputs: [
      { key: "creatinine_umol_l", label: "Serum creatinine", kind: "DECIMAL", unit: "µmol/L", required: true, options: [], help: null },
      { key: "age_years", label: "Age", kind: "INTEGER", unit: "years", required: true, options: [], help: null },
      { key: "sex", label: "Sex", kind: "ENUM", unit: null, required: true, help: null, options: [{ value: "FEMALE", label: "Female" }, { value: "MALE", label: "Male" }] },
    ],
  },
};

function mockGet(overrides: Record<string, unknown> = {}) {
  get.mockImplementation((url: string) => {
    if (url in overrides) return overrides[url] as Promise<unknown>;
    if (url === "/internal/v1/clinical/calculators") return Promise.resolve(CATALOGUE);
    if (url === "/internal/v1/clinical/calculators/egfr-ckd-epi-2021") return Promise.resolve(DESCRIPTOR);
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
}

describe("CalculatorsPanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists what the platform serves rather than a local array", async () => {
    mockGet();
    render(<CalculatorsPanel />, { wrapper });

    expect(await screen.findByText("eGFR (CKD-EPI 2021)")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/clinical/calculators");
  });

  it("never claims a calculator is validated", async () => {
    mockGet();
    render(<CalculatorsPanel />, { wrapper });
    await screen.findByText("eGFR (CKD-EPI 2021)");

    // The badge that started all this. A surface may say where a number came from; it may not
    // assert an approval nobody granted.
    expect(screen.queryByText(/^Validated$/)).not.toBeInTheDocument();
  });

  it("says the catalogue is unknown rather than showing an empty list when it cannot be loaded", async () => {
    get.mockRejectedValue(new Error("gateway"));
    render(<CalculatorsPanel />, { wrapper });

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/could not be loaded/i);
    // The distinction that matters: unknown is not the same as none.
    expect(alert).toHaveTextContent(/not because there are none/i);
  });

  it("renders a form from the descriptor and posts the entered values to be computed", async () => {
    mockGet();
    post.mockResolvedValue({
      data: {
        calculator_id: "egfr-ckd-epi-2021",
        computed: true,
        outputs: [
          { key: "egfr", label: "eGFR", value: 78, unit: "mL/min/1.73m²", primary: true, note: null },
          { key: "kdigo_stage", label: "KDIGO GFR category", value: "G2", unit: null, primary: true, note: null },
        ],
        refusal_reason: null,
        refusal_detail: null,
        caveats: DESCRIPTOR.data.caveats,
        content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
      },
    });

    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));

    await user.type(await screen.findByLabelText(/Serum creatinine/), "90");
    await user.type(screen.getByLabelText(/Age/), "54");
    await user.selectOptions(screen.getByLabelText(/Sex/), "FEMALE");
    await user.click(screen.getByRole("button", { name: /calculate/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/clinical/calculators/egfr-ckd-epi-2021/evaluate", {
        creatinine_umol_l: "90",
        age_years: "54",
        sex: "FEMALE",
      }),
    );
    expect(await screen.findByText("78")).toBeInTheDocument();
    expect(screen.getByText("G2")).toBeInTheDocument();
  });

  it("omits an empty field entirely so the platform can tell not-measured from zero", async () => {
    mockGet();
    post.mockResolvedValue({
      data: {
        calculator_id: "egfr-ckd-epi-2021",
        computed: false,
        outputs: [],
        refusal_reason: "INPUT_MISSING",
        refusal_detail: "Serum creatinine is not recorded.",
        caveats: [],
        content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
      },
    });

    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));
    await user.type(await screen.findByLabelText(/Age/), "54");
    await user.click(screen.getByRole("button", { name: /calculate/i }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post.mock.calls[0][1]).toEqual({ age_years: "54" });
    expect(post.mock.calls[0][1]).not.toHaveProperty("creatinine_umol_l");
  });

  it("shows a refusal as the calculator's own answer, with its explanation, not as an error", async () => {
    mockGet();
    post.mockResolvedValue({
      data: {
        calculator_id: "egfr-ckd-epi-2021",
        computed: false,
        outputs: [],
        refusal_reason: "INPUT_OUT_OF_PHYSIOLOGICAL_RANGE",
        refusal_detail: "A creatinine of 1.1 µmol/L is implausible; this is usually a mg/dL value.",
        caveats: [],
        content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
      },
    });

    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));
    await user.type(await screen.findByLabelText(/Serum creatinine/), "1.1");
    await user.click(screen.getByRole("button", { name: /calculate/i }));

    // The explanation is what tells a clinician what to do next; the bare code does not.
    expect(await screen.findByText(/usually a mg\/dL value/)).toBeInTheDocument();
    expect(screen.getByText("INPUT_OUT_OF_PHYSIOLOGICAL_RANGE")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("distinguishes a platform failure from a refusal and shows no number", async () => {
    mockGet();
    post.mockRejectedValue(new Error("bad gateway"));

    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));
    await user.type(await screen.findByLabelText(/Serum creatinine/), "90");
    await user.click(screen.getByRole("button", { name: /calculate/i }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/could not be run/i);
    expect(alert).toHaveTextContent(/not a normal one and not a reassuring one/i);
  });

  it("clears a stale answer when an input changes under it", async () => {
    mockGet();
    post.mockResolvedValue({
      data: {
        calculator_id: "egfr-ckd-epi-2021",
        computed: true,
        outputs: [{ key: "egfr", label: "eGFR", value: 78, unit: "mL/min/1.73m²", primary: true, note: null }],
        refusal_reason: null,
        refusal_detail: null,
        caveats: [],
        content_version: "impilo-egfr-ckd-epi-2021-1.0.0",
      },
    });

    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));
    await user.type(await screen.findByLabelText(/Serum creatinine/), "90");
    await user.click(screen.getByRole("button", { name: /calculate/i }));
    expect(await screen.findByText("78")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Age/), "5");

    await waitFor(() => expect(screen.queryByText("78")).not.toBeInTheDocument());
  });

  it("shows the source and content version alongside the form", async () => {
    mockGet();
    const user = userEvent.setup();
    render(<CalculatorsPanel />, { wrapper });
    await user.click(await screen.findByText("eGFR (CKD-EPI 2021)"));

    expect(await screen.findByText(/not vendored in this system/)).toBeInTheDocument();
    expect(screen.getByText("impilo-egfr-ckd-epi-2021-1.0.0")).toBeInTheDocument();
  });

  it("points at the persisted APGAR rather than offering a detached copy", async () => {
    mockGet();
    render(<CalculatorsPanel />, { wrapper });

    expect(await screen.findByText("APGAR score")).toBeInTheDocument();
    expect(screen.getByText(/persisted and timed/)).toBeInTheDocument();
  });
});
