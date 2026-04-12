# Portfolio Analysis — AI-Powered Indian Stock Market Advisor

A **Spring Boot + Spring AI + Ollama** application that analyses an Indian equity portfolio by:

1. Importing holdings from **Zerodha** (via the official KiteConnect SDK).
2. Enriching each holding with market metrics from the **NSE API** (sector, PE, 52-week high/low, sector PE).
3. Calculating portfolio-level risk metrics (concentration, diversification score, sector exposure).
4. Generating actionable, conservative investment advice using a **local Ollama LLM** with **Spring AI tool calling** so deterministic metrics stay separate from LLM reasoning (no data leaves your machine).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25 / Spring Boot 4 |
| AI | Spring AI 2.0 (Ollama) |
| LLM | Ollama — `qwen2.5:7b-instruct` (default, configurable) |
| Broker API | Zerodha KiteConnect SDK 3.4 |
| Market Data | NSE public API |
| Storage | PostgreSQL |
| API Docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |

---

## Prerequisites

1. **Java 25** (or later)
2. **Maven 3.9+**
3. **Ollama** running locally on port `11434`
   ```bash
   # Install from https://ollama.com, then pull the default model:
   ollama pull qwen2.5:7b-instruct
   ```
4. A **Zerodha developer account** — create an app at <https://developers.kite.trade/> to obtain your `api_key` and `api_secret`.
5. **PostgreSQL** running locally (or via Docker — see below).

---

## Configuration

All sensitive values are read from **environment variables**. Never commit credentials to source control.

| Environment Variable | Description | Required |
|---|---|---|
| `ZERODHA_API_KEY` | Zerodha API key | Yes |
| `ZERODHA_API_SECRET` | Zerodha API secret | Yes |
| `ZERODHA_AUTH_HEADER` | Pre-built auth token (`token <key>:<access_token>`). Optional — leave empty to use the OAuth callback flow. | No |
| `ZERODHA_REDIRECT_URI` | OAuth callback URI (default: `https://kite.trade/`) | No |
| `OLLAMA_BASE_URL` | Ollama base URL (default: `http://localhost:11434`) | No |
| `OLLAMA_MODEL` | Ollama model name (default: `qwen2.5:7b-instruct`) | No |
| `PORTFOLIO_ADVISOR_NUM_PREDICT` | Max generated tokens for the advisor response (default: `192`) | No |
| `PORTFOLIO_ADVISOR_KEEP_ALIVE` | Ollama model keep-alive window to reduce cold-start latency (default: `10m`) | No |
| `PORTFOLIO_ADVISOR_INTERNAL_TOOL_EXECUTION` | Enables Spring AI/Ollama internal tool execution for the advisor flow (default: `true`) | No |
| `PORTFOLIO_ADVISOR_THINKING_ENABLED` | Enables model thinking mode for the advisor flow. Keep `false` for lower latency. | No |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/portfolio`) | No |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username (default: `portfolio`) | No |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password (default: `portfolio`) | No |

### Setting environment variables

**Linux / macOS**
```bash
export ZERODHA_API_KEY=your_api_key
export ZERODHA_API_SECRET=your_api_secret
```

**Windows (PowerShell)**
```powershell
$env:ZERODHA_API_KEY="your_api_key"
$env:ZERODHA_API_SECRET="your_api_secret"
```

Alternatively, create a local `.env` file (excluded from git via `.gitignore`) and load it before running.

---

## Running the Application

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

### Local run with PostgreSQL + Ollama (Docker)

1. Install and start Docker Desktop.
2. Start PostgreSQL and Ollama:

```bash
docker compose --profile db --profile ai up -d
```

3. Run the app (PowerShell):

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
./mvnw spring-boot:run
```

4. Stop containers when done:

```bash
docker compose --profile db --profile ai down
```

The datasource already points to `jdbc:postgresql://localhost:5432/portfolio` with user/password `portfolio`.

---

## API Reference

Browse the full interactive API documentation at **http://localhost:8080/swagger-ui.html** once the application is running.

