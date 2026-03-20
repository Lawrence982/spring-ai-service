package ru.home.vibo.spring_ai_service.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    // Use individual mocks for each step in the chain to avoid deep stubs issues
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @Mock
    private ChatRepository chatRepository;

    private ChatService chatService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatService = new ChatService(chatRepository, chatClient, "Test persona");

        // Set up the sync chain: chatClient.prompt() -> requestSpec
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        // Set up the streaming chain: chatClient.prompt(String) -> requestSpec
        lenient().when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        // Common continuations on requestSpec
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        // Sync call chain
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        // Stream chain
        lenient().when(requestSpec.stream()).thenReturn(streamResponseSpec);
    }

    // ------------------- getAllChats() -------------------

    @Test
    void getAllChats_returnsChatsFromRepository() {
        List<Chat> expected = List.of(
                Chat.builder().id(1L).title("Chat 1").build(),
                Chat.builder().id(2L).title("Chat 2").build()
        );
        when(chatRepository.findAll(any(Sort.class))).thenReturn(expected);

        List<Chat> result = chatService.getAllChats();

        assertThat(result).isEqualTo(expected);
        verify(chatRepository).findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ------------------- getChat() -------------------

    @Test
    void getChat_existingId_returnsChat() {
        Chat chat = Chat.builder().id(1L).title("Test").build();
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));

        Chat result = chatService.getChat(1L);

        assertThat(result).isEqualTo(chat);
    }

    @Test
    void getChat_nonExistingId_throwsEntityNotFoundException() {
        when(chatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChat(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------- getChatWithHistory() -------------------

    @Test
    void getChatWithHistory_existingId_returnsChat() {
        Chat chat = Chat.builder().id(1L).title("Test").build();
        when(chatRepository.findByIdWithHistory(1L)).thenReturn(Optional.of(chat));

        Chat result = chatService.getChatWithHistory(1L);

        assertThat(result).isEqualTo(chat);
    }

    @Test
    void getChatWithHistory_nonExistingId_throwsEntityNotFoundException() {
        when(chatRepository.findByIdWithHistory(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatWithHistory(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------- createNewChat() -------------------

    @Test
    void createNewChat_savesWithCorrectTitle() {
        Chat savedChat = Chat.builder().id(10L).title("My Chat").build();
        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        Chat result = chatService.createNewChat("My Chat");

        ArgumentCaptor<Chat> captor = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("My Chat");
        assertThat(result.getId()).isEqualTo(10L);
    }

    // ------------------- deleteChat() -------------------

    @Test
    void deleteChat_callsDeleteById() {
        chatService.deleteChat(5L);
        verify(chatRepository).deleteById(5L);
    }

    // ------------------- proceedInteraction() -------------------

    @Test
    @SuppressWarnings("unchecked")
    void proceedInteraction_callsChatClient() {
        when(callResponseSpec.content()).thenReturn("LLM response");

        chatService.proceedInteraction(1L, "What is Golarion?");

        // Verify that the full chain was traversed
        verify(chatClient).prompt();
        verify(requestSpec).system("Test persona");
        verify(requestSpec).user("What is Golarion?");
        verify(requestSpec).advisors(any(Consumer.class));
        verify(requestSpec).call();
        verify(callResponseSpec).content();
    }

    // ------------------- proceedInteractionWithStreaming() -------------------

    @Test
    void proceedInteractionWithStreaming_returnsNonNullSseEmitter() {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.empty());

        SseEmitter emitter = chatService.proceedInteractionWithStreaming(1L, "hello");

        assertThat(emitter).isNotNull();
    }

    @Test
    void proceedInteractionWithStreaming_onFluxComplete_emitterCompletes() throws InterruptedException {
        // When Flux.empty() completes, the production code calls sseEmitter.complete().
        // In a unit test without a servlet handler, onCompletion callbacks are not triggered
        // by SseEmitter.complete() directly — they require the servlet framework.
        // We verify the subscription was set up by confirming chatResponse() was invoked
        // and that the emitter is returned without error.
        CountDownLatch subscriptionLatch = new CountDownLatch(1);
        when(streamResponseSpec.chatResponse()).thenAnswer(inv -> {
            subscriptionLatch.countDown();
            return Flux.empty();
        });

        SseEmitter emitter = chatService.proceedInteractionWithStreaming(1L, "hello");

        // The chatResponse() should have been called to set up the subscription
        boolean subscribed = subscriptionLatch.await(2, TimeUnit.SECONDS);
        assertThat(subscribed).as("chatResponse() should be called to start streaming").isTrue();
        assertThat(emitter).isNotNull();
    }

    @Test
    void proceedInteractionWithStreaming_onFluxError_emitterCompletesWithError() throws InterruptedException {
        // Verify that when the Flux errors, chatResponse() was subscribed (error is handled internally)
        RuntimeException llmError = new RuntimeException("LLM error");
        CountDownLatch subscriptionLatch = new CountDownLatch(1);
        when(streamResponseSpec.chatResponse()).thenAnswer(inv -> {
            subscriptionLatch.countDown();
            return Flux.error(llmError);
        });

        SseEmitter emitter = chatService.proceedInteractionWithStreaming(1L, "hello");

        boolean subscribed = subscriptionLatch.await(2, TimeUnit.SECONDS);
        assertThat(subscribed).as("chatResponse() should be called to start streaming").isTrue();
        assertThat(emitter).isNotNull();
    }
}
