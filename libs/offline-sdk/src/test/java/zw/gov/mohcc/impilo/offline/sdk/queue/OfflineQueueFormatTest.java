package zw.gov.mohcc.impilo.offline.sdk.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the offline queue format records.
 */
class OfflineQueueFormatTest {

    @Test
    @DisplayName("OfflineActionEntry enforces required fields")
    void actionEntryRequiresFields() {
        assertThatThrownBy(() -> new OfflineActionEntry(
                null, UUID.randomUUID(), "token", "actor", UUID.randomUUID(),
                UUID.randomUUID(), "PAT-1", "VITAL_SIGN",
                Map.of("code", "8867-4"), Instant.now(),
                "device-1", "fp-1", 1, "idem-1", "corr-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entryId");
    }

    @Test
    @DisplayName("OfflineActionEntry rejects sequenceNum < 1")
    void actionEntryRejectsInvalidSequence() {
        assertThatThrownBy(() -> new OfflineActionEntry(
                UUID.randomUUID(), UUID.randomUUID(), "token", "actor",
                UUID.randomUUID(), UUID.randomUUID(), "PAT-1", "VITAL_SIGN",
                Map.of("code", "8867-4"), Instant.now(),
                "device-1", "fp-1", 0, "idem-1", "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequenceNum");
    }

    @Test
    @DisplayName("OfflineSyncBatch rejects empty entries list")
    void syncBatchRejectsEmpty() {
        assertThatThrownBy(() -> new OfflineSyncBatch(
                UUID.randomUUID(), UUID.randomUUID(), "token", "device-1",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entries must not be empty");
    }

    @Test
    @DisplayName("OfflineSyncBatch creates immutable copy of entries")
    void syncBatchImmutableEntries() {
        OfflineActionEntry entry = new OfflineActionEntry(
                UUID.randomUUID(), UUID.randomUUID(), "token", "actor",
                UUID.randomUUID(), UUID.randomUUID(), "PAT-1", "CAPTURE_VITALS",
                Map.of("systolic", 120, "diastolic", 80), Instant.now(),
                "device-1", "fp-1", 1, "idem-1", "corr-1");

        OfflineSyncBatch batch = new OfflineSyncBatch(
                UUID.randomUUID(), UUID.randomUUID(), "signed-token", "device-1",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), List.of(entry));

        assertThat(batch.entries()).hasSize(1);
        assertThat(batch.entries().get(0).patientRef()).isEqualTo("PAT-1");
    }
}
