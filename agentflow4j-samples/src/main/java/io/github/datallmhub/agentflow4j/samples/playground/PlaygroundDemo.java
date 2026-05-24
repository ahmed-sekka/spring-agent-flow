package io.github.datallmhub.agentflow4j.samples.playground;

import java.time.Duration;

import io.github.datallmhub.agentflow4j.core.Agent;
import io.github.datallmhub.agentflow4j.core.AgentContext;
import io.github.datallmhub.agentflow4j.core.AgentEvent;
import io.github.datallmhub.agentflow4j.core.AgentResult;
import io.github.datallmhub.agentflow4j.samples.HnRadarDemo;
import io.github.datallmhub.agentflow4j.samples.SupportTriageDemo;
import io.github.datallmhub.agentflow4j.squad.ExecutorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

/**
 * Boots a Spring Boot web app that auto-wires the
 * {@code agentflow4j-playground}. Open
 * <a href="http://localhost:8080/playground">http://localhost:8080/playground</a>
 * and chat with the agents below.
 *
 * <p>Run offline:
 * <pre>
 * mvn -pl agentflow4j-samples -am spring-boot:run \
 *     -Dspring-boot.run.mainClass=io.github.datallmhub.agentflow4j.samples.PlaygroundDemo
 * </pre>
 *
 * <p>The {@code mistral} agent only registers when {@code MISTRAL_API_KEY}
 * is exported, so the demo works with zero credentials out of the box.
 */
@SpringBootApplication
public class PlaygroundDemo {

    private static final Logger log = LoggerFactory.getLogger(PlaygroundDemo.class);

    public static void main(String[] args) {
        SpringApplication.run(PlaygroundDemo.class, args);
        log.info("Playground ready: http://localhost:8080/playground");
    }

    /**
     * Pure offline agent — echoes the last user message back in uppercase.
     * Useful to validate the playground works without any credentials.
     */
    @Bean
    Agent echo() {
        return ctx -> AgentResult.ofText("ECHO: " + lastUserMessage(ctx).toUpperCase());
    }

    /**
     * Demonstrates token-by-token streaming. Splits a canned reply into words
     * and emits them at 80ms intervals so the UI shows the typewriter effect.
     */
    @Bean
    Agent streamer() {
        String reply = "Streaming tokens through Server-Sent Events feels much "
                + "snappier than waiting for the full response.";
        String[] words = reply.split(" ");

        return new Agent() {
            @Override
            public AgentResult execute(AgentContext context) {
                return AgentResult.ofText(reply);
            }

            @Override
            public Flux<AgentEvent> executeStream(AgentContext context) {
                Flux<AgentEvent> tokens = Flux.interval(Duration.ofMillis(80))
                        .take(words.length)
                        .map(i -> AgentEvent.token(words[i.intValue()] + " "));
                return tokens.concatWith(Flux.just(AgentEvent.completed(AgentResult.ofText(reply))));
            }
        };
    }

    /**
     * Multi-node graph agent — a customer-support ticket flowing through
     * triage → specialist → policy → reply. Runs in deterministic stub mode
     * (no API key) so the Trace panel always shows a rich, reproducible
     * node path. This is the agent that best showcases the execution trace.
     */
    @Bean
    Agent support() {
        return SupportTriageDemo.buildGraph(null);
    }

    /**
     * Real-network graph: scans Hacker News for a topic and synthesises a
     * markdown digest. The {@code search} node performs a real HTTPS call to
     * the HN Algolia API, so the execution trace shows genuine latency rather
     * than fake sleeps — type something like "rust" or "kubernetes" and watch
     * the {@code search} step take its time.
     */
    @Bean
    Agent radar() {
        return HnRadarDemo.buildGraph();
    }

    /**
     * Real LLM-backed agent. Provider-agnostic — Spring AI auto-configures a
     * {@link ChatModel} from whichever {@code spring-ai-starter-model-*}
     * dependency is on the classpath (Mistral, OpenAI, Anthropic, Gemini,
     * Ollama, ...). See {@code docs/llm-providers.md} for the swap recipe.
     * The bean only registers when a {@link ChatModel} is present, so the
     * playground stays clone-and-run without credentials.
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    Agent llm(ChatModel chatModel) {
        return ExecutorAgent.builder()
                .name("llm")
                .chatClient(ChatClient.builder(chatModel).build())
                .systemPrompt("You are a concise, friendly assistant. Answer in at most 3 sentences.")
                .build();
    }

    private static String lastUserMessage(AgentContext ctx) {
        return ctx.messages().stream()
                .reduce((a, b) -> b)
                .map(Message::getText)
                .orElse("(no message)");
    }
}
