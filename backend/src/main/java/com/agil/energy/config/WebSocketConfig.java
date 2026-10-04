package com.agil.energy.config;

import com.agil.energy.security.CustomUserDetailsService;
import com.agil.energy.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:4200")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> auth = accessor.getNativeHeader("Authorization");
                    if (auth == null || auth.isEmpty()) {
                        log.warn("WS CONNECT without Authorization header — rejecting");
                        throw new IllegalArgumentException("Missing Authorization");
                    }
                    String header = auth.get(0);
                    String token  = header.startsWith("Bearer ") ? header.substring(7) : header;
                    try {
                        String email = jwtUtil.extractUsername(token);
                        UserDetails ud = userDetailsService.loadUserByUsername(email);
                        if (jwtUtil.validateToken(token, ud)) {
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    ud, null, ud.getAuthorities());
                            accessor.setUser(authentication);
                            log.debug("WS CONNECT authenticated: {}", email);
                        } else {
                            throw new JwtException("Token validation failed");
                        }
                    } catch (Exception e) {
                        log.warn("WS CONNECT rejected: {}", e.getMessage());
                        throw new IllegalArgumentException("Invalid token", e);
                    }
                }
                return message;
            }
        });
    }
}