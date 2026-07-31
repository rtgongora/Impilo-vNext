package zw.gov.mohcc.impilo.tshepo.audit.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="keycloak_event_receipt",schema="tshepo_audit")
public class KeycloakEventReceiptEntity {
 @Id @Column(name="event_key") private String eventKey;
 @Column(nullable=false) private String source;
 @Column(name="keycloak_event_time") private Instant keycloakEventTime;
 @Column(name="ingested_at",nullable=false) private Instant ingestedAt;
 public String getEventKey(){return eventKey;} public void setEventKey(String v){eventKey=v;}
 public String getSource(){return source;} public void setSource(String v){source=v;}
 public Instant getKeycloakEventTime(){return keycloakEventTime;} public void setKeycloakEventTime(Instant v){keycloakEventTime=v;}
 public Instant getIngestedAt(){return ingestedAt;} public void setIngestedAt(Instant v){ingestedAt=v;}
}
