package ru.home.vibo.spring_ai_service.service;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final ChatModel chatModel;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatClient samplingChatClient;

    public McpClientManager(ChatModel chatModel, ToolCallbackProvider toolCallbackProvider) {
        this.chatModel = chatModel;
        this.toolCallbackProvider = toolCallbackProvider;
        this.samplingChatClient = ChatClient.builder(chatModel).build();
    }

    @PostConstruct
    public void logAvailableTools() {
        var tools = toolCallbackProvider.getToolCallbacks();
        log.info("MCP: зарегистрировано инструментов: {}", tools.length);
        for (var tool : tools) {
            var def = tool.getToolDefinition();
            log.info("  Tool '{}': description='{}', inputSchema={}",
                    def.name(), def.description(), def.inputSchema());
        }
    }

    @McpLogging(clients = "mcp-server")
    public void logClient(McpSchema.LoggingMessageNotification loggingMessageNotification) {
        log.info("Клиент говорит: я получил послание от сервера - {}", loggingMessageNotification.data());
    }

    @McpSampling(clients = "mcp-server")
    public McpSchema.CreateMessageResult sampling(McpSchema.CreateMessageRequest createMessageRequest) {
        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
                .numPredict(createMessageRequest.maxTokens());
        if (createMessageRequest.temperature() != null) {
            optionsBuilder.temperature(createMessageRequest.temperature());
        }

        String userContent = createMessageRequest.messages().stream()
                .filter(m -> m.role() == McpSchema.Role.USER)
                .reduce((first, second) -> second)
                .map(m -> m.content() instanceof McpSchema.TextContent tc ? tc.text() : "")
                .orElse("");

        String samplingAnswer = samplingChatClient
                .prompt()
                .options(optionsBuilder.build())
                .system(createMessageRequest.systemPrompt() != null ? createMessageRequest.systemPrompt() : "")
                .user(userContent)
                .call()
                .content();

        return McpSchema.CreateMessageResult.builder()
                .role(McpSchema.Role.ASSISTANT)
                .content(new McpSchema.TextContent(samplingAnswer != null ? samplingAnswer : ""))
                .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                .build();
    }
}
