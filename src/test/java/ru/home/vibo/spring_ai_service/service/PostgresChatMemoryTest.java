package ru.home.vibo.spring_ai_service.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.model.ChatEntry;
import ru.home.vibo.spring_ai_service.model.Role;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresChatMemoryTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatEntryPersistence entryPersistence;

    private PostgresChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        chatMemory = PostgresChatMemory.builder()
                .chatMemoryRepository(chatRepository)
                .entryPersistence(entryPersistence)
                .maxMessages(12)
                .build();
    }

    // ------------------- get() tests -------------------

    @Test
    void get_chatNotFound_throwsEntityNotFoundException() {
        when(chatRepository.findByIdWithHistory(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMemory.get("99"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void get_emptyHistory_returnsSeedMessages() {
        Chat chat = Chat.builder().id(1L).title("Test").build();
        // history is empty by default via @Builder.Default new ArrayList<>()
        when(chatRepository.findByIdWithHistory(1L)).thenReturn(Optional.of(chat));

        List<Message> messages = chatMemory.get("1");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("Расскажи о себе");
    }

    @Test
    void get_historyWithinLimit_returnsAllMessages() {
        List<ChatEntry> entries = buildEntries(5);
        Chat chat = Chat.builder().id(1L).title("Test").history(entries).build();
        when(chatRepository.findByIdWithHistory(1L)).thenReturn(Optional.of(chat));

        List<Message> messages = chatMemory.get("1");

        assertThat(messages).hasSize(5);
    }

    @Test
    void get_historyExceedsLimit_returnsLastMaxMessages() {
        List<ChatEntry> entries = buildEntries(15);
        Chat chat = Chat.builder().id(1L).title("Test").history(entries).build();
        when(chatRepository.findByIdWithHistory(1L)).thenReturn(Optional.of(chat));

        List<Message> messages = chatMemory.get("1");

        assertThat(messages).hasSize(12);
        // Verify these are the LAST 12 by checking the content of the first returned message
        // entries are indexed 0..14; skipping 3 means we start at entry index 3
        assertThat(messages.get(0).getText()).isEqualTo("content-3");
    }

    @Test
    void get_messagesConvertedToCorrectType() {
        ChatEntry userEntry = ChatEntry.builder()
                .role(Role.USER)
                .content("What is a tanuki?")
                .build();
        List<ChatEntry> entries = new ArrayList<>();
        entries.add(userEntry);
        Chat chat = Chat.builder().id(1L).title("Test").history(entries).build();
        when(chatRepository.findByIdWithHistory(1L)).thenReturn(Optional.of(chat));

        List<Message> messages = chatMemory.get("1");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("What is a tanuki?");
    }

    // ------------------- add() tests -------------------

    @Test
    void add_userMessage_callsEntryPersistence() {
        UserMessage userMessage = new UserMessage("msg");

        chatMemory.add("1", List.of(userMessage));

        verify(entryPersistence).addEntry(1L, "msg", Role.USER);
    }

    @Test
    void add_assistantMessage_callsEntryPersistence() {
        AssistantMessage assistantMessage = new AssistantMessage("real answer");

        chatMemory.add("1", List.of(assistantMessage));

        verify(entryPersistence).addEntry(1L, "real answer", Role.ASSISTANT);
    }

    @Test
    void add_assistantMessageWithNullText_skipped() {
        // AssistantMessage with null text should be skipped (tool call chunk)
        AssistantMessage assistantMessage = new AssistantMessage(null);

        chatMemory.add("1", List.of(assistantMessage));

        verifyNoInteractions(entryPersistence);
    }

    @Test
    void add_assistantMessageWithBlankText_skipped() {
        AssistantMessage assistantMessage = new AssistantMessage("   ");

        chatMemory.add("1", List.of(assistantMessage));

        verifyNoInteractions(entryPersistence);
    }

    @Test
    void add_mixedMessages_onlyValidOnesPersistedTwice() {
        UserMessage user = new UserMessage("q");
        AssistantMessage blank = new AssistantMessage("");
        AssistantMessage real = new AssistantMessage("real answer");

        chatMemory.add("1", List.of(user, blank, real));

        // user + real = 2 calls; blank is skipped
        verify(entryPersistence, times(2)).addEntry(eq(1L), any(), any());
        verify(entryPersistence).addEntry(1L, "q", Role.USER);
        verify(entryPersistence).addEntry(1L, "real answer", Role.ASSISTANT);
    }

    @Test
    void clear_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> chatMemory.clear("1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------- helpers -------------------

    private List<ChatEntry> buildEntries(int count) {
        List<ChatEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Role role = (i % 2 == 0) ? Role.USER : Role.ASSISTANT;
            entries.add(ChatEntry.builder()
                    .role(role)
                    .content("content-" + i)
                    .build());
        }
        return entries;
    }
}
