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
| Storage | In-memory (`ConcurrentHashMap`) |

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

### Zerodha Authentication

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/zerodha/login-url` | Returns the Zerodha OAuth login URL |
| `GET` | `/api/zerodha/callback?request_token=...` | OAuth callback — exchanges token for session |
| `GET` | `/api/zerodha/session` | Checks if a session is active |

**OAuth flow** (first-time login):
1. `GET /api/zerodha/login-url` → open the URL in your browser.
2. Log in with Zerodha credentials.
3. Zerodha redirects to the callback URL; the app exchanges the `request_token` for a session.

### Portfolio Management

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/portfolios` | Create a portfolio `{"ownerName":"..."}` |
| `GET` | `/api/portfolios` | List all portfolios |
| `GET` | `/api/portfolios/{id}` | Get a specific portfolio |
| `PUT` | `/api/portfolios/{id}/holdings` | Add or update a holding |
| `DELETE` | `/api/portfolios/{id}/holdings/{symbol}` | Remove a holding |

### Import from Zerodha

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/portfolios/{id}/holdings/import/zerodha` | Import into an existing portfolio |
| `POST` | `/api/portfolios/holdings/import/zerodha` | Import + auto-create portfolio `{"ownerName":"..."}` |

### AI Analysis

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/portfolios/{id}/analysis` | Run AI-powered portfolio analysis |

The analysis response includes:
- Per-holding performance (P&L, allocation %, risk flags)
- Portfolio-level metrics (diversification score, sector exposure, concentration flags)
- Tool-assisted LLM-generated advice: risk overview, diversification feedback, 3 actionable suggestions, cautionary note

The advisor flow keeps **metrics deterministic** and uses the LLM only for reasoning:
- `PortfolioMetricsService` computes portfolio concentration/diversification metrics
- `PortfolioReasoningTools` exposes those metrics plus flagged holdings through Spring AI tool calling
- `AiPortfolioAdvisorService` sends a compact routing prompt to Ollama, enables internal tool execution, and lets the model pull only the evidence it needs

---

## Typical Workflow

```
1. Authenticate  : GET /api/zerodha/login-url  → login in browser
2. Import        : POST /api/portfolios/holdings/import/zerodha  {"ownerName":"Alice"}
3. Analyse       : GET /api/portfolios/{id}/analysis
```

---

## Risk Flags

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
├── service/             Business logic (analysis, metrics, AI advisor, import)
├── integration/
│   ├── zerodha/         KiteConnect SDK wrapper + DTOs
│   └── market/          NSE API client + DTOs
├── model/               Domain models (Portfolio, Holding, AssetType)
├── dto/                 Request/response DTOs
└── config/              Spring beans (RestTemplate)
```

---

## Known Limitations

- **In-memory storage** — all data is lost on restart. A future version should add a database (PostgreSQL / H2).
- **Market cap type** — the NSE public API does not return SEBI market-cap classification (LARGE / MID / SMALL) directly. The `SMALL_CAP_RISK` flag relies on this field and will not trigger until a proper classification source is integrated.
- **NSE API rate limits** — the NSE website may block or throttle rapid requests. Consider adding delays or caching TTLs between imports.
- **Zerodha session** — access tokens expire daily; the app must go through the OAuth flow each day. The `ZERODHA_AUTH_HEADER` env var can skip the flow during development, but it must be refreshed manually.

---

## Contributing

Pull requests are welcome. Please open an issue first to discuss significant changes.
