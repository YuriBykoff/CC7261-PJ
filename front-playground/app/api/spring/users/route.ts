import { NextResponse, NextRequest } from "next/server"

export async function GET() {
  try {
    const springApiResponse = await fetch(`http://localhost/api/users`, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    })

    if (springApiResponse.ok) {
      try {
        const springApiData = await springApiResponse.json()
        return NextResponse.json(springApiData, { status: 200 })
      } catch (parseError) {
        console.error("Erro ao parsear JSON da API Spring (GET):", parseError)
        return NextResponse.json(
          { error: "Resposta da API externa inválida" },
          { status: 500 }
        )
      }
    } else {
      console.error(
        `API Spring (GET /users) respondeu com status ${springApiResponse.status}`
      )
      return NextResponse.json(
        { error: "Falha ao buscar usuários no serviço externo" },
        { status: 500 }
      )
    }
  } catch (error) {
    console.error("Erro geral ao buscar usuários (proxy):", error)
    return NextResponse.json(
      { error: "Erro interno ao processar a busca de usuários" },
      { status: 500 }
    )
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const name = body.name

    if (!name || typeof name !== "string") {
      return NextResponse.json(
        { error: "Nome é obrigatório e deve ser uma string" },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(`http://localhost/api/users`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ name }),
    })

    if (springApiResponse.status === 201) {
      try {
        const springApiData = await springApiResponse.json()
        return NextResponse.json(springApiData, { status: 201 })
      } catch (parseError) {
        console.error("Erro ao parsear JSON da API Spring (201):", parseError)
        return NextResponse.json(
          { error: "Resposta de sucesso da API inválida" },
          { status: 500 }
        )
      }
    } else {
      console.error(
        `API Spring respondeu com status ${springApiResponse.status}`
      )
      return NextResponse.json(
        { error: "Falha ao criar usuário no serviço externo" },
        { status: 500 }
      )
    }
  } catch (error) {
    console.error("Erro geral ao criar usuário (proxy):", error)
    return NextResponse.json(
      { error: "Erro interno ao processar a criação do usuário" },
      { status: 500 }
    )
  }
}
