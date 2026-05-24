# AgentFlow4J

**Build stateful multi-agent workflows in Java — with graphs, retries, and persistence.**

<p align="center">
<img width="250" height="400" alt="agents" src="https://github.com/user-attachments/assets/7c954ec6-a6f5-42f8-8f84-1de9332debd1" />
</p>

No orchestration code. No glue logic. Just define your agents and run.

[![build](https://github.com/datallmhub/agentflow4j/actions/workflows/build.yml/badge.svg)](https://github.com/datallmhub/agentflow4j/actions)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-green)](https://docs.spring.io/spring-ai/reference/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---
        
## 🚀 Try it in 30 seconds (no API key)

```bash
git clone https://github.com/datallmhub/agentflow4j.git
cd agentflow4j
mvn install -DskipTests -q
mvn -pl agentflow4j-samples exec:java
```

Runs `SupportTriageDemo` — a customer-support ticket flowing through a graph: triage → specialist → policy gate → reply. Falls back to deterministic stubs offline, or calls Mistral when `MISTRAL_API_KEY` is set.

---

## ⚡ In 60 seconds

```java
ExecutorAgent researcher = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Find key facts.")
        .build();

ExecutorAgent writer = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Write a clear report.")
        .build();

CoordinatorAgent coordinator = CoordinatorAgent.builder()
        .executors(Map.of("research", researcher, "writing", writer))
        .routingStrategy(RoutingStrategy.llmDriven(chatClient))
        .build();

AgentResult result = coordinator.execute(
        AgentContext.of("Compare Claude 4 and GPT-5"));
```

A multi-step, stateful workflow with routing, coordination, and resilience — without writing orchestration code.

⭐ **If this saves you time, consider [starring the repo](https://github.com/datallmhub/agentflow4j).**

---

## 🧠 Why AgentFlow4J?

Real-world AI systems are **multi-step**, **stateful**, **failure-prone**, and **long-running**.

Spring AI gives you agent primitives. **AgentFlow4J gives you a runtime.**

| Spring AI | AgentFlow4J |
|---|---|
| Primitives (`ChatClient`, tools) | Structured runtime (`AgentGraph`, `CoordinatorAgent`) |
| Manual orchestration | Graph-based execution |
| No durable state | Typed shared state + checkpoints |
| Retry logic in user code | Built-in retry + circuit breaker |
| No resume | Interrupt + resume support |
| Agents fully trusted | **Governed execution** — tools, state writes and cost are gated |

**Use it if** your agent needs multiple LLM calls, your workflow has branches or loops, failures matter, or multiple agents must coordinate.
**Skip it if** you just call `ChatClient` once.

---

## 🛡 Governed by default

Agents are **not implicitly trusted**. Gate what they can call, what they can change, what they can spend, and when a human must step in — without writing governance glue:

```java
// 1. restrict which tools an agent may call (gated on the executor)
ExecutorAgent paymentAgent = ExecutorAgent.builder()
    .chatClient(chatClient)
    .tools(webSearch, shellTool)
    .toolPolicy(ToolPolicy.allowList("web.search").and(ToolPolicy.denyList("shell.execute")))
    .build();

// state, cost and approval are gated on the graph
AgentGraph.builder()
    .addNode("assistant", assistant)
    .addNode("payment.transfer", paymentAgent)
    // 2. protect sensitive state keys from being written
    .statePolicy(StatePolicy.denyWriteKeys("payment.confirmed"))
    // 3. cap spend per run / node / call
    .budgetPolicy(BudgetPolicy.hierarchical(BudgetLimits.run(2.00), estimator, meter))
    // 4. pause for a human before high-stakes nodes
    .approvalGate(ApprovalGate.requireFor("payment.transfer"))
    .checkpointStore(store)
    .build();
```

Each gate is opt-in with a zero-overhead default. See [Tool policy](docs/tool-policy.md), [State policy](docs/state-policy.md), [Approval gate](docs/approval-gate.md), and [Budget policy](docs/resilience.md#6-budget-policy-cost-gate).

---

## 🧩 Two levels of control

- **Squad API** — dynamic routing, minimal setup. A `CoordinatorAgent` dispatches to `ExecutorAgent`s.
- **Graph API** — explicit flows, loops, conditions, full control.

Both are covered in the [docs](#-documentation).

---

## 🛠 Installation

**Requirements:** Java 17+, Spring Boot 3.x, Spring AI 1.0+.
Distributed via [JitPack](https://jitpack.io).

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.datallmhub.agentflow4j</groupId>
    <artifactId>agentflow4j-starter</artifactId>
    <version>v0.6.0</version>
</dependency>
```

### Gradle

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.datallmhub.agentflow4j:agentflow4j-starter:v0.6.0' }
```

### Modules

| Module | Purpose |
|---|---|
| `agentflow4j-starter` | Spring Boot auto-config, properties, Micrometer listener |
| `agentflow4j-core` | Minimal API (`Agent`, `AgentContext`, `StateKey`, `AgentResult`) |
| `agentflow4j-graph` | `AgentGraph`, `RetryPolicy`, `CircuitBreakerPolicy`, `BudgetPolicy`, checkpoint contract |
| `agentflow4j-squad` | `CoordinatorAgent`, `ExecutorAgent`, `ReActAgent`, `ParallelAgent` |
| `agentflow4j-checkpoint` | `JdbcCheckpointStore`, `RedisCheckpointStore`, Jackson codec |
| `agentflow4j-resilience4j` | `CircuitBreakerPolicy` adapter backed by Resilience4j |
| `agentflow4j-playground` | Drop-in web UI to chat with your `Agent` beans |
| `agentflow4j-cli-agents` | `CliAgentNode` — Claude Code / Codex / Gemini CLI as graph nodes |
| `agentflow4j-test` | `MockAgent`, `TestGraph` for LLM-free unit tests |

---

## 📚 Documentation

- [Two API levels (Squad + Graph)](docs/two-api-levels.md) — when to use which, with code
- [Typed state](docs/state.md) — `StateKey<T>` instead of `Map<String, Object>`
- [Tool policy](docs/tool-policy.md) — allow/deny tool calls per agent, with argument-aware rules
- [State policy](docs/state-policy.md) — allow/deny writes to specific `StateKey<T>`, with argument-aware rules
- [Approval gate](docs/approval-gate.md) — human-in-the-loop pause/resume on sensitive nodes
  - [Recipe: approval via Slack](docs/recipes/approval-via-slack.md) — async, non-blocking, ~30 lines
- [Resilience & error handling](docs/resilience.md) — retries, circuit breaker, budget policy
- [Observability](docs/observability.md) — Micrometer metrics, tags, listeners
- [Run log](docs/run-log.md) — structured, replayable execution timeline per run
- [Streaming](docs/streaming.md) — `Flux<AgentEvent>` tokens, transitions, tool calls
- [Testing without an LLM](docs/testing.md) — `MockAgent` + `TestGraph`
- [Samples](docs/samples.md) — runnable examples shipped with the repo

**Tutorial:** [Stop your AI agent from burning $1000 overnight](docs/tutorials/stop-your-agent-burning-money.md) — governed execution end to end.

---

## 📈 Roadmap

| Version | Status | Focus |
|---------|--------|-------|
| **0.5** | shipped | Subgraphs, parallel fan-out, cancellation, typed output, retry/circuit-breaker/budget policies, JDBC/Redis checkpoint store, web playground |
| **0.6** | shipped | Governed execution: `ToolPolicy`, `StatePolicy`, `ApprovalGate` (allow/deny tools, guard state writes, human-in-the-loop pause/resume) |
| **1.0** | planned | API stabilization, documentation, community feedback |
| **1.1** | planned | Crew roles (CrewAI-inspired), auto-config for checkpoint backends |
| **2.0** | exploring | OpenTelemetry tracing, MCP integration, Agent-as-Tool |

---

## 📝 Note on scope

It is not an official Spring project.

---

## 🤝 Contributing & License

Contributions welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
Released under the [Apache 2.0 License](LICENSE).
