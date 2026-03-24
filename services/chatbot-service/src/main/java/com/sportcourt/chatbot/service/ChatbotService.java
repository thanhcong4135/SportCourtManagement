package com.sportcourt.chatbot.service;

import com.sportcourt.chatbot.dto.ChatMessageRequest;
import com.sportcourt.chatbot.dto.ChatMessageResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class ChatbotService {

    public ChatMessageResponse handleMessage(ChatMessageRequest request) {
        String normalized = request.message().toLowerCase(Locale.ROOT);

        String intent = "GENERAL_ASSIST";
        double confidence = 0.55;
        String response = "Ban co the dat san, xem lich trong va quan ly booking tai he thong SportCourt.";

        if (normalized.contains("dat san") || normalized.contains("booking")) {
            intent = "BOOKING_ASSIST";
            confidence = 0.87;
            response = "De dat san, ban chon san, khung gio, sau do thanh toan coc toi thieu 50% de xac nhan.";
        } else if (normalized.contains("huy") || normalized.contains("cancel")) {
            intent = "CANCEL_ASSIST";
            confidence = 0.8;
            response = "Ban co the huy booking trong muc quan ly booking. He thong se ap dung chinh sach hoan coc theo quy dinh.";
        } else if (normalized.contains("thanh toan") || normalized.contains("payment")) {
            intent = "PAYMENT_ASSIST";
            confidence = 0.82;
            response = "Sau khi tao draft booking, ban thanh toan coc. Khi thanh toan thanh cong, booking se duoc confirm.";
        }

        return new ChatMessageResponse(
            request.sessionId(),
            request.userId(),
            intent,
            confidence,
            response,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
