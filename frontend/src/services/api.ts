const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

interface ErrorBody {
  message?: string
}

export interface ApiError extends Error {
  status?: number
}

export interface ChatResponse {
  answer: string
  chatId: number
  analysisId: number
}

export interface SessionStatusResponse {
  authenticated: boolean
  hint: string
}

export interface ZerodhaImportResponse {
  portfolioId: string
  importedHoldings: number
  symbols: string[]
}

const parseApiError = async (response: Response): Promise<ApiError> => {
  let body: ErrorBody = {}
  try {
    body = (await response.json()) as ErrorBody
  } catch {
    body = {}
  }
  const error = new Error(
    body.message || `${response.status} ${response.statusText}`,
  ) as ApiError
  error.status = response.status
  return error
}

const request = async <T>(
  path: string,
  options: RequestInit = {},
): Promise<T> => {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    })
  } catch {
    const error = new Error('Unable to reach API') as ApiError
    error.status = undefined
    throw error
  }

  if (!response.ok) {
    throw await parseApiError(response)
  }

  return (await response.json()) as T
}

export const checkSessionStatus = () =>
  request<SessionStatusResponse>('/api/zerodha/session', {
    method: 'GET',
  })

export const importHoldingsFromZerodha = () =>
  request<ZerodhaImportResponse>('/api/portfolios/holdings/import/zerodha', {
    method: 'POST',
  })

export const requestPortfolioAnalysis = () =>
  request<Record<string, unknown>>('/api/portfolios/analysis', {
    method: 'GET',
  })

export const sendChatMessage = (question: string) =>
  request<ChatResponse>('/api/portfolios/chat', {
    method: 'POST',
    body: JSON.stringify({ question }),
  })
