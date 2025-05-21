// DELETE /api/follows/{followerId}/unfollow/{followedId}

import { NextResponse } from "next/server"

export async function DELETE(request: Request) {
  try {
    const body = await request.json()
    const { actionType, currentUserId, targetUserId, followerId } = body

    if (!actionType) {
      return NextResponse.json(
        {
          error:
            "actionType é obrigatório no corpo da requisição (ex: 'unfollow' ou 'removeFollower')",
        },
        { status: 400 }
      )
    }

    let springApiUrl = ""
    let errorMessageContext = ""

    if (actionType === "unfollow") {
      if (!currentUserId || !targetUserId) {
        return NextResponse.json(
          {
            error:
              "Para actionType 'unfollow', currentUserId e targetUserId são obrigatórios",
          },
          { status: 400 }
        )
      }
      springApiUrl = `http://localhost/api/follows/${currentUserId}/unfollow/${targetUserId}`
      errorMessageContext = `DELETE /api/follows/${currentUserId}/unfollow/${targetUserId}`
    } else if (actionType === "removeFollower") {
      if (!followerId || !currentUserId) {
        return NextResponse.json(
          {
            error:
              "Para actionType 'removeFollower', followerId e currentUserId são obrigatórios",
          },
          { status: 400 }
        )
      }
      springApiUrl = `http://localhost/api/follows/${followerId}/unfollow/${currentUserId}`
      errorMessageContext = `DELETE /api/follows/${followerId}/unfollow/${currentUserId}`
    } else {
      return NextResponse.json(
        { error: "actionType inválido" },
        { status: 400 }
      )
    }

    const springApiResponse = await fetch(springApiUrl, {
      method: "DELETE",
    })

    if (!springApiResponse.ok) {
      let errorMsg = `Falha ao processar ${actionType} na API externa`
      try {
        const errorData = await springApiResponse.json()
        errorMsg = errorData.message || errorData.error || errorMsg
      } catch (e) {
      }
      console.error(
        `API Spring (${errorMessageContext}) respondeu com status ${springApiResponse.status}: ${errorMsg}`
      )
      return NextResponse.json(
        { error: errorMsg },
        { status: springApiResponse.status }
      )
    }

    return NextResponse.json({ success: true }, { status: 200 })
  } catch (error) {
    console.error(
      "Erro ao fazer proxy para remover follow:",
      error instanceof Error ? error.message : String(error)
    )
    if (error instanceof SyntaxError && error.message.includes("JSON")) {
      return NextResponse.json(
        { error: "Corpo da requisição JSON inválido" },
        { status: 400 }
      )
    }
    return NextResponse.json(
      { error: "Erro interno ao processar a remoção de follow" },
      { status: 500 }
    )
  }
}
