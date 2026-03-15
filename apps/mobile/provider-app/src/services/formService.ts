/**
 * Form Service — Dynamic forms engine.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/forms/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { FormSchema, FormSubmission } from "../types";

export async function getFormSchemas(category?: string): Promise<FormSchema[]> {
  const params = category ? `?category=${encodeURIComponent(category)}` : "";
  const response = await apiClient.get<{ data: FormSchema[] }>(
    `/internal/v1/mobile/provider/forms${params}`
  );
  return response.data.data;
}

export async function getFormSchema(id: string): Promise<FormSchema> {
  const response = await apiClient.get<{ data: FormSchema }>(
    `/internal/v1/mobile/provider/forms/${id}`
  );
  return response.data.data;
}

export async function submitForm(input: {
  formSchemaId: string;
  encounterId: string;
  patientId: string;
  data: Record<string, unknown>;
}): Promise<FormSubmission> {
  const response = await apiClient.post<{ data: FormSubmission }>(
    "/internal/v1/mobile/provider/forms/submissions",
    {
      form_schema_id: input.formSchemaId,
      encounter_id: input.encounterId,
      patient_id: input.patientId,
      data: input.data,
    }
  );
  return response.data.data;
}

export async function getFormSubmissions(encounterId: string): Promise<FormSubmission[]> {
  const response = await apiClient.get<{ data: FormSubmission[] }>(
    `/internal/v1/mobile/provider/forms/submissions?encounter_id=${encounterId}`
  );
  return response.data.data;
}
