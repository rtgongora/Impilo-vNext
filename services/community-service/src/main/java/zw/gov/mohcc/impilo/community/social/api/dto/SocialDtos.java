package zw.gov.mohcc.impilo.community.social.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO contracts for the social subdomain. Mirrors contracts/openapi/social.openapi.yaml. */
public final class SocialDtos {

    private SocialDtos() {}

    public record IdentityDto(
            String kind, String id, String displayName, String avatarUrl,
            String roleBadge, boolean verified, String facilityName) {
    }

    public record ScopeDto(
            UUID communityId, UUID groupId, UUID pageId, String facilityId) {
    }

    public record ReactionSummaryDto(
            int total, Map<String, Integer> byType, String viewerReaction) {
    }

    public record AttachmentDto(
            String id, String mediaType, String url, String previewText, String kind, Map<String, Object> metadata) {
    }

    public record PostDto(
            UUID id,
            UUID tenantId,
            String kind,
            String status,
            String visibility,
            String title,
            String body,
            List<String> topics,
            IdentityDto author,
            ScopeDto scope,
            List<AttachmentDto> attachments,
            ReactionSummaryDto reactionSummary,
            int commentCount,
            boolean bookmarked,
            boolean pinned,
            String source,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            OffsetDateTime scheduledFor,
            Map<String, Object> nompiloFlags) {
    }

    public record CreatePostRequest(
            String kind,
            String title,
            String body,
            String visibility,
            List<String> topics,
            ScopeDto scope,
            List<AttachmentDto> attachments,
            OffsetDateTime scheduledFor,
            Boolean saveAsDraft,
            String source,
            Map<String, Object> nompiloAssist) {
    }

    public record UpdatePostRequest(
            String title, String body, String visibility,
            List<String> topics, List<AttachmentDto> attachments) {
    }

    public record CommentRequest(String body, UUID parentCommentId) {}

    public record CommentDto(
            UUID id, UUID postId, String body, IdentityDto author,
            UUID parentCommentId, OffsetDateTime createdAt, ReactionSummaryDto reactionSummary) {
    }

    public record ReactionRequest(String type) {}

    public record CommunityDto(
            UUID id, UUID tenantId, String slug, String name, String description,
            String category, String kind, String coverColor, String iconUrl, String coverImageUrl,
            int memberCount, List<String> rules, List<String> moderatorIds,
            String viewerMembership, OffsetDateTime createdAt, boolean nompiloAssistantEnabled) {
    }

    public record CreateCommunityRequest(
            String name, String slug, String description, String category, String kind,
            String coverColor, String iconUrl, String coverImageUrl, List<String> rules) {
    }

    public record GroupDto(
            UUID id, UUID communityId, String name, String description, String category,
            String facilityId, String organisationId, int memberCount, String viewerMembership,
            OffsetDateTime createdAt) {
    }

    public record CreateGroupRequest(
            String name, String description, UUID communityId, String facilityId,
            String organisationId, String category) {
    }

    public record ActionLinkDto(String label, String href, String kind) {}

    public record PageDto(
            UUID id, UUID tenantId, String kind, String name, String slug, String bio,
            String iconUrl, String coverImageUrl, boolean verified, int followerCount,
            List<ActionLinkDto> actionLinks, List<String> adminIds, boolean viewerFollows,
            OffsetDateTime createdAt) {
    }

    public record CreatePageRequest(
            String name, String slug, String kind, String bio, String iconUrl,
            String coverImageUrl, List<ActionLinkDto> actionLinks) {
    }

    public record SuggestionsBundleDto(
            List<CommunityDto> communities,
            List<GroupDto> groups,
            List<PageDto> pages) {
    }

    public record ReportRequest(String reason, String details) {}

    public record ModerationCaseDto(
            UUID id, UUID postId, String reporterActorId, String reason, String details,
            String status, String decision, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
    }

    public record ResolveModerationRequest(String decision, String rationale) {}

    public record FeedPageDto(String scope, List<PostDto> items, String nextCursor) {}
}
