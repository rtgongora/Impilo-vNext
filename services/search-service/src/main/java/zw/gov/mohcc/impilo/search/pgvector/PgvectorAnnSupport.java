package zw.gov.mohcc.impilo.search.pgvector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.search.embeddings.EmbeddingVectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Writes {@code embedding_vec} and runs cosine-distance ANN queries on PostgreSQL with pgvector.
 * No-ops when disabled, not PostgreSQL, or the vector extension is missing.
 */
public final class PgvectorAnnSupport {

    private static final Logger log = LoggerFactory.getLogger(PgvectorAnnSupport.class);

    private final JdbcTemplate jdbc;
    private final SearchPgvectorProperties properties;
    private final DataSource dataSource;

    private volatile Boolean postgres;
    private volatile Boolean vectorExtension;

    public PgvectorAnnSupport(JdbcTemplate jdbc, SearchPgvectorProperties properties) {
        this.jdbc = jdbc;
        this.dataSource = Objects.requireNonNull(jdbc.getDataSource());
        this.properties = properties;
    }

    public boolean isUsable() {
        if (!properties.isEnabled()) {
            return false;
        }
        if (Boolean.FALSE.equals(postgres)) {
            return false;
        }
        if (postgres == null) {
            postgres = detectPostgres();
            if (!postgres) {
                log.info("impilo.search.pgvector.enabled=true but datasource is not PostgreSQL; ANN disabled.");
                return false;
            }
        }
        if (Boolean.FALSE.equals(vectorExtension)) {
            return false;
        }
        if (vectorExtension == null) {
            vectorExtension = detectVectorExtension();
            if (!vectorExtension) {
                log.warn("impilo.search.pgvector.enabled=true but pgvector extension is missing; run Flyway V003 and CREATE EXTENSION.");
                return false;
            }
        }
        return true;
    }

    private boolean detectPostgres() {
        try (Connection c = dataSource.getConnection()) {
            String name = c.getMetaData().getDatabaseProductName();
            return name != null && name.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (Exception e) {
            log.warn("Could not read database product name: {}", e.getMessage());
            return false;
        }
    }

    private boolean detectVectorExtension() {
        try {
            Integer one = jdbc.query("SELECT 1 FROM pg_extension WHERE extname = 'vector' LIMIT 1",
                    rs -> rs.next() ? 1 : null);
            return one != null;
        } catch (DataAccessException e) {
            log.warn("pgvector extension probe failed: {}", e.getMessage());
            return false;
        }
    }

    public void resetProbes() {
        postgres = null;
        vectorExtension = null;
    }

    /**
     * Persists {@code embedding_vec} (padded/truncated to {@link SearchPgvectorProperties#getDimensions()}).
     * Clears the column when {@code raw} is null.
     */
    public void syncEmbeddingVector(String rowId, float[] raw) {
        if (!isUsable()) {
            return;
        }
        int dim = Math.max(1, properties.getDimensions());
        try {
            if (raw == null || raw.length == 0) {
                jdbc.update("UPDATE ss_search_index SET embedding_vec = NULL WHERE id = ?", rowId);
                return;
            }
            float[] padded = EmbeddingVectors.padOrTruncate(raw, dim);
            if (padded == null) {
                jdbc.update("UPDATE ss_search_index SET embedding_vec = NULL WHERE id = ?", rowId);
                return;
            }
            String literal = toVectorLiteral(padded);
            jdbc.update("UPDATE ss_search_index SET embedding_vec = CAST(? AS vector) WHERE id = ?",
                    literal, rowId);
        } catch (DataAccessException e) {
            log.warn("Failed to sync embedding_vec for id={}: {}", rowId, e.getMessage());
        }
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.8f", v[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * ANN id list in ascending cosine-distance order (best first), capped by {@code limit}.
     */
    public List<String> annSearchIds(String tenantId, String entityType, float[] queryVector, int limit, int offset) {
        if (!isUsable() || queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        int dim = Math.max(1, properties.getDimensions());
        float[] qVec = EmbeddingVectors.padOrTruncate(queryVector, dim);
        if (qVec == null) {
            return List.of();
        }
        String qLiteral = toVectorLiteral(qVec);
        String et = entityType != null && !entityType.isBlank() ? entityType : null;
        try {
            return jdbc.query(
                    """
                            SELECT id FROM ss_search_index
                            WHERE tenant_id = ? AND embedding_vec IS NOT NULL
                              AND (?::text IS NULL OR entity_type = ?::text)
                            ORDER BY embedding_vec <=> CAST(? AS vector)
                            LIMIT ? OFFSET ?
                            """,
                    (ResultSet rs, int rowNum) -> rs.getString(1),
                    tenantId, et, et, qLiteral, limit, offset);
        } catch (DataAccessException e) {
            log.warn("ANN search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long countVectorRows(String tenantId, String entityType) {
        if (!isUsable()) {
            return 0L;
        }
        String et = entityType != null && !entityType.isBlank() ? entityType : null;
        try {
            Long c = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM ss_search_index
                            WHERE tenant_id = ? AND embedding_vec IS NOT NULL
                              AND (?::text IS NULL OR entity_type = ?::text)
                            """,
                    Long.class,
                    tenantId, et, et);
            return c == null ? 0L : c;
        } catch (DataAccessException e) {
            log.warn("ANN count failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Merges ANN-ordered ids with lexical ids (deduped, ANN order first).
     */
    public static List<String> mergeAnnThenLexical(List<String> annOrdered, List<String> lexical) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String id : annOrdered) {
            if (id != null && seen.add(id)) {
                out.add(id);
            }
        }
        if (lexical != null) {
            for (String id : lexical) {
                if (id != null && seen.add(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }
}
