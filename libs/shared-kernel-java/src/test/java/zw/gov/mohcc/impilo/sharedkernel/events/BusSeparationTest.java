package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI-time bus separation contract test.
 *
 * <p>Asserts the cardinal rule of the Impilo v1.1 event architecture:
 * <strong>no kernel.* publisher emits to clinical.* topics and vice versa.</strong></p>
 *
 * <p>Validates every topic registered in {@link ImpiloTopicBus#TOPIC_BUS_MAP}:
 * <ul>
 *   <li>Topics prefixed {@code clinical.} must belong to bus {@code clinical}</li>
 *   <li>Topics prefixed {@code kernel.} must belong to bus {@code kernel}</li>
 *   <li>Topics prefixed {@code trust.} must belong to bus {@code trust}</li>
 *   <li>No topic in the clinical bus starts with {@code kernel.}</li>
 *   <li>No topic in the kernel bus starts with {@code clinical.}</li>
 * </ul>
 */
class BusSeparationTest {

    @Test
    void allRegisteredTopicsHaveConsistentBusAssignment() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : ImpiloTopicBus.TOPIC_BUS_MAP.entrySet()) {
            String topic = entry.getKey();
            String registeredBus = entry.getValue();
            String derivedBus = ImpiloTopicBus.busFor(topic);

            if (!registeredBus.equals(derivedBus)) {
                violations.add("Topic '" + topic + "': registered as '" + registeredBus
                        + "' but prefix resolves to '" + derivedBus + "'");
            }
        }

        assertTrue(violations.isEmpty(),
                "Bus assignment inconsistencies found:\n" + String.join("\n", violations));
    }

    @Test
    void noKernelPublisherEmitsToClinicalBus() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : ImpiloTopicBus.TOPIC_BUS_MAP.entrySet()) {
            String topic = entry.getKey();
            String registeredBus = entry.getValue();

            if (ImpiloTopicBus.KERNEL.equals(registeredBus) && topic.startsWith("clinical.")) {
                violations.add("Kernel-bus topic emits to clinical: " + topic);
            }
        }

        assertTrue(violations.isEmpty(),
                "Kernel publishers must not emit to clinical.* topics:\n"
                + String.join("\n", violations));
    }

    @Test
    void noClinicalPublisherEmitsToKernelBus() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : ImpiloTopicBus.TOPIC_BUS_MAP.entrySet()) {
            String topic = entry.getKey();
            String registeredBus = entry.getValue();

            if (ImpiloTopicBus.CLINICAL.equals(registeredBus) && topic.startsWith("kernel.")) {
                violations.add("Clinical-bus topic emits to kernel: " + topic);
            }
        }

        assertTrue(violations.isEmpty(),
                "Clinical publishers must not emit to kernel.* topics:\n"
                + String.join("\n", violations));
    }

    @Test
    void noClinicalPublisherEmitsToTrustBus() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : ImpiloTopicBus.TOPIC_BUS_MAP.entrySet()) {
            String topic = entry.getKey();
            String registeredBus = entry.getValue();

            if (ImpiloTopicBus.CLINICAL.equals(registeredBus) && topic.startsWith("trust.")) {
                violations.add("Clinical-bus topic emits to trust: " + topic);
            }
        }

        assertTrue(violations.isEmpty(),
                "Clinical publishers must not emit to trust.* topics:\n"
                + String.join("\n", violations));
    }

    @Test
    void noKernelPublisherEmitsToTrustBus() {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : ImpiloTopicBus.TOPIC_BUS_MAP.entrySet()) {
            String topic = entry.getKey();
            String registeredBus = entry.getValue();

            if (ImpiloTopicBus.KERNEL.equals(registeredBus) && topic.startsWith("trust.")) {
                violations.add("Kernel-bus topic emits to trust: " + topic);
            }
        }

        assertTrue(violations.isEmpty(),
                "Kernel publishers must not emit to trust.* topics:\n"
                + String.join("\n", violations));
    }

    @Test
    void pctTopicsAreOnClinicalBus() {
        ImpiloTopicBus.topicsForBus(ImpiloTopicBus.CLINICAL).stream()
                .filter(t -> t.contains(".pct."))
                .forEach(topic -> assertEquals(ImpiloTopicBus.CLINICAL, ImpiloTopicBus.busFor(topic),
                        "PCT topic must be on clinical bus: " + topic));
    }

    @Test
    void orosTopicsAreOnClinicalBus() {
        ImpiloTopicBus.topicsForBus(ImpiloTopicBus.CLINICAL).stream()
                .filter(t -> t.contains(".oros."))
                .forEach(topic -> assertEquals(ImpiloTopicBus.CLINICAL, ImpiloTopicBus.busFor(topic),
                        "OROS topic must be on clinical bus: " + topic));
    }

    @Test
    void vitoTopicsAreOnKernelBus() {
        ImpiloTopicBus.topicsForBus(ImpiloTopicBus.KERNEL).stream()
                .filter(t -> t.contains(".vito."))
                .forEach(topic -> assertEquals(ImpiloTopicBus.KERNEL, ImpiloTopicBus.busFor(topic),
                        "VITO topic must be on kernel bus: " + topic));
    }

    @Test
    void tusoTopicsAreOnKernelBus() {
        ImpiloTopicBus.topicsForBus(ImpiloTopicBus.KERNEL).stream()
                .filter(t -> t.contains(".tuso."))
                .forEach(topic -> assertEquals(ImpiloTopicBus.KERNEL, ImpiloTopicBus.busFor(topic),
                        "TUSO topic must be on kernel bus: " + topic));
    }

    @Test
    void trustRevocationTopicsAreOnTrustBus() {
        List.of(
                "trust.revocation.consent",
                "trust.revocation.privilege",
                "trust.revocation.identity",
                "trust.federation.pod_registered",
                "trust.decision_evidence"
        ).forEach(topic -> assertEquals(ImpiloTopicBus.TRUST, ImpiloTopicBus.busFor(topic),
                "Trust topic must be on trust bus: " + topic));
    }

    @Test
    void busForHandlesUnknownTopics() {
        assertEquals(ImpiloTopicBus.UNKNOWN, ImpiloTopicBus.busFor("platform.legacy.events"));
        assertEquals(ImpiloTopicBus.UNKNOWN, ImpiloTopicBus.busFor(null));
        assertEquals(ImpiloTopicBus.UNKNOWN, ImpiloTopicBus.busFor(""));
    }

    @Test
    void assertBusThrowsOnViolation() {
        assertThrows(IllegalArgumentException.class,
                () -> ImpiloTopicBus.assertBus(ImpiloTopicBus.KERNEL, "clinical.pct.events"),
                "assertBus should throw when kernel publisher tries to use clinical topic");

        assertThrows(IllegalArgumentException.class,
                () -> ImpiloTopicBus.assertBus(ImpiloTopicBus.CLINICAL, "kernel.tuso.facility"),
                "assertBus should throw when clinical publisher tries to use kernel topic");
    }

    @Test
    void assertBusPassesOnCorrectAssignment() {
        assertDoesNotThrow(() -> ImpiloTopicBus.assertBus(ImpiloTopicBus.CLINICAL, "clinical.pct.events"));
        assertDoesNotThrow(() -> ImpiloTopicBus.assertBus(ImpiloTopicBus.KERNEL, "kernel.vito.identity"));
        assertDoesNotThrow(() -> ImpiloTopicBus.assertBus(ImpiloTopicBus.TRUST, "trust.revocation.consent"));
    }

    @Test
    void registryCoversAllMandatoryServices() {
        String[] requiredServicePrefixes = {
                "clinical.pct.", "clinical.oros.", "clinical.pharmacy.", "clinical.inpatient.",
                "kernel.vito.", "kernel.tuso.", "kernel.varapi.", "kernel.msika.",
                "kernel.zibo.", "kernel.ubomi.", "kernel.mushex.", "kernel.costa.",
                "trust.revocation.", "trust.tshepo."
        };

        for (String prefix : requiredServicePrefixes) {
            boolean found = ImpiloTopicBus.TOPIC_BUS_MAP.keySet().stream()
                    .anyMatch(t -> t.startsWith(prefix));
            assertTrue(found, "No topics registered for service prefix: " + prefix);
        }
    }
}
