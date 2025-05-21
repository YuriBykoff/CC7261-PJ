import { NextResponse } from "next/server"

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const { userId, notificationId } = body

    if (!userId || !notificationId) {
      return NextResponse.json(
        {
          error:
            "userId e notificationId são obrigatórios no corpo da requisição",
        },
        { status: 400 }
      )
    }

    const springApiUrl = `http://localhost/api/users/${userId}/notifications/mark-read`
    const springApiBody = JSON.stringify([notificationId])

    const springApiResponse = await fetch(springApiUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: springApiBody,
    })

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao marcar notificação como lida na API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (POST ${springApiUrl}) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }
    return NextResponse.json({ success: true }, { status: 200 })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para marcar notificação como lida:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      {
        error: "Erro interno ao processar a marcação de notificação como lida",
      },
      { status: 500 }
    )
  }
}
