package io.github.datallmhub.agentflow4j.samples;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.datallmhub.agentflow4j.core.Agent;
import io.github.datallmhub.agentflow4j.core.AgentContext;
import io.github.datallmhub.agentflow4j.core.AgentEvent;
import io.github.datallmhub.agentflow4j.core.AgentResult;
import io.github.datallmhub.agentflow4j.core.StateKey;
import io.github.datallmhub.agentflow4j.graph.AgentGraph;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * Real-world example: a 4-node "tech radar" graph that scans Hacker News for
 * recent stories about a topic and produces a short markdown digest.
 *
 * <p>Authentic latency: the {@code search} node performs a real HTTPS call to
 * the Hacker News Algolia API (public, no key required), so the execution
 * trace shows a genuine pause on that node rather than fake {@code sleep()}s.
 *
 * <p>Flow:
 * <pre>
 *   parse → search → classify → synthesize
 * </pre>
 *
 * <ul>
 *   <li>{@code parse}      — pulls the topic from the last user message</li>
 *   <li>{@code search}     — HN Algolia API, top 5 recent stories (network-bound)</li>
 *   <li>{@code classify}   — computes the average HN score (deterministic)</li>
 *   <li>{@code synthesize} — builds the markdown digest (deterministic)</li>
 * </ul>
 */
public final class HnRadarDemo {

    /** A single Hacker News story projected to the fields we use. */
    public record Story(String title, String url, int points) {}

    static final StateKey<String>  TOPIC      = StateKey.of("radar.topic",      String.class);
    static final StateKey<Story[]> STORIES    = StateKey.of("radar.stories",    Story[].class);
    static final StateKey<Double>  AVG_POINTS = StateKey.of("radar.avg_points", Double.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HnRadarDemo() {}

    public static AgentGraph buildGraph() {
        Agent parse = ctx -> {
            String topic = lastUserMessage(ctx).trim();
            if (topic.isEmpty()) {
                topic = "ai";
            }
            return AgentResult.builder()
                    .text("topic: " + topic)
                    .stateUpdates(Map.of(TOPIC, topic))
                    .completed(true)
                    .build();
        };

        Agent search = ctx -> {
            String topic = ctx.get(TOPIC);
            Story[] stories = fetchHnStories(topic, 5);
            return AgentResult.builder()
                    .text("found " + stories.length + " stories")
                    .stateUpdates(Map.of(STORIES, stories))
                    .completed(true)
                    .build();
        };

        Agent classify = ctx -> {
            Story[] stories = ctx.get(STORIES);
            double avg = stories.length == 0
                    ? 0.0
                    : Arrays.stream(stories).mapToInt(Story::points).average().orElse(0.0);
            return AgentResult.builder()
                    .text("avg HN score: " + (int) avg)
                    .stateUpdates(Map.of(AVG_POINTS, avg))
                    .completed(true)
                    .build();
        };

        Agent synthesize = new Agent() {
            @Override
            public AgentResult execute(AgentContext context) {
                return AgentResult.ofText(buildMarkdown(context));
            }

            @Override
            public Flux<AgentEvent> executeStream(AgentContext context) {
                String text = buildMarkdown(context);
                List<String> chunks = tokenize(text);
                Flux<AgentEvent> tokens = Flux.interval(Duration.ofMillis(40))
                        .take(chunks.size())
                        .map(i -> AgentEvent.token(chunks.get(i.intValue())));
                return tokens.concatWith(Flux.just(
                        AgentEvent.completed(AgentResult.ofText(text))));
            }
        };

        return AgentGraph.builder()
                .name("hn-radar")
                .addNode("parse",      parse)
                .addNode("search",     search)
                .addNode("classify",   classify)
                .addNode("synthesize", synthesize)
                .addEdge("parse",    "search")
                .addEdge("search",   "classify")
                .addEdge("classify", "synthesize")
                .build();
    }

    public static void main(String[] args) {
        String topic = args.length > 0 ? String.join(" ", args) : "spring ai";
        AgentResult result = buildGraph().invoke(AgentContext.of(topic));
        System.out.println(result.text());
    }

    private static Story[] fetchHnStories(String topic, int max) {
        String url = "https://hn.algolia.com/api/v1/search?tags=story&hitsPerPage=" + max
                + "&query=" + URLEncoder.encode(topic, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("HN API returned " + response.statusCode());
            }
            JsonNode hits = JSON.readTree(response.body()).path("hits");
            List<Story> stories = new ArrayList<>();
            for (int i = 0; i < hits.size() && i < max; i++) {
                JsonNode h = hits.get(i);
                String title = h.path("title").asText(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                stories.add(new Story(
                        title,
                        h.path("url").asText(""),
                        h.path("points").asInt(0)));
            }
            return stories.toArray(new Story[0]);
        }
        catch (java.io.IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HN fetch failed: " + ex.getMessage(), ex);
        }
    }

    static String buildMarkdown(AgentContext ctx) {
        String topic = ctx.get(TOPIC);
        Story[] stories = ctx.get(STORIES);
        Double avgBoxed = ctx.get(AVG_POINTS);
        double avg = avgBoxed != null ? avgBoxed : 0.0;
        StringBuilder sb = new StringBuilder()
                .append("**Hacker News radar — ").append(topic).append("**\n\n")
                .append("Average score across top ").append(stories.length)
                .append(" stories: **").append((int) avg).append("**\n\n");
        if (stories.length == 0) {
            sb.append("_No stories found._");
        }
        for (Story s : stories) {
            sb.append("- (").append(s.points()).append(") ")
                    .append(s.title()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Chunk a string into word-sized tokens that preserve newlines, so the
     * streamed synthesis renders identically to the final markdown.
     */
    static List<String> tokenize(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur.append(c);
            if (c == ' ' || c == '\n') {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            chunks.add(cur.toString());
        }
        return chunks;
    }

    private static String lastUserMessage(AgentContext ctx) {
        return ctx.messages().stream()
                .reduce((a, b) -> b)
                .map(Message::getText)
                .orElse("");
    }
}
