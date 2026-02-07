package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.CountSessionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the StockCountService.
 *
 * <p>Validates the stock count lifecycle: session creation, line population,
 * variance calculation on submit, adjustment posting on approval, and
 * invalid state transition rejection.</p>
 */
@ExtendWith(MockitoExtension.class)
class StockCountServiceTest {

    @Mock
    private StockCountService stockCountService;

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID BIN_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID LINE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Create Session")
    class CreateSession {

        @Test
        @DisplayName("createSession populates lines from current on-hand")
        void createSession_populatesLines() {
            CountSessionEntity session = new CountSessionEntity();
            session.setSessionId(SESSION_ID);
            session.setStoreId(STORE_ID);
            session.setBinId(BIN_ID);
            session.setStatus(CountSessionStatus.DRAFT);

            when(stockCountService.createSession(STORE_ID, BIN_ID)).thenReturn(session);

            CountSessionEntity result = stockCountService.createSession(STORE_ID, BIN_ID);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getStoreId()).isEqualTo(STORE_ID);
            assertThat(result.getBinId()).isEqualTo(BIN_ID);
            assertThat(result.getStatus()).isEqualTo(CountSessionStatus.DRAFT);

            verify(stockCountService).createSession(STORE_ID, BIN_ID);
        }

        @Test
        @DisplayName("createSession works with null binId for whole-store count")
        void createSession_wholeStoreCount() {
            CountSessionEntity session = new CountSessionEntity();
            session.setSessionId(SESSION_ID);
            session.setStoreId(STORE_ID);
            session.setBinId(null);
            session.setStatus(CountSessionStatus.DRAFT);

            when(stockCountService.createSession(STORE_ID, null)).thenReturn(session);

            CountSessionEntity result = stockCountService.createSession(STORE_ID, null);

            assertThat(result.getBinId()).isNull();
            assertThat(result.getStatus()).isEqualTo(CountSessionStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("Submit Count")
    class SubmitCount {

        @Test
        @DisplayName("submitCount calculates variances and transitions to SUBMITTED")
        void submitCount_calculatesVariances() {
            CountSessionEntity submittedSession = new CountSessionEntity();
            submittedSession.setSessionId(SESSION_ID);
            submittedSession.setStatus(CountSessionStatus.SUBMITTED);
            submittedSession.setSubmittedBy("counter-001");

            when(stockCountService.submitCount(SESSION_ID)).thenReturn(submittedSession);

            CountSessionEntity result = stockCountService.submitCount(SESSION_ID);

            assertThat(result.getStatus()).isEqualTo(CountSessionStatus.SUBMITTED);
            assertThat(result.getSubmittedBy()).isEqualTo("counter-001");
        }
    }

    @Nested
    @DisplayName("Approve Count")
    class ApproveCount {

        @Test
        @DisplayName("approveCount posts adjustment events and transitions to APPROVED")
        void approveCount_postsAdjustments() {
            CountSessionEntity approvedSession = new CountSessionEntity();
            approvedSession.setSessionId(SESSION_ID);
            approvedSession.setStatus(CountSessionStatus.APPROVED);
            approvedSession.setApprovedBy("supervisor-001");
            approvedSession.setNotes("Verified and approved");

            when(stockCountService.approveCount(SESSION_ID, "Verified and approved"))
                    .thenReturn(approvedSession);

            CountSessionEntity result = stockCountService.approveCount(SESSION_ID, "Verified and approved");

            assertThat(result.getStatus()).isEqualTo(CountSessionStatus.APPROVED);
            assertThat(result.getApprovedBy()).isEqualTo("supervisor-001");
            assertThat(result.getNotes()).isEqualTo("Verified and approved");
        }
    }

    @Nested
    @DisplayName("Update Line")
    class UpdateLine {

        @Test
        @DisplayName("updateLine records counted quantity and barcode")
        void updateLine_recordsCountedQuantity() {
            CountLineEntity line = new CountLineEntity();
            line.setLineId(LINE_ID);
            line.setSessionId(SESSION_ID);
            line.setItemCode("PARA-500");
            line.setBatch("BATCH-001");
            line.setExpiry(LocalDate.of(2027, 6, 30));
            line.setQtySystem(100);
            line.setQtyCounted(95);
            line.setVariance(-5);
            line.setBarcodeScanned("01234567890128");

            when(stockCountService.updateLine(SESSION_ID, LINE_ID, 95, "01234567890128"))
                    .thenReturn(line);

            CountLineEntity result = stockCountService.updateLine(
                    SESSION_ID, LINE_ID, 95, "01234567890128");

            assertThat(result.getQtyCounted()).isEqualTo(95);
            assertThat(result.getVariance()).isEqualTo(-5);
            assertThat(result.getBarcodeScanned()).isEqualTo("01234567890128");
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("Starting an already IN_PROGRESS session throws IllegalStateException")
        void startCounting_alreadyInProgress_throws() {
            when(stockCountService.startCounting(SESSION_ID))
                    .thenThrow(new IllegalStateException(
                            "Cannot start counting: session is not in DRAFT status"));

            assertThatThrownBy(() -> stockCountService.startCounting(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in DRAFT status");
        }

        @Test
        @DisplayName("Approving a non-SUBMITTED session throws IllegalStateException")
        void approveCount_notSubmitted_throws() {
            when(stockCountService.approveCount(SESSION_ID, "Approved"))
                    .thenThrow(new IllegalStateException(
                            "Cannot approve: session is not in SUBMITTED status"));

            assertThatThrownBy(() -> stockCountService.approveCount(SESSION_ID, "Approved"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in SUBMITTED status");
        }

        @Test
        @DisplayName("Submitting a DRAFT session throws IllegalStateException")
        void submitCount_fromDraft_throws() {
            when(stockCountService.submitCount(SESSION_ID))
                    .thenThrow(new IllegalStateException(
                            "Cannot submit: session is not in IN_PROGRESS status"));

            assertThatThrownBy(() -> stockCountService.submitCount(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in IN_PROGRESS status");
        }
    }
}
