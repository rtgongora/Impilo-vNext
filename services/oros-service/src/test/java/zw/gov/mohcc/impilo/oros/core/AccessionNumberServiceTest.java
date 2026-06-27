package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccessionNumberService} — accession-number format and sequence wiring.
 */
@ExtendWith(MockitoExtension.class)
class AccessionNumberServiceTest {

    @Mock
    private AccessionSequenceAllocator allocator;

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("ab12cd34-0000-4000-8000-000000000002");

    @Test
    void reserve_formatsAccessionWithYearFacilityAndPaddedSequence() {
        AccessionNumberService service = new AccessionNumberService(allocator);
        when(allocator.nextSequence(TENANT, FACILITY, 2026)).thenReturn(42L);

        String accession = service.reserve(TENANT, FACILITY, 2026);

        assertThat(accession).isEqualTo("ACC-2026-AB12CD34-000042");
    }

    @Test
    void reserve_padsToSixDigitsAndIsUppercase() {
        AccessionNumberService service = new AccessionNumberService(allocator);
        when(allocator.nextSequence(TENANT, FACILITY, 2026)).thenReturn(7L);

        assertThat(service.reserve(TENANT, FACILITY, 2026)).isEqualTo("ACC-2026-AB12CD34-000007");
    }

    @Test
    void format_isStableAndUidDerived() {
        assertThat(AccessionNumberService.format(FACILITY, 2030, 123456L))
                .isEqualTo("ACC-2030-AB12CD34-123456");
    }
}
