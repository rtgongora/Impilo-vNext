/**
 * Bedside clinical calculators.
 *
 * The arithmetic is not here. These hooks fetch a calculator's input schema from the Clinical
 * Knowledge Platform (via BFF /internal/v1/clinical/calculators/**) and post entered values back
 * to be computed there. A browser-side copy of an equation would be a second implementation to
 * drift from the engines that read the same figures — and a page that computes its own numbers is
 * how the clinical-tools surface came to display instruments it was not actually running.
 *
 * Three outcomes, and a surface must tell them apart:
 *
 *  - a RESULT — `computed: true`, with named outputs;
 *  - a REFUSAL — `computed: false`, with a reason and an explanation. This is the calculator
 *    working: an intubated patient has no verbal GCS score, and saying so is the correct answer,
 *    not an error;
 *  - a FAILURE — the request never reached the platform. `isError` covers this, and it must never
 *    be rendered as a blank result, which reads as a normal value.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** How a field is entered, which is all a generic form needs to render it. */
export type CalculatorInputKind = "DECIMAL" | "INTEGER" | "BOOLEAN" | "ENUM" | "ENUM_MULTI";

export interface CalculatorOption {
  value: string;
  label: string;
  description?: string | null;
}

export interface CalculatorInputField {
  key: string;
  label: string;
  kind: CalculatorInputKind;
  /** The unit the value must be entered in. Null where the value has none. */
  unit: string | null;
  /**
   * Whether the calculator can produce anything without it. Optional does not mean unimportant:
   * an omitted albumin changes what a corrected calcium means, and the result says so.
   */
  required: boolean;
  options: CalculatorOption[];
  help: string | null;
}

export interface CalculatorSummary {
  id: string;
  name: string;
  category: string;
  summary: string;
  content_version: string;
}

export interface CalculatorDescriptor extends CalculatorSummary {
  /** The published source. Every calculator names one, and says where the text is not held. */
  source_citation: string;
  inputs: CalculatorInputField[];
  /** Standing warnings about the instrument, shown whether or not it produced a number. */
  caveats: string[];
}

export interface CalculatorOutputValue {
  key: string;
  label: string;
  value: string | number | boolean | string[] | null;
  unit: string | null;
  /** More than one output may be primary — an instrument may genuinely have two answers. */
  primary: boolean;
  note: string | null;
}

export interface CalculatorResult {
  calculator_id: string;
  computed: boolean;
  outputs: CalculatorOutputValue[];
  /** The domain refusal code, e.g. INPUT_MISSING. Null when computed. */
  refusal_reason: string | null;
  /** The sentence a clinician reads. It says what to do next; the bare code does not. */
  refusal_detail: string | null;
  caveats: string[];
  content_version: string;
}

interface CalculatorListResponse {
  calculators: CalculatorSummary[];
  total: number;
}

const CALCULATORS_PATH = "/internal/v1/clinical/calculators";

/**
 * Every calculator the platform serves.
 *
 * Deliberately not cached indefinitely: the catalogue is the list of instruments that actually
 * compute, and a stale copy would reintroduce the defect of advertising one that does not.
 */
export function useCalculatorCatalogue() {
  const query = useQuery({
    queryKey: ["clinical", "calculators"],
    queryFn: () => apiClient.get<ApiResponse<CalculatorListResponse>>(CALCULATORS_PATH),
    staleTime: 1000 * 60 * 10,
  });

  return {
    calculators: query.data?.data?.calculators ?? [],
    total: query.data?.data?.total ?? 0,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  };
}

/**
 * One calculator's input schema, source and caveats.
 *
 * A form renders from this rather than from a hand-written field list, so a field added, renamed
 * or re-united in the domain library cannot silently disagree with what the clinician is asked.
 */
export function useCalculatorDescriptor(calculatorId: string | undefined) {
  const query = useQuery({
    queryKey: ["clinical", "calculators", calculatorId],
    queryFn: () =>
      apiClient.get<ApiResponse<CalculatorDescriptor>>(`${CALCULATORS_PATH}/${calculatorId}`),
    enabled: !!calculatorId,
    staleTime: 1000 * 60 * 10,
  });

  return {
    descriptor: query.data?.data,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  };
}

/**
 * Runs a calculator server-side.
 *
 * A refusal resolves rather than rejects. It is a clinical statement the calculator authored, and
 * routing it through the error path would present a stated limitation as a system fault — and hide
 * the explanation, which is the part the clinician needs.
 *
 * Only a transport or platform failure rejects. `isError` therefore means "we do not know", never
 * "there is no result".
 */
export function useEvaluateCalculator(calculatorId: string | undefined) {
  return useMutation({
    mutationFn: async (inputs: Record<string, unknown>) => {
      const response = await apiClient.post<ApiResponse<CalculatorResult>>(
        `${CALCULATORS_PATH}/${calculatorId}/evaluate`,
        inputs,
      );
      return response.data;
    },
  });
}

/**
 * Only the values a clinician actually entered.
 *
 * Absent is not zero and absent is not false. An empty box means the measurement was not taken,
 * and the calculators distinguish that from a measurement that was taken and was normal — several
 * refuse rather than score an unanswered question as a negative, because the negative reading is
 * the one that understates severity. Sending `""` or a defaulted `false` would destroy that
 * distinction before the request left the browser.
 */
export function submittableInputs(entered: Record<string, unknown>): Record<string, unknown> {
  const submitted: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entered)) {
    if (value === undefined || value === null) continue;
    if (typeof value === "string" && value.trim() === "") continue;
    if (Array.isArray(value) && value.length === 0) {
      // An empty multi-select is a real answer — "assessed, nothing found" — and is sent as such.
      submitted[key] = [];
      continue;
    }
    submitted[key] = value;
  }
  return submitted;
}
