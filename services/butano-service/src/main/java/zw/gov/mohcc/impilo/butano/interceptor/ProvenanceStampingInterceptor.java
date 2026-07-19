package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import java.util.Date;
import java.util.List;

/**
 * HAPI FHIR interceptor that stamps provenance metadata on every resource write.
 *
 * <p>Every resource written to the BUTANO Shared Health Record is annotated with
 * comprehensive provenance information derived from the TSHEPO trust headers.
 * This creates an immutable audit trail embedded directly in the FHIR resource
 * metadata, ensuring that every clinical data modification can be traced back to
 * its origin.</p>
 *
 * <h3>Provenance tags stamped on every write</h3>
 * <p>All tags use the system configured in {@code butano.provenance.tag-system}
 * (default: {@code https://impilo.gov.zw/provenance}):</p>
 * <ul>
 *   <li><strong>tenant</strong> — from {@code X-Tenant-Id}</li>
 *   <li><strong>facility</strong> — from {@code X-Facility-Id} (if present)</li>
 *   <li><strong>workspace</strong> — from {@code X-Workspace-Id} (if present)</li>
 *   <li><strong>actor</strong> — composite of {@code X-Actor-Id} and {@code X-Actor-Type}</li>
 *   <li><strong>purpose</strong> — from {@code X-Purpose-Of-Use}</li>
 *   <li><strong>correlation</strong> — from {@code X-Correlation-Id}</li>
 *   <li><strong>break-glass</strong> — tagged if {@code X-Decision} contains "BREAK_GLASS"</li>
 * </ul>
 *
 * <h3>Additional metadata</h3>
 * <ul>
 *   <li>{@code meta.source} — set to {@code urn:impilo:butano:{tenantId}}</li>
 *   <li>{@code meta.lastUpdated} — set to current server time</li>
 * </ul>
 *
 * <p>Provenance tags are additive on updates — previous provenance tags are preserved
 * and new ones are added. This creates a full history of who touched the resource.
 * However, the most recent provenance values overwrite tags of the same code prefix
 * to prevent unbounded tag growth.</p>
 */
