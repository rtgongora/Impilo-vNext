package zw.gov.mohcc.impilo.guidance.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.guidance.core.GuidanceService;
import zw.gov.mohcc.impilo.guidance.events.GuidanceOutboxAppender;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ReminderEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceApiControllerContractSurfaceTest {

    @Mock
    private GuidanceService guidanceService;

    @Mock
    private GuidanceOutboxAppender outboxAppender;

    @InjectMocks
    private GuidanceApiController controller;

    @Test
    void askReturnsGuidanceEnvelopeAndRecordsOutbox() {
        Map<String, Object> answer = Map.of("answer", "Stay hydrated", "sources", List.of());
        when(guidanceService.ask("tenant-1", "actor-1", "How much water?", false)).thenReturn(answer);

        var response = controller.ask("tenant-1", "actor-1", Map.of("question", "How much water?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        verify(outboxAppender).recordAskCompleted(eq("tenant-1"), eq("actor-1"), eq("How much water?"), eq(false), eq(answer));
    }

    @Test
    void remindersReturnsMappedItems() {
        ReminderEntity reminder = org.mockito.Mockito.mock(ReminderEntity.class);
        UUID id = UUID.randomUUID();
        when(reminder.getId()).thenReturn(id);
        when(reminder.getReminderType()).thenReturn("MEDICATION");
        when(reminder.getTitle()).thenReturn("Take meds");
        when(reminder.getDescription()).thenReturn("Morning dose");
        when(reminder.getDueDate()).thenReturn(null);
        when(reminder.getPriority()).thenReturn("HIGH");
        when(reminder.getStatus()).thenReturn("ACTIVE");
        when(guidanceService.getReminders("tenant-1", "actor-1")).thenReturn(List.of(reminder));

        var response = controller.reminders("tenant-1", "actor-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0)).containsEntry("title", "Take meds");
    }
}
