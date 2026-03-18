package ru.home.vibo.spring_ai_service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.home.vibo.spring_ai_service.advisors.expansion.ExpansionQueryAdvisor;
import ru.home.vibo.spring_ai_service.advisors.rag.RagAdvisor;
import ru.home.vibo.spring_ai_service.repository.ChatRepository;
import ru.home.vibo.spring_ai_service.service.ChatEntryPersistence;
import ru.home.vibo.spring_ai_service.service.PostgresChatMemory;

@Configuration
@RequiredArgsConstructor
public class AiConfiguration {

    private final ChatRepository chatRepository;
    private final ChatEntryPersistence entryPersistence;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        return builder.defaultAdvisors(
                ExpansionQueryAdvisor.builder(chatModel).order(0).build(),
                getHistoryAdvisor(1),
                SimpleLoggerAdvisor.builder().order(2).build(),
                RagAdvisor.builder(vectorStore).order(3).build(),
                SimpleLoggerAdvisor.builder().order(4).build())
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultOptions(OllamaChatOptions.builder()
                        .temperature(0.3).topP(0.7).topK(20).repeatPenalty(1.1).build())
                .build();
    }

    private Advisor getHistoryAdvisor(int order) {
        return MessageChatMemoryAdvisor.builder(getChatMemory()).order(order).build();
    }

    private ChatMemory getChatMemory() {
        return PostgresChatMemory.builder()
                .maxMessages(12)
                .chatMemoryRepository(chatRepository)
                .entryPersistence(entryPersistence)
                .build();
    }

}
