package zw.gov.mohcc.impilo.msikaapps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaapps.api.dto.MsikaAppsDtos.*;
import zw.gov.mohcc.impilo.msikaapps.domain.*;
import zw.gov.mohcc.impilo.msikaapps.repository.MsikaAppsRepositories.*;
import zw.gov.mohcc.impilo.msikaapps.service.MarketplaceService;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the {@link MarketplaceService} lifecycle: catalogue listing,
 * activation request submission and decision, installation activation and
 * suspension, and the role/facility-aware launcher view. Uses Mockito to
 * stub the Spring Data repositories.
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceLifecycleTest {

    @Mock private MarketplaceItemRepository items;
    @Mock private PublisherRepository publishers;
    @Mock private ActivationRequestRepository activationRequests;
    @Mock private InstallationRepository installations;
    @Mock private AuditEventRepository auditEvents;

    private MarketplaceService service;

    private MarketplaceItemEntity telemed;
    private MarketplaceItemEntity dhis2Connector;
    private MarketplaceItemEntity draftApp;
    private MarketplaceItemEntity deprecatedApp;

    private final Map<String, ActivationRequestEntity> activationStore = new LinkedHashMap<>();
    private final Map<String, InstallationEntity> installationStore = new LinkedHashMap<>();
    private final List<AuditEventEntity> auditStore = new ArrayList<>();

    @BeforeEach
    void setup() {
        service = new MarketplaceService(items, publishers, activationRequests, installations, auditEvents);

        telemed       = seed("telemed",        "APP",       "TELEMEDICINE",   "APPROVED",   "1.0.0");
        dhis2Connector= seed("dhis2-connector","CONNECTOR", "PUBLIC_HEALTH",  "APPROVED",   "1.0.0");
        draftApp      = seed("draft-app",      "APP",       "WELLNESS",       "DRAFT",      "0.1.0");
        deprecatedApp = seed("deprecated-app", "APP",       "TRAINING",       "DEPRECATED", "1.0.0");

        PublisherEntity pub = new PublisherEntity();
        pub.setId("pub-impilo");
        pub.setName("Impilo Health OS");

        lenient().when(publishers.findAll()).thenReturn(List.of(pub));
        lenient().when(items.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return items().stream().filter(i -> id.equals(i.getId())).findFirst();
        });
        lenient().when(items.findByItemCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return items().stream().filter(i -> code.equals(i.getItemCode())).findFirst();
        });
        lenient().when(items.findAll()).thenAnswer(inv -> items());
        lenient().when(items.search(any(), any(), any())).thenAnswer(this::search);

        lenient().when(activationRequests.save(any(ActivationRequestEntity.class))).thenAnswer(this::saveActivation);
        lenient().when(activationRequests.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(activationStore.get(inv.getArgument(0))));
        lenient().when(activationRequests.findByRequesterTenantIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return activationStore.values().stream().filter(a -> t.equals(a.getRequesterTenantId())).toList();
        });
        lenient().when(activationRequests.findByStatusOrderByCreatedAtAsc(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return activationStore.values().stream().filter(a -> s.equals(a.getStatus())).toList();
        });

        lenient().when(installations.save(any(InstallationEntity.class))).thenAnswer(this::saveInstallation);
        lenient().when(installations.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(installationStore.get(inv.getArgument(0))));
        lenient().when(installations.findByTenantIdOrderByInstalledAtDesc(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return installationStore.values().stream().filter(i -> t.equals(i.getTenantId())).toList();
        });
        lenient().when(installations.findByTenantIdAndLifecycleOrderByInstalledAtDesc(anyString(), anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            String l = inv.getArgument(1);
            return installationStore.values().stream()
                    .filter(i -> t.equals(i.getTenantId()) && l.equals(i.getLifecycle())).toList();
        });
        lenient().when(installations.findByItemIdAndTenantIdOrderByInstalledAtDesc(anyString(), anyString())).thenAnswer(inv -> {
            String it = inv.getArgument(0);
            String tn = inv.getArgument(1);
            return installationStore.values().stream()
                    .filter(i -> it.equals(i.getItemId()) && tn.equals(i.getTenantId())).toList();
        });

        lenient().when(auditEvents.save(any(AuditEventEntity.class))).thenAnswer(inv -> {
            AuditEventEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID().toString());
            if (e.getOccurredAt() == null) e.setOccurredAt(OffsetDateTime.now());
            auditStore.add(e);
            return e;
        });
        lenient().when(auditEvents.findByTenantIdOrderByOccurredAtDesc(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return auditStore.stream().filter(a -> t.equals(a.getTenantId())).toList();
        });
    }

    private List<MarketplaceItemEntity> items() {
        return List.of(telemed, dhis2Connector, draftApp, deprecatedApp);
    }

    private List<MarketplaceItemEntity> search(InvocationOnMock inv) {
        String type = inv.getArgument(0);
        String category = inv.getArgument(1);
        String publisherId = inv.getArgument(2);
        return items().stream()
                .filter(i -> type == null || type.equals(i.getType()))
                .filter(i -> category == null || category.equals(i.getCategory()))
                .filter(i -> publisherId == null || publisherId.equals(i.getPublisherId()))
                .toList();
    }

    private ActivationRequestEntity saveActivation(InvocationOnMock inv) {
        ActivationRequestEntity e = inv.getArgument(0);
        if (e.getId() == null) e.setId(UUID.randomUUID().toString());
        if (e.getCreatedAt() == null) e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        activationStore.put(e.getId(), e);
        return e;
    }

    private InstallationEntity saveInstallation(InvocationOnMock inv) {
        InstallationEntity e = inv.getArgument(0);
        if (e.getId() == null) e.setId(UUID.randomUUID().toString());
        if (e.getCreatedAt() == null) e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        installationStore.put(e.getId(), e);
        return e;
    }

    private MarketplaceItemEntity seed(String code, String type, String category, String approval, String version) {
        MarketplaceItemEntity e = new MarketplaceItemEntity();
        e.setItemCode(code);
        e.setName(code);
        e.setDescription("desc " + code);
        e.setType(type);
        e.setCategory(category);
        e.setPublisherId("pub-impilo");
        e.setVersion(version);
        e.setCompatibilityVersion("1.x");
        e.setSupportedEnvironments("SANDBOX,STAGING,PRODUCTION");
        e.setManifestRef("manifests/" + code + ".json");
        e.setApprovalStatus(approval);
        e.setHealthStatus("HEALTHY");
        return e;
    }

    @Test
    @DisplayName("searchCatalogue filters by type and category")
    void searchCatalogue() {
        assertThat(service.searchCatalogue("APP", null, null))
                .extracting(MarketplaceItemResponse::itemCode)
                .containsExactlyInAnyOrder("telemed", "draft-app", "deprecated-app");

        assertThat(service.searchCatalogue("CONNECTOR", null, null))
                .extracting(MarketplaceItemResponse::itemCode).containsExactly("dhis2-connector");

        assertThat(service.searchCatalogue(null, "WELLNESS", null))
                .extracting(MarketplaceItemResponse::itemCode).containsExactly("draft-app");
    }

    @Test
    @DisplayName("createActivationRequest rejects non-APPROVED items")
    void rejectActivationOfNonApprovedItem() {
        ActivationRequestRequest req = new ActivationRequestRequest(
                draftApp.getId(), "fac-A", null, null, "TREATMENT", "for clinic A", null);
        assertThatThrownBy(() -> service.createActivationRequest(req, "actor-1", "tenant-1", "pod-1", "cor-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not APPROVED");
    }

    @Test
    @DisplayName("Full lifecycle: request → approve → INSTALLED → activate → suspend → reactivate")
    void fullActivationLifecycle() {
        ActivationRequestRequest req = new ActivationRequestRequest(
                telemed.getId(), "fac-A", null, null, "TREATMENT", "Province X needs telemed", null);
        ActivationRequestResponse created = service.createActivationRequest(req, "actor-1", "tenant-1", "pod-1", "cor-1");
        assertThat(created.status()).isEqualTo("REQUESTED");

        ActivationRequestResponse approved = service.decideActivationRequest(
                created.id(),
                new ActivationDecisionRequest("APPROVED", "ok"),
                "approver-1", "tenant-1");
        assertThat(approved.status()).isEqualTo("APPROVED");

        List<InstallationResponse> insts = service.listInstallations("tenant-1", null);
        assertThat(insts).hasSize(1);
        InstallationResponse installed = insts.get(0);
        assertThat(installed.lifecycle()).isEqualTo("INSTALLED");

        InstallationResponse activated = service.activateInstallation(installed.id(), "actor-1", "tenant-1");
        assertThat(activated.lifecycle()).isEqualTo("ACTIVE");
        assertThat(activated.activatedAt()).isNotNull();

        InstallationResponse suspended = service.suspendInstallation(
                installed.id(), new InstallationSuspendRequest("compliance review"), "actor-1", "tenant-1");
        assertThat(suspended.lifecycle()).isEqualTo("SUSPENDED");
        assertThat(suspended.suspendedReason()).isEqualTo("compliance review");

        InstallationResponse reactivated = service.activateInstallation(installed.id(), "actor-1", "tenant-1");
        assertThat(reactivated.lifecycle()).isEqualTo("ACTIVE");
        assertThat(reactivated.suspendedAt()).isNull();
        assertThat(reactivated.suspendedReason()).isNull();
    }

    @Test
    @DisplayName("Tenant isolation: each tenant only sees its own activation requests")
    void tenantIsolation() {
        service.createActivationRequest(
                new ActivationRequestRequest(telemed.getId(), "fac-A", null, null, "TREATMENT", "A", null),
                "actor-A", "tenant-A", "pod-1", "cor-A");
        service.createActivationRequest(
                new ActivationRequestRequest(telemed.getId(), "fac-B", null, null, "TREATMENT", "B", null),
                "actor-B", "tenant-B", "pod-1", "cor-B");

        List<ActivationRequestResponse> tenantA = service.listActivationRequests("tenant-A", null);
        List<ActivationRequestResponse> tenantB = service.listActivationRequests("tenant-B", null);
        assertThat(tenantA).hasSize(1);
        assertThat(tenantB).hasSize(1);
        assertThat(tenantA.get(0).requesterTenantId()).isEqualTo("tenant-A");
        assertThat(tenantB.get(0).requesterTenantId()).isEqualTo("tenant-B");
    }

    @Test
    @DisplayName("Launcher reports REQUEST_ACCESS when not installed for the tenant")
    void launcherRequestAccess() {
        List<LauncherAppResponse> launcher = service.getLauncher("tenant-1", "fac-A", List.of());
        LauncherAppResponse t = launcher.stream()
                .filter(l -> "telemed".equals(l.itemCode())).findFirst().orElseThrow();
        assertThat(t.state()).isEqualTo("REQUEST_ACCESS");
        assertThat(t.installationId()).isNull();
        // Connectors are not launcher candidates
        assertThat(launcher).noneMatch(l -> "dhis2-connector".equals(l.itemCode()));
    }

    @Test
    @DisplayName("Launcher reports INSTALLED state after approval + activation")
    void launcherInstalledState() {
        ActivationRequestResponse req = service.createActivationRequest(
                new ActivationRequestRequest(telemed.getId(), null, null, null, "TREATMENT", "j", null),
                "actor-1", "tenant-1", "pod-1", "cor-1");
        service.decideActivationRequest(
                req.id(), new ActivationDecisionRequest("APPROVED", "ok"), "approver-1", "tenant-1");
        InstallationResponse inst = service.listInstallations("tenant-1", null).get(0);
        service.activateInstallation(inst.id(), "actor-1", "tenant-1");

        List<LauncherAppResponse> launcher = service.getLauncher("tenant-1", null, List.of());
        LauncherAppResponse telemedLauncher = launcher.stream()
                .filter(l -> "telemed".equals(l.itemCode())).findFirst().orElseThrow();
        assertThat(telemedLauncher.state()).isEqualTo("INSTALLED");
        assertThat(telemedLauncher.installationId()).isEqualTo(inst.id());
    }

    @Test
    @DisplayName("Each lifecycle transition is audited")
    void auditTrail() {
        ActivationRequestResponse created = service.createActivationRequest(
                new ActivationRequestRequest(telemed.getId(), "fac-A", null, null, "TREATMENT", "j", null),
                "actor-1", "tenant-1", "pod-1", "cor-1");
        service.decideActivationRequest(
                created.id(), new ActivationDecisionRequest("APPROVED", "ok"), "approver-1", "tenant-1");
        InstallationResponse inst = service.listInstallations("tenant-1", null).get(0);
        service.activateInstallation(inst.id(), "actor-1", "tenant-1");
        service.suspendInstallation(inst.id(), new InstallationSuspendRequest("paused"), "actor-1", "tenant-1");

        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEvents, atLeast(5)).save(captor.capture());
        List<String> eventTypes = captor.getAllValues().stream().map(AuditEventEntity::getEventType).toList();
        assertThat(eventTypes).contains(
                "ACTIVATION_REQUESTED",
                "ACTIVATION_APPROVED",
                "INSTALLATION_CREATED",
                "INSTALLATION_ACTIVATED",
                "INSTALLATION_SUSPENDED"
        );
    }
}
