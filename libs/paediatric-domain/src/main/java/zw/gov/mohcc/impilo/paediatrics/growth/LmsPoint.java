package zw.gov.mohcc.impilo.paediatrics.growth;

/**
 * One row of a growth reference table: the Box-Cox power, the median and the coefficient
 * of variation at a single point on the standard's axis.
 *
 * @param l Box-Cox power (skew)
 * @param m median
 * @param s coefficient of variation
 */
public record LmsPoint(double l, double m, double s) {
}
