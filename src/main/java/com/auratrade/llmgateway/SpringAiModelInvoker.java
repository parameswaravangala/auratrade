package com.auratrade.llmgateway;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

/**
 * Ollama adapter. All Spring AI types live in this one class (ArchitectureTest enforces that
 * nothing outside llmgateway uses them). A Claude adapter will replace or sit beside this one.
 */
@Component
class SpringAiModelInvoker implements ModelInvoker {

  private final ChatModel chatModel;

  SpringAiModelInvoker(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  @Override
  public InvocationResult invoke(String model, String system, String user) {
    return invoke(model, system, user, CallOptions.DEFAULT);
  }

  @Override
  public InvocationResult invoke(String model, String system, String user, CallOptions options) {
    var chatOptions = OllamaChatOptions.builder().model(model);
    if (options.temperature() != null) {
      chatOptions.temperature(options.temperature());
    }
    if (options.seed() != null) {
      chatOptions.seed(options.seed());
    }
    if (options.jsonOutput()) {
      chatOptions.format("json");
    }

    Prompt prompt =
        new Prompt(List.of(new SystemMessage(system), new UserMessage(user)), chatOptions.build());

    ChatResponse response = chatModel.call(prompt);

    String text = response.getResult().getOutput().getText();
    Usage u = response.getMetadata().getUsage();
    // Ollama reports no cache tokens, so everything is fresh input.
    TokenUsage usage =
        new TokenUsage(count(u.getPromptTokens()), count(u.getCompletionTokens()), 0, 0);
    return new InvocationResult(text == null ? "" : text, usage);
  }

  private static long count(Number n) {
    return n == null ? 0 : n.longValue();
  }
}
