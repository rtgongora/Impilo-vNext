type AnyRecord = Record<string, unknown>;

export function normalizeCitizenLearningSnapshot(payload: AnyRecord) {
  const inProgress = Array.isArray(payload.inProgress) ? payload.inProgress.length : Number(payload.inProgressCount ?? 0);
  const completed = Array.isArray(payload.completed) ? payload.completed.length : Number(payload.completedCount ?? 0);
  const certificates = Array.isArray(payload.certificates) ? payload.certificates.length : Number(payload.certificateCount ?? 0);
  return {
    inProgress: Number.isFinite(inProgress) ? inProgress : 0,
    completed: Number.isFinite(completed) ? completed : 0,
    certificates: Number.isFinite(certificates) ? certificates : 0,
  };
}
