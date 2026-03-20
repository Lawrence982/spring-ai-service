package ru.home.vibo.spring_ai_service.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleTest {

    @Test
    void getRole_validUserString_returnsUserRole() {
        Role result = Role.getRole("user");
        assertThat(result).isEqualTo(Role.USER);
    }

    @Test
    void getRole_validAssistantString_returnsAssistantRole() {
        Role result = Role.getRole("assistant");
        assertThat(result).isEqualTo(Role.ASSISTANT);
    }

    @Test
    void getRole_validSystemString_returnsSystemRole() {
        Role result = Role.getRole("system");
        assertThat(result).isEqualTo(Role.SYSTEM);
    }

    @Test
    void getRole_unknownString_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Role.getRole("unknown")
        );
        assertThat(ex.getMessage()).contains("Unknown role: unknown");
    }

    @Test
    void getMessage_user_returnsUserMessage() {
        Message message = Role.USER.getMessage("hello");
        assertThat(message).isInstanceOf(UserMessage.class);
        assertThat(message.getText()).isEqualTo("hello");
    }

    @Test
    void getMessage_assistant_returnsAssistantMessage() {
        Message message = Role.ASSISTANT.getMessage("reply");
        assertThat(message).isInstanceOf(AssistantMessage.class);
        assertThat(message.getText()).isEqualTo("reply");
    }

    @Test
    void getMessage_system_returnsSystemMessage() {
        Message message = Role.SYSTEM.getMessage("system instruction");
        assertThat(message).isInstanceOf(SystemMessage.class);
        assertThat(message.getText()).isEqualTo("system instruction");
    }

    @Test
    void getRole_field_returnsLowercaseName() {
        assertThat(Role.USER.getRole()).isEqualTo("user");
        assertThat(Role.ASSISTANT.getRole()).isEqualTo("assistant");
        assertThat(Role.SYSTEM.getRole()).isEqualTo("system");
    }
}
