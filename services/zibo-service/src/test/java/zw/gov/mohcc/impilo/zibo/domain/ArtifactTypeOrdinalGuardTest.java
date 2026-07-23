package zw.gov.mohcc.impilo.zibo.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link ArtifactType} ordinals. The enum is persisted by ordinal in
 * historical rows — it is APPEND-ONLY: new values may only be added at the
 * END, never inserted or reordered. If this test fails, a reorder/insert has
 * happened and historical rows are silently corrupted — fix the enum, never
 * this test. (OF-D deliberately added NO new type: medicines ride the
 * existing CODE_SYSTEM machinery.)
 */
class ArtifactTypeOrdinalGuardTest {

    @Test
    void ordinalsAreAppendOnly() {
        assertThat(ArtifactType.CODE_SYSTEM.ordinal()).isEqualTo(0);
        assertThat(ArtifactType.VALUE_SET.ordinal()).isEqualTo(1);
        assertThat(ArtifactType.CONCEPT_MAP.ordinal()).isEqualTo(2);
        assertThat(ArtifactType.NAMING_SYSTEM.ordinal()).isEqualTo(3);
        assertThat(ArtifactType.STRUCTURE_DEFINITION.ordinal()).isEqualTo(4);
        assertThat(ArtifactType.IMPLEMENTATION_GUIDE.ordinal()).isEqualTo(5);
        assertThat(ArtifactType.OBSERVATION_DEFINITION.ordinal()).isEqualTo(6);
        // Values appended AFTER OBSERVATION_DEFINITION are legal; anything that
        // shifts the ordinals above is not.
    }
}
