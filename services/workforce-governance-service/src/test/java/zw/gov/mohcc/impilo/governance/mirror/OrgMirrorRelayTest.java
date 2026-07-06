package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgMirrorRelayTest {

    @Mock
    private OrgRegistryMirrorClient mirrorClient;

    private OrgMirrorEvent sampleEvent() {
        OrgMirrorPayload payload = new OrgMirrorPayload(
                UUID.randomUUID(), "ORG-1", "Legal Ltd", "HOSPITAL_GROUP", "ACTIVE", null);
        return new OrgMirrorEvent(payload, UUID.randomUUID(), UUID.randomUUID(), "created");
    }

    @Test
    void flagOn_pushesToMirrorClient() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        OrgMirrorRelay relay = new OrgMirrorRelay(mirrorClient, props);
        OrgMirrorEvent event = sampleEvent();

        relay.onOrgMirrorEvent(event);

        verify(mirrorClient, times(1)).push(event.payload(), event.tenantId(), event.correlationId());
    }

    @Test
    void flagOff_neverCallsMirrorClient() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(false);
        OrgMirrorRelay relay = new OrgMirrorRelay(mirrorClient, props);

        relay.onOrgMirrorEvent(sampleEvent());

        verifyNoInteractions(mirrorClient);
    }

    @Test
    void mirrorClientFailure_isSwallowed_neverPropagates() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setEnabled(true);
        when(mirrorClient.push(any(), any(), any())).thenThrow(new RuntimeException("mirror down"));
        OrgMirrorRelay relay = new OrgMirrorRelay(mirrorClient, props);

        // A throwing client must never escape the AFTER_COMMIT callback.
        assertDoesNotThrow(() -> relay.onOrgMirrorEvent(sampleEvent()));
        verify(mirrorClient, times(1)).push(any(), any(), any());
    }
}
