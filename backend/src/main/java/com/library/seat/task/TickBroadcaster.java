package com.library.seat.task;

import com.library.seat.websocket.TimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 时间帧广播器：每 1000ms 向所有在线客户端推送服务器时间
 */
@Component
@RequiredArgsConstructor
public class TickBroadcaster {

    private final TimeWebSocketHandler handler;

    @Scheduled(fixedRate = 1000)
    public void broadcastTick() {
        handler.broadcast();
    }
}