package zw.gov.mohcc.impilo.burns.tbsa;

/**
 * The Lund–Browder body regions.
 *
 * <p>Regions are split left/right and anterior/posterior exactly as the published chart splits
 * them, because that is the granularity at which a clinician shades the diagram. Collapsing
 * "arms" into one region would force an estimate at the point where the chart asks for an
 * observation.
 */
public enum BodyRegion {

    HEAD_ANTERIOR,
    HEAD_POSTERIOR,
    NECK_ANTERIOR,
    NECK_POSTERIOR,
    TRUNK_ANTERIOR,
    TRUNK_POSTERIOR,
    BUTTOCK_LEFT,
    BUTTOCK_RIGHT,
    GENITALIA,
    UPPER_ARM_LEFT,
    UPPER_ARM_RIGHT,
    FOREARM_LEFT,
    FOREARM_RIGHT,
    HAND_LEFT,
    HAND_RIGHT,
    THIGH_LEFT,
    THIGH_RIGHT,
    LOWER_LEG_LEFT,
    LOWER_LEG_RIGHT,
    FOOT_LEFT,
    FOOT_RIGHT;

    /** True for the four regions whose share of body surface changes with age. */
    public boolean isAgeVariable() {
        return switch (this) {
            case HEAD_ANTERIOR, HEAD_POSTERIOR,
                 THIGH_LEFT, THIGH_RIGHT,
                 LOWER_LEG_LEFT, LOWER_LEG_RIGHT -> true;
            default -> false;
        };
    }
}
