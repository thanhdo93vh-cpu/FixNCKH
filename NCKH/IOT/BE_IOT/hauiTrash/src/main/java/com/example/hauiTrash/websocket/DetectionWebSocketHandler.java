package com.example.hauiTrash.websocket;

import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import com.example.hauiTrash.service.AiYoloService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class DetectionWebSocketHandler extends BinaryWebSocketHandler {

    private final AiYoloService aiYoloService;
    private final ObjectMapper objectMapper;

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            java.nio.ByteBuffer byteBuffer = message.getPayload();
            byte[] payload = new byte[byteBuffer.remaining()];
            byteBuffer.get(payload);
            
            // Gửi sang hàm xử lý realtime
            RealtimeDetectionResponse response = aiYoloService.detectRealtime(payload);
            
            // Nếu không có lỗi, phản hồi kết quả về Frontend dưới dạng JSON Text
            if (response == null) {
                response = RealtimeDetectionResponse.builder()
                        .label(null)
                        .labelDisplay("Không phát hiện rác")
                        .confidence(0.0f)
                        .build();
            }
            
            String jsonOutput = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(jsonOutput));

        } catch (Exception e) {
            log.error("Lỗi khi xử lý frame qua WebSocket: ", e);
        }
    }
}
