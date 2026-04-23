package zw.gov.mohcc.impilo.search.embeddings;

/**
 * Produces a dense vector for ranking. Implementations should L2-normalize outputs when practical.
 */
public interface EmbeddingModel {

    /** @return normalized embedding, or {@code null} when this provider is inactive or input unusable */
    float[] embed(String text);
}
