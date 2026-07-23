package zw.gov.mohcc.impilo.telemonitoring.core;

import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;

/**
 * OF-B26 seam on OF-B25: callback invoked by {@link ReadingIngestionService} AFTER a
 * reading has completed ingestion (row persisted; quarantine event emitted; SHR
 * hand-off attempted where eligible). Fired for EVERY stored reading — VALID,
 * DEGRADED_VALID and QUARANTINED — because the alert engine needs all three
 * (§14.5 step 15: INVALID-stamped readings are excluded from value-alerting but MAY
 * trigger device-quality alerts).
 *
 * <p>Contract: best-effort and non-blocking — the ingestion service catches every
 * listener throwable; a broken evaluator must never fail or roll back an ingested
 * reading. Deduplicated replays do NOT re-fire.</p>
 */
public interface ReadingCompletionListener {

    void onReadingCompleted(TelemetryReadingEntity reading);
}
