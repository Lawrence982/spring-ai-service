package ru.home.vibo.spring_ai_service.controller;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.home.vibo.spring_ai_service.model.Chat;
import ru.home.vibo.spring_ai_service.service.ChatService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void mainPage_returnsOkAndChatView() throws Exception {
        when(chatService.getAllChats()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"));
    }

    @Test
    void mainPage_addsChatsToModel() throws Exception {
        List<Chat> chats = List.of(
                Chat.builder().id(1L).title("First Chat").build(),
                Chat.builder().id(2L).title("Second Chat").build()
        );
        when(chatService.getAllChats()).thenReturn(chats);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("chats", chats));
    }

    @Test
    void showChat_returnsOkAndAddsChat() throws Exception {
        List<Chat> chats = List.of(Chat.builder().id(1L).title("Test Chat").build());
        Chat chat = Chat.builder().id(1L).title("Test Chat").build();

        when(chatService.getAllChats()).thenReturn(chats);
        when(chatService.getChatWithHistory(1L)).thenReturn(chat);

        mockMvc.perform(get("/chat/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(model().attribute("chats", chats))
                .andExpect(model().attribute("chat", chat));
    }

    @Test
    void newChat_createsAndRedirects() throws Exception {
        Chat savedChat = Chat.builder().id(42L).title("My Chat").build();
        when(chatService.createNewChat("My Chat")).thenReturn(savedChat);

        mockMvc.perform(post("/chat/new").param("title", "My Chat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat/42"));
    }

    @Test
    void deleteChat_deletesAndRedirects() throws Exception {
        mockMvc.perform(post("/chat/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(chatService).deleteChat(1L);
    }

    @Test
    void getChat_notFound_returns404() throws Exception {
        when(chatService.getAllChats()).thenReturn(List.of());
        when(chatService.getChatWithHistory(99L)).thenThrow(new EntityNotFoundException("Chat not found with id: 99"));

        mockMvc.perform(get("/chat/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void talkToModel_callsServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/chat/1/entry").param("prompt", "hello"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat/1"));

        verify(chatService).proceedInteraction(1L, "hello");
    }
}
