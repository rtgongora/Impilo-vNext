package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A KIND of configurable thing. Adding a type is a code change, because code owns the
 * configuration language: a type with no engine behind it is configuration nobody executes.
 *
 * <p>{@code requiresFourEyes} marks the types the regulator may not change single-handed — fees,
 * penalties, register-entry rules and decision workflows. {@code applicantFacing} marks the types
 * whose definitions must declare a self-service route, so a release cannot activate a regulator
 * capability that an applicant has no way to reach.</p>
 */
@Entity
@Table(name = "regulatory_config_definition_type", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ConfigDefinitionTypeEntity {

    @Id
    @Column(length = 48)
    private String typeCode;

    @Column(nullable = false, length = 128)
    private String label;

    private String description;

    @Column(nullable = false)
    private boolean requiresFourEyes;

    @Column(nullable = false)
    private boolean applicantFacing;

    @Column(nullable = false)
    private int sortOrder = 100;
}
