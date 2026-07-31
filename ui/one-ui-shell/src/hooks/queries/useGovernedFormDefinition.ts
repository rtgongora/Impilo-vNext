import { useMemo } from "react";
import { useFormCatalog, type FormCatalogEntry } from "@/hooks/queries/useEncounterForms";
import type { ClinicalFormDefinition } from "@/lib/clinical-forms/types";

export function useGovernedFormDefinition(formKey: string) {
  const catalog = useFormCatalog();
  const entry = useMemo((): FormCatalogEntry | null => {
    for (const e of catalog.data?.data ?? []) {
      if (e.formKey === formKey) return e;
    }
    return null;
  }, [catalog.data, formKey]);
  const definition = useMemo((): ClinicalFormDefinition | null => {
    if (!entry) return null;
    try {
      return JSON.parse(entry.definitionJson) as ClinicalFormDefinition;
    } catch {
      return null;
    }
  }, [entry]);
  return { isLoading: catalog.isLoading, isError: catalog.isError, definition, catalogEntry: entry };
}
