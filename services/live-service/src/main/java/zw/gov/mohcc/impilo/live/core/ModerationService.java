package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventChatMessageEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventQuestionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventChatMessageRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventQuestionRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ModerationService {

    private final LiveEventService eventService;
    private final LiveEventRepository eventRepository;
    private final LiveEventChatMessageRepository chatRepository;
    private final LiveEventQuestionRepository questionRepository;

    public ModerationService(LiveEventService eventService,
                             LiveEventRepository eventRepository,
                             LiveEventChatMessageRepository chatRepository,
                             LiveEventQuestionRepository questionRepository) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.chatRepository = chatRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public LiveEventChatMessageEntity moderateChat(UUID tenantId, UUID eventId, UUID messageId,
                                                    String action, String moderatedBy) {
        eventService.get(tenantId, eventId);
        LiveEventChatMessageEntity message = chatRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Chat message not found"));
        message.setStatus(switch (action.toUpperCase()) {
            case "HIDE" -> "HIDDEN";
            case "DELETE" -> "DELETED";
            default -> throw new IllegalArgumentException("Unknown moderation action: " + action);
        });
        message.setModeratedBy(moderatedBy);
        return chatRepository.save(message);
    }

    @Transactional
    public LiveEventQuestionEntity moderateQuestion(UUID tenantId, UUID eventId, UUID questionId,
                                                     String action, String moderatedBy) {
        eventService.get(tenantId, eventId);
        LiveEventQuestionEntity question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        switch (action.toUpperCase()) {
            case "ANSWER" -> {
                question.setStatus("ANSWERED");
                question.setAnsweredBy(moderatedBy);
                question.setAnsweredAt(OffsetDateTime.now());
            }
            case "REJECT" -> question.setStatus("REJECTED");
            case "PIN" -> question.setPinned(true);
            case "UNPIN" -> question.setPinned(false);
            case "HIDE" -> question.setStatus("HIDDEN");
            default -> throw new IllegalArgumentException("Unknown moderation action: " + action);
        }
        return questionRepository.save(question);
    }

    @Transactional
    public LiveEventEntity disableChat(UUID tenantId, UUID eventId, boolean enabled, String updatedBy) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        event.setChatEnabled(enabled);
        event.setUpdatedBy(updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        return eventRepository.save(event);
    }

    @Transactional
    public LiveEventEntity disableQna(UUID tenantId, UUID eventId, boolean enabled, String updatedBy) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        event.setQnaEnabled(enabled);
        event.setUpdatedBy(updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        return eventRepository.save(event);
    }
}
