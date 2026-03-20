package ru.home.vibo.spring_ai_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.model.ChatEntry;
import ru.home.vibo.spring_ai_service.model.Role;
import ru.home.vibo.spring_ai_service.repository.ChatEntryRepository;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEntryPersistenceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatEntryRepository chatEntryRepository;

    private ChatEntryPersistence chatEntryPersistence;

    @BeforeEach
    void setUp() {
        chatEntryPersistence = new ChatEntryPersistence(chatRepository, chatEntryRepository);
    }

    @Test
    void addEntry_callsGetReferenceById_withChatId() {
        Chat chatRef = Chat.builder().id(42L).build();
        when(chatRepository.getReferenceById(42L)).thenReturn(chatRef);

        chatEntryPersistence.addEntry(42L, "test content", Role.USER);

        verify(chatRepository).getReferenceById(42L);
    }

    @Test
    void addEntry_savesCorrectChatEntry() {
        Chat chatRef = Chat.builder().id(42L).build();
        when(chatRepository.getReferenceById(42L)).thenReturn(chatRef);

        chatEntryPersistence.addEntry(42L, "hello world", Role.USER);

        ArgumentCaptor<ChatEntry> captor = ArgumentCaptor.forClass(ChatEntry.class);
        verify(chatEntryRepository).save(captor.capture());

        ChatEntry saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("hello world");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getChat()).isSameAs(chatRef);
    }

    @Test
    void addEntry_withAssistantRole_savesAssistantRole() {
        Chat chatRef = Chat.builder().id(7L).build();
        when(chatRepository.getReferenceById(7L)).thenReturn(chatRef);

        chatEntryPersistence.addEntry(7L, "assistant response", Role.ASSISTANT);

        ArgumentCaptor<ChatEntry> captor = ArgumentCaptor.forClass(ChatEntry.class);
        verify(chatEntryRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.ASSISTANT);
        assertThat(captor.getValue().getContent()).isEqualTo("assistant response");
    }

    @Test
    void addEntry_neverCallsFindById() {
        Chat chatRef = Chat.builder().id(1L).build();
        when(chatRepository.getReferenceById(1L)).thenReturn(chatRef);

        chatEntryPersistence.addEntry(1L, "content", Role.USER);

        verify(chatRepository, never()).findById(any());
    }
}
