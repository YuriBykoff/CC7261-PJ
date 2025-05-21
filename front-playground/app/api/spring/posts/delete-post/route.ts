import { NextResponse } from "next/server"

export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url)
  const postId = searchParams.get("postId")

  if (!postId) {
    return NextResponse.json(
      { error: "postId é obrigatório como parâmetro de query" },
      { status: 400 }
    )
  }

  try {
    const body = await request.json()
    const { userId } = body

    if (!userId) {
      return NextResponse.json(
        {
          error:
            "userId é obrigatório no corpo da requisição para deletar o post",
        },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(
      `http://localhost/api/posts/${postId}`,
      {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ userId }),
      }
    )

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao deletar post na API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (DELETE /api/posts/${postId}) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    return NextResponse.json({ success: true }, { status: 200 })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para deletar post:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      { error: "Erro interno ao processar a deleção do post" },
      { status: 500 }
    )
  }
}
