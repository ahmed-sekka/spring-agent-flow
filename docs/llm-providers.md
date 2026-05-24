# LLM providers — swap freely

AgentFlow4J builds on top of Spring AI. Anything Spring AI auto-configures as a `ChatModel` works as the brain of an `ExecutorAgent` — Mistral, OpenAI, Anthropic Claude, Google Gemini, a local Ollama, your own. Switching is just **two changes**: the starter dependency, and the API key property.

The playground's `llm` bean is provider-agnostic. It receives whatever `ChatModel` Spring AI built; you decide which one.

## The swap recipe

For each provider, drop in the matching dependency and set the matching property. Pick one — putting multiple model starters on the same classpath is supported, but Spring AI will then need an explicit selector. For a sample, one provider at a time is the cleanest setup.

### Mistral (default in the playground)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-mistral-ai</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    mistralai:
      api-key: ${MISTRAL_API_KEY}
      chat:
        options:
          model: mistral-small-latest
          temperature: 0.3
```

### OpenAI (GPT family)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.3
```

### Anthropic (Claude)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-5-sonnet-latest
          temperature: 0.3
```

### Google (Vertex AI Gemini)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-vertex-ai-gemini</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GOOGLE_PROJECT_ID}
          location: us-central1
          chat:
            options:
              model: gemini-1.5-flash
```

Authentication uses [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials) — `gcloud auth application-default login` for local, or a service account in production.

### Ollama (local, no key)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1
```

Run `ollama serve` locally and `ollama pull llama3.1` first. No key, no rate limit, fully on your machine — ideal for governance-sensitive workflows.

## Nothing else changes

Once the `ChatModel` bean is in the context, every part of AgentFlow4J keeps working unchanged:

- `ExecutorAgent.builder().chatClient(ChatClient.builder(chatModel).build())` — same code, any provider
- `CoordinatorAgent` routing via `RoutingStrategy.llmDriven(chatClient)` — same code
- `BudgetPolicy`, `ToolPolicy`, `StatePolicy`, `ApprovalGate` — provider-independent
- The run log, checkpoints, retries, circuit breaker — provider-independent

This is the point of building on Spring AI: the provider is a swap, not a rewrite.

## Multi-provider in one app

You can mix providers in the same graph — e.g., a cheap model for triage, a strong model for synthesis. Inject two distinct `ChatModel` beans (use Spring's `@Qualifier`) and pass each to the relevant `ExecutorAgent`. Spring AI's docs cover the multi-bean setup; from AgentFlow4J's perspective each agent simply takes a `ChatClient`.
