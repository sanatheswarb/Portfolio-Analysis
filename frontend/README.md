# Frontend — Conversational Portfolio Chat UI

React + TypeScript + Vite frontend for the backend chat API.

## Run

```bash
cd frontend
npm install
npm run dev
```

By default, the Vite dev server proxies `/api/*` requests to `http://localhost:8080`.

## Build

```bash
cd frontend
npm run build
```

## Architecture Overview

- `src/App.tsx` — feature composition + state orchestration for setup actions and chat flow
- `src/services/api.ts` — typed API client, response models, and centralized error parsing
- `src/App.css` / `src/index.css` — responsive UI layout and visual design tokens

## Data Flow

1. User triggers setup actions (`session`, `import`, `analysis`) from top controls.
2. UI calls typed API service methods and appends system feedback to conversation.
3. User sends a question; UI immediately appends user message.
4. UI posts to `/api/portfolios/chat`; assistant response is appended to chat timeline.
5. API errors are normalized and rendered as assistant/system messages.
