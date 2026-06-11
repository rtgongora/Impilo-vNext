import { apiClient } from "@/lib/api-client";
import { resolveTelemedicinePatientId } from "@/lib/webrtc/dev-test-facility";

export async function sendCallInvite(args: {
  sessionId: string;
  /** Notify citizen patient (provider → patient). */
  patientId?: string;
  /** Notify any online user by Health OS user id (patient → provider). */
  targetUserId?: string;
  callerName?: string;
  callType: "audio" | "video";
}) {
  const patientId = args.patientId
    ? resolveTelemedicinePatientId(args.patientId)
    : undefined;
  await apiClient.post("/internal/v1/telemedicine/calls/invite", {
    sessionId: args.sessionId,
    patientId,
    targetUserId: args.targetUserId,
    callerName: args.callerName,
    callType: args.callType,
  });
}
