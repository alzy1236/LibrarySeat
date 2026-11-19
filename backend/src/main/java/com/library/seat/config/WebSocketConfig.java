package com.library.seat.config;

import com.library.seat.websocket.TimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：实时服务器时间通道
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TimeWebSocketHandler timeWebSocketHandler;

    public WebSocketConfig(TimeWebSocketHandler timeWebSocketHandler) {
        this.timeWebSocketHandler = timeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(timeWebSocketHandler, "/api/ws/time")
                .setAllowedOriginPatterns("*");
    }
}