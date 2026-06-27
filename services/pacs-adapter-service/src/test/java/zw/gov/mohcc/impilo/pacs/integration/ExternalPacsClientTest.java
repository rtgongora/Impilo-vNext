package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G045: verifies the DICOMweb {@link ExternalPacsClient} against a simulated DICOMweb endpoint
 * (JDK {@link HttpServer}) — real HTTP, real {@code application/dicom+json} parsing — and that its
 * Orthanc-shaped output is readable by the existing parsers the sync flow uses.
 */
class ExternalPacsClientTest {

    private static final String STUDY = "1.2.3.100";
    private static final String SERIES = "1.2.3.200";
    private static final String SOP = "1.2.3.300";

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            if (path.endsWith("/metadata")) {
                body = "[{\"00080018\":{\"vr\":\"UI\",\"Value\":[\"" + SOP + "\"]},"
                        + "\"00200013\":{\"vr\":\"IS\",\"Value\":[\"1\"]},"
                        + "\"00280008\":{\"vr\":\"IS\",\"Value\":[\"1\"]},"
                        + "\"00020010\":{\"vr\":\"UI\",\"Value\":[\"1.2.840.10008.1.2.1\"]}}]";
            } else if (path.endsWith("/instances")) {
                body = "[{\"00080018\":{\"vr\":\"UI\",\"Value\":[\"" + SOP + "\"]},"
                        + "\"00200013\":{\"vr\":\"IS\",\"Value\":[\"1\"]}}]";
            } else if (path.endsWith("/series")) {
                body = "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"" + SERIES + "\"]},"
                        + "\"00080060\":{\"vr\":\"CS\",\"Value\":[\"CT\"]},"
                        + "\"00200011\":{\"vr\":\"IS\",\"Value\":[\"2\"]},"
                        + "\"0008103E\":{\"vr\":\"LO\",\"Value\":[\"Chest CT\"]},"
                        + "\"00180015\":{\"vr\":\"CS\",\"Value\":[\"CHEST\"]}}]";
            } else if (path.equals("/studies")) {
                body = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"" + STUDY + "\"]}}]";
            } else {
                body = "[]";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private static RestTemplate dicomwebRestTemplate() {
        RestTemplate rt = new RestTemplate();
        for (HttpMessageConverter<?> c : rt.getMessageConverters()) {
            if (c instanceof MappingJackson2HttpMessageConverter jackson) {
                List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
                types.add(MediaType.parseMediaType("application/dicom+json"));
                jackson.setSupportedMediaTypes(types);
            }
        }
        return rt;
    }

    private ExternalPacsClient configuredClient() {
        return new ExternalPacsClient(dicomwebRestTemplate(), new ObjectMapper(),
                "EXTERNAL", baseUrl, true, false);
    }

    @Test
    void findStudyIds_returnsStudyUid_whenQidoMatches() {
        assertEquals(List.of(STUDY), configuredClient().findStudyIdsByStudyInstanceUid(STUDY));
    }

    @Test
    void getStudy_mapsSeriesToCompositeIds() {
        JsonNode study = configuredClient().getStudy(STUDY);
        JsonNode series = study.get("Series");
        assertTrue(series.isArray());
        assertEquals(1, series.size());
        assertEquals(STUDY + "|" + SERIES, series.get(0).asText());
    }

    @Test
    void getSeries_buildsMainDicomTagsAndInstanceIds_readableByOrthancHelpers() {
        JsonNode ser = configuredClient().getSeries(STUDY + "|" + SERIES);
        JsonNode main = ser.get("MainDicomTags");
        // Readable by the same helper the sync service uses.
        assertEquals(SERIES, OrthancMainDicomHelper.stringField(main, "SeriesInstanceUID"));
        assertEquals("CT", OrthancMainDicomHelper.stringField(main, "Modality"));
        assertEquals(Integer.valueOf(2), OrthancMainDicomHelper.intField(main, "SeriesNumber"));
        assertEquals("Chest CT", OrthancMainDicomHelper.stringField(main, "SeriesDescription"));

        JsonNode instances = ser.get("Instances");
        assertEquals(1, instances.size());
        assertEquals(STUDY + "|" + SERIES + "|" + SOP, instances.get(0).asText());
    }

    @Test
    void getInstanceSimplifiedTags_returnsDicomJson_readableByOrthancTagParser() {
        JsonNode tags = configuredClient().getInstanceSimplifiedTags(STUDY + "|" + SERIES + "|" + SOP);
        assertEquals(SOP, OrthancTagParser.stringTag(tags, "00080018")); // SOPInstanceUID
        assertEquals(Integer.valueOf(1), OrthancTagParser.intTag(tags, "00200013")); // InstanceNumber
        assertEquals(Integer.valueOf(1), OrthancTagParser.intTag(tags, "00280008")); // NumberOfFrames
        assertEquals("1.2.840.10008.1.2.1", OrthancTagParser.stringTag(tags, "00020010")); // TransferSyntax
    }

    @Test
    void systemStatus_reachableWhenConfiguredAndUp() {
        Map<String, Object> status = configuredClient().systemStatus();
        assertEquals(Boolean.TRUE, status.get("reachable"));
        assertEquals("EXTERNAL", status.get("provider"));
    }

    @Test
    void failsClosed_whenNoBaseUrlConfigured() {
        ExternalPacsClient unconfigured = new ExternalPacsClient(
                dicomwebRestTemplate(), new ObjectMapper(), "EXTERNAL", "", true, false);

        assertFalse(unconfigured.supportsHierarchySync());
        assertTrue(unconfigured.findStudyIdsByStudyInstanceUid(STUDY).isEmpty());
        assertEquals(Boolean.FALSE, unconfigured.systemStatus().get("reachable"));
        assertThrows(IllegalStateException.class, () -> unconfigured.getStudy(STUDY));
    }
}
