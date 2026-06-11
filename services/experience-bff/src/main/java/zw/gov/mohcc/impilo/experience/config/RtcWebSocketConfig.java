package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import zw.gov.mohcc.impilo.experience.telemedicine.RtcSignalingWebSocketHandler;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicinePresenceWebSocketHandler;

@Configuration
@EnableWebSocket
public class RtcWebSocketConfig implements WebSocketConfigurer {

    private final RtcSignalingWebSocketHandler signalingHandler;
    private final TelemedicinePresenceWebSocketHandler presenceHandler;

    public RtcWebSocketConfig(
            RtcSignalingWebSocketHandler signalingHandler,
            TelemedicinePresenceWebSocketHandler presenceHandler) {
        this.signalingHandler = signalingHandler;
        this.presenceHandler = presenceHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws/telemedicine/rtc/{sessionId}")
                .setAllowedOriginPatterns("*");
        registry.addHandler(presenceHandler, "/ws/telemedicine/presence")
                .setAllowedOriginPatterns("*");
    }
}
