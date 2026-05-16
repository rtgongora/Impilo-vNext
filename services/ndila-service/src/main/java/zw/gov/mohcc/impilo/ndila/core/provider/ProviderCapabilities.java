package zw.gov.mohcc.impilo.ndila.core.provider;

/**
 * Capability metadata advertised by every Ndila provider adapter so the
 * NdilaProviderPolicyService can pick an appropriate provider for each
 * operation. Capabilities are immutable for the lifetime of an adapter.
 */
public record ProviderCapabilities(
        boolean supportsGeocoding,
        boolean supportsReverseGeocoding,
        boolean supportsRouting,
        boolean supportsDistanceMatrix,
        boolean supportsIsochrones,
        boolean supportsTraffic,
        boolean supportsOffline,
        boolean supportsTiles,
        boolean supportsVectorTiles,
        boolean supportsBoundaries,
        boolean supportsZimbabweCoverage,
        boolean supportsCommercialSla,
        boolean supportsSensitiveWorkflows,
        String estimatedCostCategory,
        String dataResidencyNotes,
        String privacyNotes
) {
    public static class Builder {
        private boolean supportsGeocoding;
        private boolean supportsReverseGeocoding;
        private boolean supportsRouting;
        private boolean supportsDistanceMatrix;
        private boolean supportsIsochrones;
        private boolean supportsTraffic;
        private boolean supportsOffline;
        private boolean supportsTiles;
        private boolean supportsVectorTiles;
        private boolean supportsBoundaries;
        private boolean supportsZimbabweCoverage = true;
        private boolean supportsCommercialSla;
        private boolean supportsSensitiveWorkflows;
        private String estimatedCostCategory = "FREE";
        private String dataResidencyNotes = "";
        private String privacyNotes = "";

        public Builder supportsGeocoding(boolean v) { this.supportsGeocoding = v; return this; }
        public Builder supportsReverseGeocoding(boolean v) { this.supportsReverseGeocoding = v; return this; }
        public Builder supportsRouting(boolean v) { this.supportsRouting = v; return this; }
        public Builder supportsDistanceMatrix(boolean v) { this.supportsDistanceMatrix = v; return this; }
        public Builder supportsIsochrones(boolean v) { this.supportsIsochrones = v; return this; }
        public Builder supportsTraffic(boolean v) { this.supportsTraffic = v; return this; }
        public Builder supportsOffline(boolean v) { this.supportsOffline = v; return this; }
        public Builder supportsTiles(boolean v) { this.supportsTiles = v; return this; }
        public Builder supportsVectorTiles(boolean v) { this.supportsVectorTiles = v; return this; }
        public Builder supportsBoundaries(boolean v) { this.supportsBoundaries = v; return this; }
        public Builder supportsZimbabweCoverage(boolean v) { this.supportsZimbabweCoverage = v; return this; }
        public Builder supportsCommercialSla(boolean v) { this.supportsCommercialSla = v; return this; }
        public Builder supportsSensitiveWorkflows(boolean v) { this.supportsSensitiveWorkflows = v; return this; }
        public Builder estimatedCostCategory(String v) { this.estimatedCostCategory = v; return this; }
        public Builder dataResidencyNotes(String v) { this.dataResidencyNotes = v; return this; }
        public Builder privacyNotes(String v) { this.privacyNotes = v; return this; }

        public ProviderCapabilities build() {
            return new ProviderCapabilities(
                    supportsGeocoding, supportsReverseGeocoding, supportsRouting,
                    supportsDistanceMatrix, supportsIsochrones, supportsTraffic,
                    supportsOffline, supportsTiles, supportsVectorTiles,
                    supportsBoundaries, supportsZimbabweCoverage,
                    supportsCommercialSla, supportsSensitiveWorkflows,
                    estimatedCostCategory, dataResidencyNotes, privacyNotes);
        }
    }
}