### Zerodha Authentication

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/zerodha/login-url` | Returns the Zerodha OAuth login URL |
| `GET` | `/api/zerodha/callback?request_token=...` | OAuth callback — exchanges token for session |
| `GET` | `/api/zerodha/session` | Checks if a session is active |

**Login URL response**
```json
{ "loginUrl": "https://kite.trade/connect/login?api_key=...&v=3" }
```

**OAuth flow** (first-time login):
1. `GET /api/zerodha/login-url` → open the URL in your browser.
2. Log in with Zerodha credentials.
3. Zerodha redirects to the callback URL; the app exchanges the `request_token` for a session.

**Callback response**
```json
{ "message": "Zerodha session created. You can now import holdings.", "tokenActive": true }
```

**Session status response**
```json
{ "authenticated": true, "hint": "Session is active in this JVM. ..." }
```

### Import Holdings from Zerodha

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/portfolios/holdings/import/zerodha` | Import holdings from the authenticated Zerodha account |

**Import response**
```json
{
  "portfolioUserId": "user-id",
  "importedHoldings": 12,
  "totalCurrentValue": 268500.00,
  "symbols": ["RELIANCE", "INFY", "TCS", "..."]
}
```

`importedHoldings` reflects the final persisted holdings count after duplicate instrument merges.

### Portfolio Management

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/portfolios` | Create a new portfolio for the authenticated user |
| `GET` | `/api/portfolios` | List all portfolios |
| `GET` | `/api/portfolios/me` | Get the authenticated user's portfolio |
| `PUT` | `/api/portfolios/holdings` | Add or update a holding |
| `DELETE` | `/api/portfolios/holdings/{symbol}` | Remove a holding by symbol |

**Add / update holding request body**
```json
{
  "symbol": "RELIANCE",
  "exchange": "NSE",
  "assetType": "EQUITY",
  "quantity": 10,
  "averageBuyPrice": 2450.00
}
```

### AI Analysis

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/portfolios/analysis` | Run AI-powered portfolio analysis |

**Analysis response**
```json
{
  "portfolioUserId": "user-id",
  "totalInvested": 245000.00,
  "totalCurrentValue": 268500.00,
  "totalProfitLoss": 23500.00,
  "aiInsights": {
    "risk_overview": "...",
    "diversification_feedback": "...",
    "suggestions": ["...", "...", "..."],
    "cautionary_note": "..."
  }
}
```

The analysis response includes:
- Per-holding performance (P&L, allocation %, risk flags)
- Portfolio-level metrics (diversification score, sector exposure, concentration flags)
- Tool-assisted LLM-generated advice: risk overview, diversification feedback, 3 actionable suggestions, cautionary note

The advisor flow keeps **metrics deterministic** and uses the LLM only for reasoning:
- `HoldingAnalyticsService` computes canonical holding-level valuation, momentum, and risk flags
- `PortfolioAnalyticsService` computes canonical portfolio concentration/diversification metrics
- `PortfolioReasoningTools` exposes those metrics plus flagged holdings through Spring AI tool calling
- `AiPortfolioAdvisorService` sends a compact routing prompt to Ollama, enables internal tool execution, and lets the model pull only the evidence it needs

---

## Typical Workflow

```
1. Login      : GET  /api/zerodha/login-url  → open URL in browser
2. Callback   : GET  /api/zerodha/callback?request_token=...  (handled automatically by browser redirect)
3. Import     : POST /api/portfolios/holdings/import/zerodha
4. Analyse    : GET  /api/portfolios/analysis
```

---

## Risk Flags

Fixed risk flag names are centralized in `com.cursor_springa_ai.playground.model.enums.RiskFlag`.

### Holding-level

| Flag | Trigger |
|---|---|
| `HIGH_CONCENTRATION` | Holding > 20 % of portfolio |
| `HIGH_VALUATION` | Stock PE > 1.5 × sector PE |
| `DEEP_CORRECTION` | Price > 25 % below 52-week high |
| `SMALL_CAP_RISK` | Market cap type = SMALL |
| `PROFIT_BOOKING_ZONE` | Unrealised profit > 40 % |

### Portfolio-level

| Flag | Trigger |
|---|---|
| `HIGH_CONCENTRATION` | Top holding > 25 % of portfolio |
| `TOP_HEAVY_PORTFOLIO` | Top 3 holdings > 60 % of portfolio |
| `SECTOR_CONCENTRATION_<SECTOR>` | Any sector > 40 % of portfolio |
| `UNDER_DIVERSIFIED` | Fewer than 5 holdings |

---

## Project Structure

