package zw.gov.mohcc.impilo.tshepo.audit.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;

public final class KeycloakEventMapper {
 private KeycloakEventMapper(){}
 public static String eventKey(String source,JsonNode event){String id=event.path("id").asText();if(!id.isBlank())return source+":"+id;String stable=event.path("time").asText()+"|"+event.path("type").asText()+"|"+event.path("userId").asText()+"|"+event.path("sessionId").asText()+"|"+event.path("resourcePath").asText();return source+":"+UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8));}
 public static AuditEventRequest map(UUID tenant,String source,JsonNode event){boolean admin="admin".equals(source);String type=admin?event.path("operationType").asText("UNKNOWN"):event.path("type").asText("UNKNOWN");JsonNode auth=event.path("authDetails");String actor=admin?auth.path("userId").asText("keycloak-system"):event.path("userId").asText("anonymous");String resource=admin?event.path("resourceType").asText("KEYCLOAK_ADMIN"):"KEYCLOAK_LOGIN";String resourceId=admin?event.path("resourcePath").asText(""):event.path("clientId").asText("");String error=event.path("error").asText("");Map<String,Object> detail=new LinkedHashMap<>();detail.put("source",source);detail.put("eventKey",eventKey(source,event));detail.put("clientId",admin?auth.path("clientId").asText(""):event.path("clientId").asText(""));detail.put("sessionId",admin?auth.path("ipAddress").asText(""):event.path("sessionId").asText(""));if(!error.isBlank())detail.put("error",error);JsonNode raw=event.path("details");for(String key:List.of("auth_method","auth_type","credential_type","code_id")){if(raw.has(key))detail.put(key,raw.path(key).asText());}return new AuditEventRequest(tenant,"KEYCLOAK_"+type,actor,admin?"ADMIN":"USER",null,resource,resourceId,type,error.isBlank()?"SUCCESS":"FAILURE","SYSTEM",null,UUID.nameUUIDFromBytes(eventKey(source,event).getBytes(StandardCharsets.UTF_8)),detail);}
}
