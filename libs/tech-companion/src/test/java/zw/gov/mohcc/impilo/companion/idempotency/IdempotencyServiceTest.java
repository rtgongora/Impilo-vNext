package zw.gov.mohcc.impilo.companion.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private InMemoryIdempotencyRepository repo;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryIdempotencyRepository();
        service = new IdempotencyService(repo);
    }

    @Test
    void computeHashIsDeterministic() {
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        String hash1 = service.computeHash("POST", "/internal/v1/items", body);
        String hash2 = service.computeHash("POST", "/internal/v1/items", body);
        assertEquals(hash1, hash2);
    }

    @Test
    void computeHashDiffersForDifferentBodies() {
        byte[] body1 = "{\"name\":\"a\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "{\"name\":\"b\"}".getBytes(StandardCharsets.UTF_8);
        String hash1 = service.computeHash("POST", "/internal/v1/items", body1);
        String hash2 = service.computeHash("POST", "/internal/v1/items", body2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeHashDiffersForDifferentMethods() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String hashPost = service.computeHash("POST", "/internal/v1/items", body);
        String hashPut = service.computeHash("PUT", "/internal/v1/items", body);
        assertNotEquals(hashPost, hashPut);
    }

    @Test
    void storeAndFindReturnsRecord() {
        service.store("t1", "pod1", "key-1", "hash-abc", 201, "{\"id\":\"123\"}");
        var found = service.find("t1", "pod1", "key-1");
        assertTrue(found.isPresent());
        assertEquals("hash-abc", found.get().requestHash());
        assertEquals(201, found.get().responseStatus());
    }

    @Test
    void findReturnsEmptyForUnknownKey() {
        var found = service.find("t1", "pod1", "unknown");
        assertTrue(found.isEmpty());
    }

    @Test
    void computeHashHandlesNullBody() {
        String hash = service.computeHash("POST", "/internal/v1/items", null);
        assertNotNull(hash);
        assertFalse(hash.isBlank());
    }

    @Test
    void computeHashHandlesEmptyBody() {
        String hash = service.computeHash("POST", "/internal/v1/items", new byte[0]);
        assertNotNull(hash);
        assertFalse(hash.isBlank());
    }
}
