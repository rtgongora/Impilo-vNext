package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Which of the five named delivery channels (clinical summary, Khuluma, Nompilo, printed/offline
 * handout, caregiver view) a template is deliverable through. A join row, not a column on the
 * template — delivery is a decision independent of the template's own content.
 */
@Entity
@Table(name = "aftercare_template_channel", schema = "procedures")
@IdClass(AftercareTemplateChannelEntity.Key.class)
public class AftercareTemplateChannelEntity {

    @Id
    @Column(name = "template_id")
    private UUID templateId;

    @Id
    @Column(name = "delivery_channel", length = 32)
    private String deliveryChannel;

    public UUID getTemplateId() { return templateId; }
    public String getDeliveryChannel() { return deliveryChannel; }

    public static class Key implements Serializable {
        private UUID templateId;
        private String deliveryChannel;

        public Key() { }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(templateId, key.templateId) && Objects.equals(deliveryChannel, key.deliveryChannel);
        }

        @Override
        public int hashCode() { return Objects.hash(templateId, deliveryChannel); }
    }
}
