package com.library.seat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.seat.service.ClockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器时间实时推送 WebSocket 处理器
 *
 * <p>客户端连接 /api/ws/time 后：</p>
 * <ul>
 *     <li>立即回送一帧服务器时间，供客户端计算时钟偏差；</li>
 *     <li>随后由 {@link com.library.seat.task.TickBroadcaster} 固定间隔广播。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    private final ClockService clockService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SESSIONS.put(session.getId(), session);
        log.debug("时间同步连接建立: {} 当前在线 {}", session.getId(), SESSIONS.size());
        send(session, clockService.serverFrame());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null) {
            return;
        }
        // 客户端 ping：立即回送服务器时间（用于校时）
        if (payload.contains("ping") || payload.contains("sync")) {
            Map<String, Object> frame = clockService.serverFrame();
            frame.put("type", "pong");
            send(session, frame);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
        log.debug("时间同步连接关闭: {} 剩余 {}", session.getId(), SESSIONS.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        SESSIONS.remove(session.getId());
        log.debug("时间同步连接异常: {}", exception.getMessage());
    }

    /**
     * 广播时间帧
     */
    public void broadcast() {
        if (SESSIONS.isEmpty()) {
            return;
        }
        Map<String, Object> frame = clockService.serverFrame();
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(frame));
        } catch (Exception e) {
            log.warn("序列化时间帧失败", e);
            return;
        }
        SESSIONS.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } else {
                    SESSIONS.remove(session.getId());
                }
            } catch (Exception e) {
                SESSIONS.remove(session.getId());
            }
        });
    }

    public int onlineCount() {
        return SESSIONS.size();
    }

    private void send(WebSocketSession session, Map<String, Object> frame) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
        } catch (Exception e) {
            log.debug("发送时间帧失败: {}", e.getMessage());
        }
    }
}