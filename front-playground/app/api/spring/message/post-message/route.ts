// POST /api/messages
// Body: { senderId: string, receiverId: string, content: string }

import { NextResponse } from "next/server"

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const { senderId, receiverId, content } = body

    if (!senderId || !receiverId || !content) {
      return NextResponse.json(
        {
          error:
            "senderId, receiverId e content são obrigatórios no corpo da requisição",
        },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(`http://localhost/api/messages`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ senderId, receiverId, content }),
    })

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao enviar mensagem para a API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (POST /api/messages) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    const springApiData = await springApiResponse.json()
    return NextResponse.json(springApiData, {
      status: springApiResponse.status,
    })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para enviar mensagem:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      { error: "Erro interno ao processar o envio da mensagem" },
      { status: 500 }
    )
  }
}
