/**
 * Phase G4 — offline-cache partitioning across a work-context switch.
 *
 * The requirement: records cached under one work context must not remain
 * readable once the person is working under a different one ("specially
 * protected records must never become available offline merely from prior
 * online access").
 *
 * `OfflineRecord` carries NO context/facility tag today, so per-context
 * eviction is impossible without a schema change across @impilo/mobile-offline
 * (shared by all 4 mobile apps) plus every write site. What IS possible, and
 * is what this does, is an all-or-nothing partition that is provably lossless:
 *
 *  - `clearAll()` wipes cached collections AND the pending-operation queue AND
 *    unresolved conflicts. Calling it with unsynced work outstanding would
 *    destroy that work — so it is only ever safe when the queue and the
 *    conflict list are both empty.
 *  - Therefore a switch is REFUSED while anything is unsynced, with an explicit
 *    message telling the person to sync first. Refusing is the honest outcome:
 *    the alternatives are silently carrying context A's cache into context B,
 *    or silently deleting the person's unsynced clinical work. Neither is
 *    acceptable.
 *
 * When the offline SDK gains per-record context tagging, this should become a
 * targeted eviction and the sync-first requirement can be dropped.
 */
import { getOfflineStorage } from "@impilo/mobile-offline";

export type PartitionCheck = { safe: true } | { safe: false; reason: string };

/**
 * True only when discarding the local cache would lose nothing. Treats an
 * unconfigured offline store as safe — nothing is cached, so nothing leaks.
 */
export async function canPartitionSafely(): Promise<PartitionCheck> {
  let storage;
  try {
    storage = getOfflineStorage();
  } catch {
    return { safe: true };
  }

  const [queueSize, conflicts] = await Promise.all([storage.getQueueSize(), storage.getConflicts()]);

  if (queueSize > 0) {
    return {
      safe: false,
      reason: `You have ${queueSize} unsynced change${queueSize === 1 ? "" : "s"}. Sync before switching workplace.`,
    };
  }
  if (conflicts.length > 0) {
    return {
      safe: false,
      reason: `You have ${conflicts.length} unresolved conflict${conflicts.length === 1 ? "" : "s"}. Resolve them before switching workplace.`,
    };
  }
  return { safe: true };
}

/**
 * Discards every locally cached record so nothing from the previous work
 * context survives into the next one. Only call after `canPartitionSafely()`
 * returns safe — see this module's header for why.
 */
export async function clearContextScopedCaches(): Promise<void> {
  try {
    await getOfflineStorage().clearAll();
  } catch {
    // Offline store not configured — nothing cached, nothing to partition.
  }
}
