import { useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { PrintJobResponse, PrintPriority } from "@/types/vito";
import { cardKeys } from "./useCards";

export interface PrintJobPayload {
  cardId: string;
  templateId?: string;
  priority?: PrintPriority;
}

export const printKeys = {
  all: ["print"] as const,
};

export function useSubmitPrintJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PrintJobPayload) =>
      vitoApiClient.post<PrintJobResponse>("/v1/print/card/job", payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: cardKeys.all });
    },
  });
}
