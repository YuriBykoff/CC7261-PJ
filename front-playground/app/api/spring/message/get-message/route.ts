// GET /api/users/{userId}/conversation/{otherUserId}

import { NextResponse } from "next/server"

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url)
  const userId = searchParams.get("userId")
  const otherUserId = searchParams.get("otherUserId")

  if (!userId || !otherUserId) {
    return NextResponse.json(
      {
        error: "userId e otherUserId são obrigatórios como parâmetros de query",
      },
      { status: 400 }
    )
  }

  try {
    const springApiResponse = await fetch(
      `http://localhost/api/users/${userId}/conversation/${otherUserId}`,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
        },
        cache: "no-store",
      }
    )

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao carregar conversa da API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (GET /api/users/${userId}/conversation/${otherUserId}) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    const springApiData = await springApiResponse.json()
    return NextResponse.json(springApiData, { status: 200 })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para buscar conversa:",
      error instanceof Error ? error.message : String(error)
    )
    return NextResponse.json(
      { error: "Erro interno ao processar a busca da conversa" },
      { status: 500 }
    )
  }
}
