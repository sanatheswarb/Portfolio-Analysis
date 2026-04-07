import { useMemo, useState } from 'react'
import './App.css'
import {
  checkSessionStatus,
  importHoldingsFromZerodha,
  requestPortfolioAnalysis,
  sendChatMessage,
  type ApiError,
} from './services/api'

type MessageRole = 'assistant' | 'user' | 'system'

interface ChatMessage {
  id: string
  role: MessageRole
  text: string
  createdAt: number
}

function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      text: 'Hi! I can answer follow-up questions about your portfolio analysis. Start by checking session, importing holdings, and running analysis.',
      createdAt: Date.now(),
    },
  ])
  const [draft, setDraft] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [isCheckingSession, setIsCheckingSession] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [sessionHint, setSessionHint] = useState('Session status not checked yet.')
  const [analysisReady, setAnalysisReady] = useState(false)

  const canSend = useMemo(
    () => !isSending && draft.trim().length > 0,
    [draft, isSending],
  )

  const addMessage = (message: Omit<ChatMessage, 'id' | 'createdAt'>) => {
    setMessages((prev) => {
      const next = [
        ...prev,
        {
          ...message,
          id: crypto.randomUUID(),
          createdAt: Date.now(),
        },
      ]
      return next.length > 100 ? next.slice(next.length - 100) : next
    })
  }

  const formatApiError = (error: ApiError) =>
    `${error.message} (${error.status ? `HTTP ${error.status}` : 'Network error'})`

  const handleCheckSession = async () => {
    setIsCheckingSession(true)
    try {
      const session = await checkSessionStatus()
      setSessionHint(session.hint)
      addMessage({
        role: 'system',
        text: session.authenticated
          ? 'Session is active. You can import holdings.'
          : 'No active session. Complete Zerodha login first.',
      })
    } catch (error) {
      const apiError = error as ApiError
      addMessage({
        role: 'system',
        text: `Session check failed: ${formatApiError(apiError)}`,
      })
    } finally {
      setIsCheckingSession(false)
    }
  }

  const handleImport = async () => {
    setIsImporting(true)
    try {
      const response = await importHoldingsFromZerodha()
      addMessage({
        role: 'system',
        text: `Imported ${response.importedHoldings} holdings from Zerodha.`,
      })
    } catch (error) {
      const apiError = error as ApiError
      addMessage({
        role: 'system',
        text: `Import failed: ${formatApiError(apiError)}`,
      })
    } finally {
      setIsImporting(false)
    }
  }

  const handleAnalyze = async () => {
    setIsAnalyzing(true)
    try {
      await requestPortfolioAnalysis()
      setAnalysisReady(true)
      addMessage({
        role: 'system',
        text: 'Portfolio analysis completed. You can now ask follow-up questions.',
      })
    } catch (error) {
      const apiError = error as ApiError
      addMessage({
        role: 'system',
        text: `Analysis failed: ${formatApiError(apiError)}`,
      })
    } finally {
      setIsAnalyzing(false)
    }
  }

  const handleSend = async () => {
    const question = draft.trim()
    if (!question || isSending) {
      return
    }

    setDraft('')
    setIsSending(true)
    addMessage({ role: 'user', text: question })

    try {
      const response = await sendChatMessage(question)
      setAnalysisReady(true)
      addMessage({ role: 'assistant', text: response.answer })
    } catch (error) {
      const apiError = error as ApiError
      if (apiError.status === 409) {
        setAnalysisReady(false)
      }
      addMessage({
        role: 'assistant',
        text: `I could not answer that right now. ${formatApiError(apiError)}`,
      })
    } finally {
      setIsSending(false)
    }
  }

  return (
    <main className="chat-page">
      <header className="header">
        <div>
          <p className="eyebrow">Portfolio Assistant</p>
          <h1>Conversational Portfolio Chat</h1>
          <p className="subtitle">
            Ask focused questions after session setup, holdings import, and analysis.
          </p>
        </div>
        <span className={`chip ${analysisReady ? 'chip--ok' : ''}`}>
          {analysisReady ? 'Analysis Ready' : 'Analysis Required'}
        </span>
      </header>

      <section className="setup-actions">
        <button onClick={handleCheckSession} disabled={isCheckingSession}>
          {isCheckingSession ? 'Checking Session…' : 'Check Session'}
        </button>
        <button onClick={handleImport} disabled={isImporting}>
          {isImporting ? 'Importing…' : 'Import Holdings'}
        </button>
        <button onClick={handleAnalyze} disabled={isAnalyzing}>
          {isAnalyzing ? 'Analyzing…' : 'Run Analysis'}
        </button>
      </section>

      <section className="hint-panel">
        <strong>Session Hint:</strong>
        <p>{sessionHint}</p>
      </section>

      <section className="conversation" aria-live="polite">
        {messages.map((message) => (
          <article key={message.id} className={`message message--${message.role}`}>
            <div className="message-meta">{message.role}</div>
            <p>{message.text}</p>
          </article>
        ))}
        {isSending && (
          <article className="message message--assistant">
            <div className="message-meta">assistant</div>
            <p>Thinking…</p>
          </article>
        )}
      </section>

      <section className="composer">
        <label htmlFor="chat-input" className="sr-only">
          Ask a question
        </label>
        <textarea
          id="chat-input"
          value={draft}
          placeholder="Ask about risk, concentration, diversification, or a holding…"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              void handleSend()
            }
          }}
          disabled={isSending}
          rows={3}
        />
        <div className="composer-actions">
          <p>Press Enter to send. Shift+Enter for new line.</p>
          <button onClick={handleSend} disabled={!canSend}>
            {isSending ? 'Sending…' : 'Send'}
          </button>
        </div>
      </section>
    </main>
  )
}

export default App
