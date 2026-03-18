package ru.home.vibo.spring_ai_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.model.ChatEntry;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

import java.util.List;

@Builder
public class PostgresChatMemory implements ChatMemory {

    private static final List<Message> SEED_MESSAGES = List.of(
            new UserMessage("Расскажи о себе"),
            new AssistantMessage("Я Чио — девушка-тануки, бродячий сыщик из мира Голариона! " +
                    "Путешествую по свету, везде нахожу нужных людей и нужные сведения. " +
                    "Спрашивай что угодно — разведаю!")
    );

    private ChatRepository chatMemoryRepository;

    private ChatEntryPersistence entryPersistence;

    private int maxMessages;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long chatId = Long.valueOf(conversationId);
        for (Message message : messages) {
            // Пропускаем AssistantMessage с пустым текстом — это промежуточные tool call chunks,
            // которые Spring AI может передать в advisor. В БД нужен только финальный ответ.
            if (message instanceof AssistantMessage am
                    && (am.getText() == null || am.getText().isBlank())) {
                continue;
            }
            ChatEntry entry = ChatEntry.toChatEntry(message);
            entryPersistence.addEntry(chatId, entry.getContent(), entry.getRole());
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        Chat chat = chatMemoryRepository.findByIdWithHistory(Long.valueOf(conversationId))
                .orElseThrow(() -> new EntityNotFoundException("Chat not found with id: " + conversationId));
        if (chat.getHistory().isEmpty()) {
            return SEED_MESSAGES;
        }
        long messagesToSkip = Math.max(0, chat.getHistory().size() - maxMessages);
        return chat.getHistory()
                .stream()
                .skip(messagesToSkip)
                .map(ChatEntry::toMessage)
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        // not implemented
    }
}
