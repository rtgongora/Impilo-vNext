package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Shapes for Clavien-Dindo grades and complication profiles (pipeline §18). */
public final class ComplicationDtos {

    private ComplicationDtos() {
    }

    public record ClavienDindoGradeView(
            String gradeCode,
            String gradeLabel,
            String description) {
    }

    public record ComplicationClassView(
            String complicationType,
            String typicalGradeCode,
            String monitoringRequired,
            String escalationAction,
            boolean neverEvent) {
    }

    public record ComplicationProfileView(
            String profileCode,
            String profileName,
            String applicableSetting,
            String description,
            String status,
            String approvingAuthority,
            String contentMaturity,
            List<ComplicationClassView> classes) {
    }
}
