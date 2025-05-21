// POST /api/follows/{followerId}/follow/{followedId}

import { NextResponse } from "next/server"

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const { currentUserId, targetUserId } = body

    if (!currentUserId || !targetUserId) {
      return NextResponse.json(
        {
          error:
            "currentUserId e targetUserId são obrigatórios no corpo da requisição",
        },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(
      `http://localhost/api/follows/${currentUserId}/follow/${targetUserId}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
      }
    )

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao seguir usuário na API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (POST /api/follows/${currentUserId}/follow/${targetUserId}) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    return NextResponse.json({ success: true }, { status: 200 })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para seguir usuário:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      { error: "Erro interno ao processar a ação de seguir usuário" },
      { status: 500 }
    )
  }
}
