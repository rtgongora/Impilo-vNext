/**
 * Lab Service — Lab order creation and results retrieval.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/labs/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { LabOrder, LabOrderStatus, LabResult } from "../types";

interface LabOrderResource {
  id: string;
  type: "LabOrder";
  attributes: {
    encounter_id: string;
    patient_id: string;
    test_name: string;
    test_code: string;
    urgency: "ROUTINE" | "URGENT" | "STAT";
    status: LabOrderStatus;
    results?: LabResultResource[];
    ordered_at: string;
    ordered_by: string;
  };
}

interface LabResultResource {
  id: string;
  lab_order_id: string;
  parameter: string;
  value: string;
  unit: string;
  reference_range: string;
  is_abnormal: boolean;
  completed_at: string;
}

function mapLabResult(r: LabResultResource): LabResult {
  return {
    id: r.id,
    labOrderId: r.lab_order_id,
    parameter: r.parameter,
    value: r.value,
    unit: r.unit,
    referenceRange: r.reference_range,
    isAbnormal: r.is_abnormal,
    completedAt: r.completed_at,
  };
}

function mapLabOrder(r: LabOrderResource): LabOrder {
  return {
    id: r.id,
    encounterId: r.attributes.encounter_id,
    patientId: r.attributes.patient_id,
    testName: r.attributes.test_name,
    testCode: r.attributes.test_code,
    urgency: r.attributes.urgency,
    status: r.attributes.status,
    results: r.attributes.results?.map(mapLabResult),
    orderedAt: r.attributes.ordered_at,
    orderedBy: r.attributes.ordered_by,
  };
}

export interface CreateLabOrderInput {
  encounterId: string;
  patientId: string;
  testName: string;
  testCode: string;
  urgency: "ROUTINE" | "URGENT" | "STAT";
}

export async function createLabOrder(input: CreateLabOrderInput): Promise<LabOrder> {
  const response = await apiClient.post<{ data: LabOrderResource }>(
    "/internal/v1/mobile/provider/labs",
    {
      encounter_id: input.encounterId,
      patient_id: input.patientId,
      test_name: input.testName,
      test_code: input.testCode,
      urgency: input.urgency,
    }
  );
  return mapLabOrder(response.data.data);
}

export async function getLabOrdersForEncounter(encounterId: string): Promise<LabOrder[]> {
  const response = await apiClient.get<{ data: LabOrderResource[] }>(
    `/internal/v1/mobile/provider/labs?encounter_id=${encounterId}`
  );
  return response.data.data.map(mapLabOrder);
}

export async function getLabOrdersForPatient(patientId: string): Promise<LabOrder[]> {
  const response = await apiClient.get<{ data: LabOrderResource[] }>(
    `/internal/v1/mobile/provider/labs?patient_id=${patientId}`
  );
  return response.data.data.map(mapLabOrder);
}

export async function getLabOrder(id: string): Promise<LabOrder> {
  const response = await apiClient.get<{ data: LabOrderResource }>(
    `/internal/v1/mobile/provider/labs/${id}`
  );
  return mapLabOrder(response.data.data);
}

export async function cancelLabOrder(id: string): Promise<LabOrder> {
  const response = await apiClient.post<{ data: LabOrderResource }>(
    `/internal/v1/mobile/provider/labs/${id}/cancel`
  );
  return mapLabOrder(response.data.data);
}
