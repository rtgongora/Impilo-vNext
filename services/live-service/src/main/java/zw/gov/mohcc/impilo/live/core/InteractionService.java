package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.*;
import zw.gov.mohcc.impilo.live.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InteractionService {

    private final LiveEventService eventService;
    private final LiveEventQuestionRepository questionRepository;
    private final LiveEventChatMessageRepository chatRepository;
    private final LiveEventPollRepository pollRepository;
    private final LiveEventPollResponseRepository pollResponseRepository;
    private final LiveEventResourceRepository resourceRepository;
    private final LiveEventFeedbackRepository feedbackRepository;

    public InteractionService(LiveEventService eventService,
                              LiveEventQuestionRepository questionRepository,
                              LiveEventChatMessageRepository chatRepository,
                              LiveEventPollRepository pollRepository,
                              LiveEventPollResponseRepository pollResponseRepository,
                              LiveEventResourceRepository resourceRepository,
                              LiveEventFeedbackRepository feedbackRepository) {
        this.eventService = eventService;
        this.questionRepository = questionRepository;
        this.chatRepository = chatRepository;
        this.pollRepository = pollRepository;
        this.pollResponseRepository = pollResponseRepository;
        this.resourceRepository = resourceRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public LiveEventQuestionEntity submitQuestion(UUID tenantId, UUID eventId,
                                                   String participantId, String participantType,
                                                   String questionText, boolean anonymousAllowed) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (!event.isQnaEnabled()) {
            throw new IllegalStateException("Q&A is disabled for this event");
        }
        LiveEventQuestionEntity q = new LiveEventQuestionEntity();
        q.setEventId(eventId);
        q.setParticipantId(participantId);
        q.setParticipantType(participantType);
        q.setQuestionText(questionText);
        q.setAnonymousAllowed(anonymousAllowed);
        q.setStatus("PENDING");
        return questionRepository.save(q);
    }

    @Transactional(readOnly = true)
    public List<LiveEventQuestionEntity> listQuestions(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return questionRepository.findByEventIdOrderByPinnedDescUpvotesDescCreatedAtAsc(eventId);
    }

    @Transactional
    public LiveEventQuestionEntity upvoteQuestion(UUID tenantId, UUID eventId, UUID questionId) {
        eventService.get(tenantId, eventId);
        LiveEventQuestionEntity q = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        q.setUpvotes(q.getUpvotes() + 1);
        return questionRepository.save(q);
    }

    @Transactional
    public LiveEventChatMessageEntity postChat(UUID tenantId, UUID eventId,
                                                String participantId, String participantType,
                                                String message) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (!event.isChatEnabled()) {
            throw new IllegalStateException("Chat is disabled for this event");
        }
        LiveEventChatMessageEntity chat = new LiveEventChatMessageEntity();
        chat.setEventId(eventId);
        chat.setParticipantId(participantId);
        chat.setParticipantType(participantType);
        chat.setMessage(message);
        chat.setStatus("VISIBLE");
        return chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public List<LiveEventChatMessageEntity> listChat(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return chatRepository.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, "VISIBLE");
    }

    @Transactional
    public LiveEventPollEntity createPoll(UUID tenantId, UUID eventId, String question,
                                          String optionsJson, String createdBy) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (!event.isPollsEnabled()) {
            throw new IllegalStateException("Polls are disabled for this event");
        }
        LiveEventPollEntity poll = new LiveEventPollEntity();
        poll.setEventId(eventId);
        poll.setQuestion(question);
        poll.setOptions(optionsJson != null ? optionsJson : "[]");
        poll.setCreatedBy(createdBy);
        poll.setStatus("DRAFT");
        return pollRepository.save(poll);
    }

    @Transactional
    public LiveEventPollEntity activatePoll(UUID tenantId, UUID eventId, UUID pollId) {
        eventService.get(tenantId, eventId);
        LiveEventPollEntity poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        poll.setStatus("ACTIVE");
        return pollRepository.save(poll);
    }

    @Transactional
    public LiveEventPollResponseEntity respondToPoll(UUID tenantId, UUID eventId, UUID pollId,
                                                      String participantId, String selectedOption) {
        eventService.get(tenantId, eventId);
        LiveEventPollEntity poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        if (!"ACTIVE".equals(poll.getStatus())) {
            throw new IllegalStateException("Poll is not active");
        }
        LiveEventPollResponseEntity response = pollResponseRepository
                .findByPollIdAndParticipantId(pollId, participantId)
                .orElseGet(LiveEventPollResponseEntity::new);
        response.setPollId(pollId);
        response.setParticipantId(participantId);
        response.setSelectedOption(selectedOption);
        return pollResponseRepository.save(response);
    }

    @Transactional(readOnly = true)
    public List<LiveEventPollEntity> listPolls(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return pollRepository.findByEventIdOrderByCreatedAtDesc(eventId);
    }

    @Transactional
    public LiveEventResourceEntity addResource(UUID tenantId, UUID eventId, String title,
                                                String resourceType, String fileId, String visibility) {
        eventService.get(tenantId, eventId);
        LiveEventResourceEntity resource = new LiveEventResourceEntity();
        resource.setEventId(eventId);
        resource.setTitle(title);
        resource.setResourceType(resourceType);
        resource.setFileId(fileId);
        resource.setVisibility(visibility != null ? visibility : "ATTENDEES");
        return resourceRepository.save(resource);
    }

    @Transactional(readOnly = true)
    public List<LiveEventResourceEntity> listResources(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return resourceRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    @Transactional
    public LiveEventFeedbackEntity submitFeedback(UUID tenantId, UUID eventId,
                                                   String participantId, Integer rating, String comments) {
        eventService.get(tenantId, eventId);
        LiveEventFeedbackEntity feedback = feedbackRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseGet(LiveEventFeedbackEntity::new);
        feedback.setEventId(eventId);
        feedback.setParticipantId(participantId);
        feedback.setRating(rating);
        feedback.setComments(comments);
        feedback.setSubmittedAt(OffsetDateTime.now());
        return feedbackRepository.save(feedback);
    }
}
