import { NextResponse } from "next/server"

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const { userId, content } = body

    if (!userId || !content) {
      return NextResponse.json(
        { error: "userId e content são obrigatórios no corpo da requisição" },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(`http://localhost/api/posts`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ userId, content }),
    })

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao criar post na API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (POST /api/posts) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    const responseText = await springApiResponse.text()
    if (responseText.length === 0 || springApiResponse.status === 204) {
      return NextResponse.json(
        {
          success: true,
          message:
            "Post criado com sucesso (sem corpo de resposta da API externa)",
        },
        { status: springApiResponse.status }
      )
    }
    try {
      const springApiData = JSON.parse(responseText)
      return NextResponse.json(springApiData, {
        status: springApiResponse.status,
      }) // Geralmente 201
    } catch (parseError) {
      console.warn(
        "API Spring (POST /api/posts) respondeu com corpo não-JSON, mas status OK."
      )
      return NextResponse.json(
        {
          success: true,
          message: "Post criado, mas resposta da API externa não era JSON.",
        },
        { status: springApiResponse.status }
      )
    }
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para criar post:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      { error: "Erro interno ao processar a criação do post" },
      { status: 500 }
    )
  }
}
