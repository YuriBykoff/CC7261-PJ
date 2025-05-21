import { NextResponse } from "next/server"

export async function GET(request: Request) {
  try {
    const springApiResponse = await fetch(`http://localhost/api/posts`, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    })

    if (!springApiResponse.ok) {
      let errorMsg = "Falha ao carregar feed geral da API externa"
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (GET /api/posts) respondeu com status ${springApiResponse.status}: ${errorMsg}`
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
      "Erro ao fazer proxy para buscar todos os posts:",
      error instanceof Error ? error.message : String(error)
    )
    return NextResponse.json(
      { error: "Erro interno ao processar a busca de todos os posts" },
      { status: 500 }
    )
  }
}
