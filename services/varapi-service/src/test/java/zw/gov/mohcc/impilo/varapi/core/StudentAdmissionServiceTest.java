package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The order in which admission refuses things.
 *
 * <p>Both preconditions could be checked in either order and the happy path would look identical.
 * The order matters on the UNHAPPY path: a number reserved and then abandoned is a permanent gap in
 * a statutory sequence, and gaps invite "who was 47?" for the rest of that register's life. So
 * completeness and the fee are both settled BEFORE anything is reserved.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentAdmissionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private CouncilFeeGate feeGate;
    @Mock private StudentApplicationService applicationService;
    @InjectMocks private StudentAdmissionService service;

    @Test
    void anIncompleteApplicationIsRefusedBeforeTheFeeIsEvenConsulted() {
        when(applicationService.isComplete(TENANT, 42L)).thenReturn(false);

        assertThatThrownBy(() -> service.admit(TENANT, 1L, 42L, 9L,
                "NCZ_STUDENT", "student-index", "STUDENT_INDEX", "registrar"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("without the evidence the council asked for");

        // No number is reserved, and the fee gate is not even asked: there is nothing to charge for.
        verify(feeGate, never()).requirePayable(any(), anyLong(), anyString());
    }

    @Test
    void anUnsetFeeStopsTheAdmissionRatherThanWavingItThrough() {
        when(applicationService.isComplete(TENANT, 42L)).thenReturn(true);
        when(feeGate.requirePayable(TENANT, 1L, "STUDENT_INDEX"))
                .thenThrow(new CouncilFeeGate.FeeNotChargeableException("STUDENT_INDEX",
                        new CouncilFeeGate.FeeVerdict(CouncilFeeGate.Verdict.NOT_CONFIGURED,
                                null, null, "NCZ-DEC-002")));

        assertThatThrownBy(() -> service.admit(TENANT, 1L, 42L, 9L,
                "NCZ_STUDENT", "student-index", "STUDENT_INDEX", "registrar"))
                .isInstanceOf(CouncilFeeGate.FeeNotChargeableException.class)
                .hasMessageContaining("NCZ-DEC-002");
        // The number sequence is untouched: a fee that cannot be charged must not burn an ordinal.
    }
}