```
src/main/java/.../
├── controller/          REST endpoints (Portfolio, ZerodhaAuth)
├── exception/           Custom exceptions (NotAuthenticatedException, ZerodhaClientException)
├── ai/
│   ├── advisor/         Spring AI agents and prompt builders
│   ├── dto/             AI-only snapshot/trace/tool DTOs
│   ├── persistence/     AI result persistence (AiAnalysisService)
│   ├── reasoning/       Spring AI tool implementations + PortfolioReasoningContext
│   ├── service/         AI orchestration services (analysis, chat, context factory)
│   └── tools/           Data builder helpers (snapshot, trace, hints, flagged holdings, overview)
├── analytics/
│   ├── model/           Internal analytics read models (PortfolioSummary, EnrichedHoldingData)
│   ├── PortfolioDerivedMetricsService  Derived metric computations (top holdings, sector/cap exposure)
│   └── ...              Holding and portfolio analytics services
├── importer/            Zerodha import pipeline orchestration + calculators
├── service/             Core business services (auth, enrichment, snapshots, persistence helpers)
├── integration/
│   ├── zerodha/         KiteConnect SDK wrappers
│   └── market/          NSE API client + wire DTOs
├── model/
│   ├── entity/          JPA entities
│   └── enums/           Shared domain enums (RiskFlag, AnalysisType, ...)
├── repository/          Spring Data JPA repositories
├── dto/                 API request/response DTOs (plus dto/zerodha)
└── config/              Spring beans (RestTemplate, OpenAPI)
```

## Package Notes

- AI DTOs moved from `dto.ai` to `ai.dto`.
- `PortfolioSummary` and `EnrichedHoldingData` moved to `analytics.model` (internal read models, not API DTOs).
- `AnalysisType` and `RiskFlag` moved to `model.enums`.
- Import pipeline no longer uses a `PreparedHolding` intermediate; preparation returns `UserHolding` directly.
- `PortfolioReasoningTools`, `PortfolioChatReasoningTools`, and `ToolInvocationRecorder` moved from `ai.tools` to `ai.reasoning` — reasoning tool implementations live alongside `PortfolioReasoningContext`.
- `PortfolioDerivedMetricsService` moved from `ai.tools` to `analytics` — derived metric computations belong in the analytics layer, not the AI tool layer.
- `PortfolioChatService` no longer injects `AiAnalysisRepository` directly; it uses `AiAnalysisService.findLatestPortfolioAnalysis` and `AiAnalysisService.findRecentChatHistory` to respect service-layer boundaries.

### Migration Map (Old -> New)

| Old path | New path |
|---|---|
| `com.cursor_springa_ai.playground.dto.ai.*` | `com.cursor_springa_ai.playground.ai.dto.*` |
| `com.cursor_springa_ai.playground.dto.PortfolioSummary` | `com.cursor_springa_ai.playground.analytics.model.PortfolioSummary` |
| `com.cursor_springa_ai.playground.dto.EnrichedHoldingData` | `com.cursor_springa_ai.playground.analytics.model.EnrichedHoldingData` |
| `com.cursor_springa_ai.playground.model.AnalysisType` | `com.cursor_springa_ai.playground.model.enums.AnalysisType` |
| `com.cursor_springa_ai.playground.model.RiskFlag` | `com.cursor_springa_ai.playground.model.enums.RiskFlag` |
| `PreparedHolding` wrapper record | Removed; importer flow now prepares `UserHolding` directly |
| `com.cursor_springa_ai.playground.ai.tools.PortfolioReasoningTools` | `com.cursor_springa_ai.playground.ai.reasoning.PortfolioReasoningTools` |
| `com.cursor_springa_ai.playground.ai.tools.PortfolioChatReasoningTools` | `com.cursor_springa_ai.playground.ai.reasoning.PortfolioChatReasoningTools` |
| `com.cursor_springa_ai.playground.ai.tools.ToolInvocationRecorder` | `com.cursor_springa_ai.playground.ai.reasoning.ToolInvocationRecorder` |
| `com.cursor_springa_ai.playground.ai.tools.PortfolioDerivedMetricsService` | `com.cursor_springa_ai.playground.analytics.PortfolioDerivedMetricsService` |

---

## Known Limitations

- **Market cap type** — the NSE public API does not return SEBI market-cap classification (LARGE / MID / SMALL) directly. The `SMALL_CAP_RISK` flag relies on this field and will not trigger until a proper classification source is integrated.
- **NSE API rate limits** — the NSE website may block or throttle rapid requests. Consider adding delays or caching TTLs between imports.
- **Zerodha session** — access tokens expire daily; the app must go through the OAuth flow each day. The `ZERODHA_AUTH_HEADER` env var can skip the flow during development, but it must be refreshed manually.

---

## Contributing

Pull requests are welcome. Please open an issue first to discuss significant changes.
