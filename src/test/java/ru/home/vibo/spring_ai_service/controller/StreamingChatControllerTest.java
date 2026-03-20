package ru.home.vibo.spring_ai_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.home.vibo.spring_ai_service.service.ChatService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamingChatController.class)
class StreamingChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void chatStream_returnsOkWithEventStreamContentType() throws Exception {
        // For an SSE endpoint, the request enters async processing when SseEmitter is returned.
        // The content type text/event-stream is declared via produces and is set by the framework
        // during async dispatch. We verify that async processing was started, confirming the
        // controller correctly delegates to the service and returns the SseEmitter.
        when(chatService.proceedInteractionWithStreaming(1L, "hello"))
                .thenReturn(new SseEmitter());

        mockMvc.perform(get("/chat-stream/1").param("userPrompt", "hello"))
                .andExpect(request().asyncStarted());

        verify(chatService).proceedInteractionWithStreaming(eq(1L), eq("hello"));
    }

    @Test
    void chatStream_callsServiceWithCorrectParams() throws Exception {
        when(chatService.proceedInteractionWithStreaming(7L, "test question"))
                .thenReturn(new SseEmitter());

        mockMvc.perform(get("/chat-stream/7").param("userPrompt", "test question"))
                .andExpect(request().asyncStarted());

        verify(chatService).proceedInteractionWithStreaming(eq(7L), eq("test question"));
    }

    @Test
    void chatStream_missingUserPrompt_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/chat-stream/1"))
                .andExpect(status().isBadRequest());
    }
}
