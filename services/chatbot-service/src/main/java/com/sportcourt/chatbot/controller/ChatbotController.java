package com.sportcourt.chatbot.controller;

import com.sportcourt.chatbot.dto.ChatMessageRequest;
import com.sportcourt.chatbot.dto.ChatMessageResponse;
import com.sportcourt.chatbot.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/messages")
    public ChatMessageResponse message(@Valid @RequestBody ChatMessageRequest request) {
        return chatbotService.handleMessage(request);
    }
}
