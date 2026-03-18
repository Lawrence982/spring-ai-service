package ru.home.vibo.spring_ai_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.model.ChatEntry;
import ru.home.vibo.spring_ai_service.model.Role;
import ru.home.vibo.spring_ai_service.repository.ChatEntryRepository;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

@Service
@RequiredArgsConstructor
public class ChatEntryPersistence {

    private final ChatRepository chatRepository;
    private final ChatEntryRepository chatEntryRepository;

    @Transactional
    public void addEntry(Long chatId, String content, Role role) {
        Chat chatRef = chatRepository.getReferenceById(chatId);
        chatEntryRepository.save(ChatEntry.builder().content(content).role(role).chat(chatRef).build());
    }
}