@Interceptor
@Component
public class ProvenanceStampingInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceStampingInterceptor.class);

    // Provenance tag code prefixes
    private static final String TAG_TENANT      = "tenant";
    private static final String TAG_FACILITY    = "facility";
    private static final String TAG_WORKSPACE   = "workspace";
    private static final String TAG_ACTOR       = "actor";
    private static final String TAG_PURPOSE     = "purpose";
    private static final String TAG_CORRELATION = "correlation";
    private static final String TAG_BREAK_GLASS = "break-glass";
    private static final String TAG_MODE        = "mode";

    private static final String BREAK_GLASS_VALUE = "BREAK_GLASS";

    private final ButanoProperties properties;

    public ProvenanceStampingInterceptor(ButanoProperties properties) {
        this.properties = properties;
    }

    /**
     * Stamps provenance metadata on newly created resources.
     */
    // Hook params must use the pointcut's declared types (IBaseResource); HAPI
    // binds by type and passes null otherwise.
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void onResourceCreated(IBaseResource theResource, RequestDetails requestDetails) {
        if (theResource instanceof Resource resource) {
            stampProvenance(requestDetails, resource);
        }
    }

    /**
     * Stamps provenance metadata on updated resources.
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void onResourceUpdated(IBaseResource theOldResource, IBaseResource theNewResource, RequestDetails requestDetails) {
        if (theNewResource instanceof Resource newResource) {
            stampProvenance(requestDetails, newResource);
        }
    }

    // ── Core stamping logic ─────────────────────────────────────────────

    /**
     * Applies all provenance tags and metadata to the given resource.
     *
     * <p>Reads trust header values from the request's user data (populated by
     * {@link HeaderValidationInterceptor}) and stamps corresponding provenance
     * tags on the resource's metadata.</p>
     */
    private void stampProvenance(RequestDetails requestDetails, Resource resource) {
        String tagSystem = properties.getProvenance().getTagSystem();
        Meta meta = resource.getMeta();

        // Extract trust header values from request user data
        String tenantId = getUserData(requestDetails, HeaderValidationInterceptor.UD_TENANT_ID);
        String facilityId = getUserData(requestDetails, HeaderValidationInterceptor.UD_FACILITY_ID);
        String workspaceId = getUserData(requestDetails, HeaderValidationInterceptor.UD_WORKSPACE_ID);
        String actorId = getUserData(requestDetails, HeaderValidationInterceptor.UD_ACTOR_ID);
        String actorType = getUserData(requestDetails, HeaderValidationInterceptor.UD_ACTOR_TYPE);
        String purposeOfUse = getUserData(requestDetails, HeaderValidationInterceptor.UD_PURPOSE_OF_USE);
        String correlationId = getUserData(requestDetails, HeaderValidationInterceptor.UD_CORRELATION_ID);
        String decision = getUserData(requestDetails, HeaderValidationInterceptor.UD_DECISION);
        Boolean breakGlass = getBreakGlass(requestDetails);
        String connectivityMode = getUserData(requestDetails, HeaderValidationInterceptor.UD_CONNECTIVITY_MODE);

        // Remove existing provenance tags of the same type to prevent unbounded growth
        List<Coding> existingTags = meta.getTag();
        existingTags.removeIf(tag -> tagSystem.equals(tag.getSystem()) && (
                tag.getCode() != null && (
                        tag.getCode().startsWith(TAG_TENANT + ":") ||
                        tag.getCode().startsWith(TAG_FACILITY + ":") ||
                        tag.getCode().startsWith(TAG_WORKSPACE + ":") ||
                        tag.getCode().startsWith(TAG_ACTOR + ":") ||
                        tag.getCode().startsWith(TAG_PURPOSE + ":") ||
                        tag.getCode().startsWith(TAG_CORRELATION + ":") ||
                        tag.getCode().equals(TAG_BREAK_GLASS) ||
                        tag.getCode().startsWith(TAG_MODE + ":")
                )
        ));

        // Stamp tenant tag
        if (tenantId != null && !tenantId.isBlank()) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_TENANT + ":" + tenantId)
                    .setDisplay("Tenant: " + tenantId);
        }

        // Stamp facility tag (optional — not all requests originate from a facility)
        if (facilityId != null && !facilityId.isBlank()) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_FACILITY + ":" + facilityId)
                    .setDisplay("Facility: " + facilityId);
        }

        // Stamp workspace tag (optional — not all requests have a workspace context)
        if (workspaceId != null && !workspaceId.isBlank()) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_WORKSPACE + ":" + workspaceId)
                    .setDisplay("Workspace: " + workspaceId);
        }

        // Stamp actor tag (composite: actorId + actorType)
        if (actorId != null && !actorId.isBlank()) {
            String actorCode = TAG_ACTOR + ":" + actorId;
            String actorDisplay = "Actor: " + actorId;
            if (actorType != null && !actorType.isBlank()) {
                actorCode += "/" + actorType;
                actorDisplay += " (" + actorType + ")";
            }
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(actorCode)
                    .setDisplay(actorDisplay);
        }

        // Stamp purpose of use tag
        if (purposeOfUse != null && !purposeOfUse.isBlank()) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_PURPOSE + ":" + purposeOfUse)
                    .setDisplay("Purpose: " + purposeOfUse);
        }

        // Stamp correlation tag
        if (correlationId != null && !correlationId.isBlank()) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_CORRELATION + ":" + correlationId)
                    .setDisplay("Correlation: " + correlationId);
        }

        // Stamp break-glass tag if this is a break-glass request
        if (Boolean.TRUE.equals(breakGlass)) {
            meta.addTag()
                    .setSystem(tagSystem)
                    .setCode(TAG_BREAK_GLASS)
                    .setDisplay("Break-Glass Override — requires post-hoc audit review");
        }

        // Stamp connectivity mode tag (ONLINE, DEGRADED, or OFFLINE)
        String mode = (connectivityMode != null && !connectivityMode.isBlank())
                ? connectivityMode.toUpperCase()
                : "ONLINE";
        meta.addTag()
                .setSystem(tagSystem)
                .setCode(TAG_MODE + ":" + mode)
                .setDisplay("Mode: " + mode);

        // Set meta.source to the BUTANO tenant URN
        if (tenantId != null && !tenantId.isBlank()) {
            meta.setSource("urn:impilo:butano:" + tenantId);
        } else {
            meta.setSource("urn:impilo:butano:unknown");
        }

        // Set meta.lastUpdated to current server time
        meta.setLastUpdated(new Date());

        log.debug("Provenance stamped on {}/{} — tenant={}, actor={}, correlation={}, breakGlass={}, mode={}",
                resource.fhirType(),
                resource.getIdElement() != null ? resource.getIdElement().getIdPart() : "new",
                tenantId, actorId, correlationId, breakGlass, mode);
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Safely extracts a String value from request user data.
     */
    private String getUserData(RequestDetails requestDetails, String key) {
        Object value = requestDetails.getUserData().get(key);
        return value instanceof String s ? s : null;
    }

    /**
     * Extracts the break-glass flag from request user data.
     */
    private Boolean getBreakGlass(RequestDetails requestDetails) {
        Object value = requestDetails.getUserData().get(HeaderValidationInterceptor.UD_BREAK_GLASS);
        return value instanceof Boolean b ? b : false;
    }
}
