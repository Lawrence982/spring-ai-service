package ru.home.vibo.spring_ai_service.service;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;
    private final ChatClient chatClient;
    private final String personaPrompt;

    public ChatService(ChatRepository chatRepository, ChatClient chatClient,
                       @Value("${app.llm.persona-prompt}") String personaPrompt) {
        this.chatRepository = chatRepository;
        this.chatClient = chatClient;
        this.personaPrompt = personaPrompt;
    }

    public List<Chat> getAllChats() {
        return chatRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Chat getChat(Long chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));
    }

    public Chat getChatWithHistory(Long chatId) {
        return chatRepository.findByIdWithHistory(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + chatId));
    }

    public Chat createNewChat(String title) {
        Chat chat = Chat.builder().title(title).build();
        return chatRepository.save(chat);
    }

    public void deleteChat(Long chatId) {
        chatRepository.deleteById(chatId);
    }

    public void proceedInteraction(Long chatId, String prompt) {
        chatClient.prompt()
                .system(personaPrompt)
                .user(prompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    public SseEmitter proceedInteractionWithStreaming(Long chatId, String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);

        // Keepalive: отправляем SSE-комментарий каждые 15 сек.
        // Без этого прокси/браузер закрывает idle-соединение во время долгого tool execution.
        AtomicBoolean keepAlive = new AtomicBoolean(true);
        Thread pingThread = Thread.ofVirtual().name("sse-ping-" + chatId).start(() -> {
            while (keepAlive.get()) {
                try {
                    Thread.sleep(15_000);
                    if (keepAlive.get()) {
                        sseEmitter.send(SseEmitter.event().comment("ping"));
                    }
                } catch (IOException | InterruptedException e) {
                    break;
                }
            }
        });

        chatClient
                .prompt(userPrompt)
                .system(personaPrompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .subscribe(
                        response -> processToken(response, sseEmitter, completed),
                        error -> {
                            keepAlive.set(false);
                            pingThread.interrupt();
                            if (completed.compareAndSet(false, true)) {
                                sseEmitter.completeWithError(error);
                            }
                        },
                        () -> {
                            keepAlive.set(false);
                            pingThread.interrupt();
                            if (completed.compareAndSet(false, true)) {
                                sseEmitter.complete();
                            }
                        });
        return sseEmitter;
    }

    private void processToken(ChatResponse response, SseEmitter emitter, AtomicBoolean completed) {
        var token = response.getResult().getOutput();
        String text = token.getText();
        // Пропускаем tool call chunks — у них text == null, а toolCalls заполнен.
        // Пользователь видит только финальный текстовый ответ.
        if (text == null || text.isBlank()) return;
        try {
            emitter.send(token);
        } catch (IOException e) {
            log.warn("processToken: client disconnected, completing emitter", e);
            if (completed.compareAndSet(false, true)) {
                emitter.completeWithError(e);
            }
        } catch (IllegalStateException e) {
            log.warn("processToken: emitter already completed, client likely disconnected");
        }
    }
}
