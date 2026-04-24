package zw.gov.mohcc.impilo.search.embeddings;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for hybrid lexical + dense-vector ranking.
 */
@ConfigurationProperties(prefix = "impilo.search.ranking")
public class SearchRankingProperties {

    /**
     * Weight on cosine term in {@code rankMode=hybrid} (0..1). Remaining mass goes to lexical score.
     */
    private double hybridCosineWeight = 0.55;

    public double getHybridCosineWeight() {
        return hybridCosineWeight;
    }

    public void setHybridCosineWeight(double hybridCosineWeight) {
        this.hybridCosineWeight = hybridCosineWeight;
    }
}
