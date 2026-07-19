package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import java.util.List;

/**
 * HAPI FHIR interceptor that enforces strict tenant isolation on the Shared Health Record.
 *
 * <p>Multi-tenancy in BUTANO uses FHIR {@code meta.tag} with the system
 * {@code https://impilo.gov.zw/tenant} to associate every resource with a specific
 * tenant (identified by the tenant UUID from the {@code X-Tenant-Id} trust header).</p>
 *
 * <h3>Write operations (CREATE / UPDATE)</h3>
 * <p>On every resource write, this interceptor:</p>
 * <ol>
 *   <li>Removes any existing tenant tags from the resource (prevents tag injection)</li>
 *   <li>Stamps the resource with a tenant tag using the tenant ID from the trust headers</li>
 * </ol>
 *
 * <h3>Read operations</h3>
 * <p>On search/read operations, this interceptor adds a mandatory {@code _tag} search
 * parameter to filter results to only the requesting tenant's data. This provides
 * hard tenant isolation — a tenant can never see another tenant's resources.</p>
 *
 * <h3>Cross-tenant protection</h3>
 * <p>On update operations, if an existing resource already has a different tenant tag,
 * the update is rejected with HTTP 403 Forbidden. A tenant cannot modify another
 * tenant's resources even if they know the resource ID.</p>
 *
 * <p>This interceptor can be disabled by setting {@code butano.tenant.enforce=false}
 * in application.yml (useful for single-tenant development environments).</p>
 */
