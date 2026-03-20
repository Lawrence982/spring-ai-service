package ru.home.vibo.spring_ai_service.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEntryTest {

    @Test
    void toChatEntry_fromUserMessage_setsRoleAndContent() {
        UserMessage userMessage = new UserMessage("What is Golarion?");
        ChatEntry entry = ChatEntry.toChatEntry(userMessage);

        assertThat(entry.getRole()).isEqualTo(Role.USER);
        assertThat(entry.getContent()).isEqualTo("What is Golarion?");
    }

    @Test
    void toChatEntry_fromAssistantMessage_setsAssistantRole() {
        AssistantMessage assistantMessage = new AssistantMessage("Golarion is a world...");
        ChatEntry entry = ChatEntry.toChatEntry(assistantMessage);

        assertThat(entry.getRole()).isEqualTo(Role.ASSISTANT);
        assertThat(entry.getContent()).isEqualTo("Golarion is a world...");
    }

    @Test
    void toChatEntry_doesNotSetId_idIsNull() {
        UserMessage userMessage = new UserMessage("hello");
        ChatEntry entry = ChatEntry.toChatEntry(userMessage);

        assertThat(entry.getId()).isNull();
    }

    @Test
    void toChatEntry_doesNotSetChat_chatIsNull() {
        UserMessage userMessage = new UserMessage("hello");
        ChatEntry entry = ChatEntry.toChatEntry(userMessage);

        assertThat(entry.getChat()).isNull();
    }

    @Test
    void toMessage_withUserRole_returnsUserMessage() {
        ChatEntry entry = ChatEntry.builder()
                .role(Role.USER)
                .content("user question")
                .build();

        Message message = entry.toMessage();

        assertThat(message).isInstanceOf(UserMessage.class);
        assertThat(message.getText()).isEqualTo("user question");
    }

    @Test
    void toMessage_withAssistantRole_returnsAssistantMessage() {
        ChatEntry entry = ChatEntry.builder()
                .role(Role.ASSISTANT)
                .content("assistant answer")
                .build();

        Message message = entry.toMessage();

        assertThat(message).isInstanceOf(AssistantMessage.class);
        assertThat(message.getText()).isEqualTo("assistant answer");
    }

    @Test
    void roundTrip_userMessage_preservesContent() {
        String originalText = "Tell me about tanuki";
        UserMessage originalMessage = new UserMessage(originalText);

        ChatEntry entry = ChatEntry.toChatEntry(originalMessage);
        Message reconstructed = entry.toMessage();

        assertThat(reconstructed.getText()).isEqualTo(originalText);
        assertThat(reconstructed).isInstanceOf(UserMessage.class);
    }
}