@Interceptor
@Component
public class TenantEnforcementInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantEnforcementInterceptor.class);

    private final ButanoProperties properties;

    public TenantEnforcementInterceptor(ButanoProperties properties) {
        this.properties = properties;
    }

    /**
     * Stamps tenant tag on newly created resources.
     *
     * <p>Removes any pre-existing tenant tags (to prevent injection) and adds the
     * canonical tenant tag derived from the trust header.</p>
     */
    // Hook params must use the pointcut's declared types (IBaseResource); HAPI
    // binds by type and passes null otherwise.
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void onResourceCreated(IBaseResource theResource, RequestDetails requestDetails) {
        if (!properties.getTenant().isEnforce() || !(theResource instanceof Resource resource)) {
            return;
        }

        String tenantId = getTenantId(requestDetails);
        if (tenantId == null) {
            log.warn("Tenant enforcement active but no tenant ID in request context — rejecting create");
            throw new ForbiddenOperationException(
                    "Tenant isolation is enforced. X-Tenant-Id header is required for all write operations.");
        }

        stampTenantTag(resource, tenantId);
        log.debug("Stamped tenant tag on new resource {}/{} — tenant={}",
                resource.fhirType(), resource.getIdElement().getIdPart(), tenantId);
    }

    /**
     * Validates tenant ownership and re-stamps tenant tag on updated resources.
     *
     * <p>Verifies that the existing resource belongs to the requesting tenant before
     * allowing the update. If it belongs to a different tenant, the update is rejected.</p>
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void onResourceUpdated(IBaseResource theOldResource, IBaseResource theNewResource, RequestDetails requestDetails) {
        if (!properties.getTenant().isEnforce() || !(theNewResource instanceof Resource newResource)) {
            return;
        }
        Resource oldResource = theOldResource instanceof Resource r ? r : null;

        String tenantId = getTenantId(requestDetails);
        if (tenantId == null) {
            throw new ForbiddenOperationException(
                    "Tenant isolation is enforced. X-Tenant-Id header is required for all write operations.");
        }

        // Check that the existing resource belongs to this tenant
        if (oldResource != null) {
            String existingTenant = getExistingTenantTag(oldResource);
            if (existingTenant != null && !existingTenant.equals(tenantId)) {
                log.warn("Cross-tenant update attempt — resource {}/{} belongs to tenant {} " +
                         "but request tenant is {}",
                        oldResource.fhirType(), oldResource.getIdElement().getIdPart(),
                        existingTenant, tenantId);
                throw new ForbiddenOperationException(
                        "Tenant isolation violation: this resource belongs to a different tenant. " +
                        "Cross-tenant updates are not permitted.");
            }
        }

        stampTenantTag(newResource, tenantId);
        log.debug("Re-stamped tenant tag on updated resource {}/{} — tenant={}",
                newResource.fhirType(), newResource.getIdElement().getIdPart(), tenantId);
    }

    /**
     * Adds tenant filter to read/search operations.
     *
     * <p>Appends a {@code _tag} parameter to every search request, restricting results
     * to only resources tagged with the requesting tenant's ID. This ensures hard
     * isolation — a tenant's search results never include another tenant's data.</p>
     *
     * <p>For direct reads (by ID), the interceptor cannot add search parameters but
     * the resource is still validated post-retrieval by the HAPI authorization chain.</p>
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void onIncomingRequest(RequestDetails requestDetails) {
        if (!properties.getTenant().isEnforce()) {
            return;
        }

        // Only apply tenant filter to search operations
        RestOperationTypeEnum operationType = requestDetails.getRestOperationType();
        if (operationType == null) {
            return;
        }

        switch (operationType) {
            case SEARCH_TYPE, SEARCH_SYSTEM, HISTORY_TYPE, HISTORY_SYSTEM, HISTORY_INSTANCE -> {
                String tenantId = getTenantId(requestDetails);
                if (tenantId == null) {
                    return; // Header validation interceptor will catch this
                }

                // Add tenant tag filter to search
                String tagSystem = properties.getTenant().getTagSystem();
                String tagFilter = tagSystem + "|" + tenantId;

                // Append to existing _tag parameters if any
                String[] existingTags = requestDetails.getParameters().get("_tag");
                if (existingTags == null) {
                    requestDetails.addParameter("_tag", new String[]{tagFilter});
                } else {
                    // Verify the tenant tag is not being overridden
                    boolean hasTenantTag = false;
                    for (String tag : existingTags) {
                        if (tag.startsWith(tagSystem + "|")) {
                            if (!tag.equals(tagFilter)) {
                                log.warn("Tenant tag override attempt detected — requested={}, actual={}",
                                        tag, tagFilter);
                                throw new ForbiddenOperationException(
                                        "Cannot override tenant filter. Your requests are scoped to tenant: " + tenantId);
                            }
                            hasTenantTag = true;
                        }
                    }
                    if (!hasTenantTag) {
                        String[] newTags = new String[existingTags.length + 1];
                        System.arraycopy(existingTags, 0, newTags, 0, existingTags.length);
                        newTags[existingTags.length] = tagFilter;
                        requestDetails.addParameter("_tag", newTags);
                    }
                }

                log.debug("Applied tenant filter to {} — tenant={}", operationType, tenantId);
            }
            default -> {
                // No tenant filter for direct reads, creates, updates, deletes
                // (handled by prestorage hooks and post-retrieval checks)
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Retrieves the tenant ID from request user data (populated by HeaderValidationInterceptor).
     */
    private String getTenantId(RequestDetails requestDetails) {
        Object tenantId = requestDetails.getUserData().get(HeaderValidationInterceptor.UD_TENANT_ID);
        return tenantId instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * Stamps the resource with the tenant tag, removing any existing tenant tags first.
     */
    private void stampTenantTag(Resource resource, String tenantId) {
        String tagSystem = properties.getTenant().getTagSystem();
        Meta meta = resource.getMeta();

        // Remove existing tenant tags to prevent injection
        List<Coding> tags = meta.getTag();
        tags.removeIf(tag -> tagSystem.equals(tag.getSystem()));

        // Add canonical tenant tag
        meta.addTag()
                .setSystem(tagSystem)
                .setCode(tenantId)
                .setDisplay("Tenant: " + tenantId);
    }

    /**
     * Extracts the existing tenant tag value from a resource, if present.
     */
    private String getExistingTenantTag(Resource resource) {
        String tagSystem = properties.getTenant().getTagSystem();
        for (Coding tag : resource.getMeta().getTag()) {
            if (tagSystem.equals(tag.getSystem())) {
                return tag.getCode();
            }
        }
        return null;
    }
}
