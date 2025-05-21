"use client"

import React, { useState, useEffect, useCallback } from "react"

interface Notification {
  id: string
  message: string
  read: boolean
  createdAt: string

}

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Check, RefreshCw, Bell, Loader2 } from "lucide-react"
import { useUser } from "@/context/user-context"
import { ScrollArea } from "@/components/ui/scroll-area"
import { toast } from "sonner"

export function NotificationManager() {
  const { selectedUser } = useUser()
  const currentUserId = selectedUser?.id || null

  const [notifications, setNotifications] = useState<Notification[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isMarkingRead, setIsMarkingRead] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchNotifications = useCallback(async () => {
    if (!currentUserId) {
      setNotifications([])
      setIsLoading(false)
      setError(null)
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const response = await fetch(
        `/api/spring/notifications/get-notification?userId=${currentUserId}`
      )
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(
          errorData.error || "Falha ao buscar notificações (API Next)"
        )
      }
      const data = await response.json()
      const fetchedNotifications: Notification[] = data.content || data || []

      setNotifications(
        fetchedNotifications.sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
      )
    } catch (err) {
      const errorMsg =
        err instanceof Error
          ? err.message
          : "Erro desconhecido ao buscar notificações"
      setError(errorMsg)
      toast.error(errorMsg)
      setNotifications([])
    } finally {
      setIsLoading(false)
    }
  }, [currentUserId])

  useEffect(() => {
    fetchNotifications()
  }, [fetchNotifications])

  const handleMarkAsRead = async () => {
    if (!currentUserId || isMarkingRead) return
    const unreadNotifications = notifications.filter((n) => !n.read)
    const unreadIds = unreadNotifications.map((n) => n.id)

    if (unreadIds.length === 0) return

    setError(null)
    setIsMarkingRead(true)

    const results = await Promise.allSettled(
      unreadIds.map(async (id) => {
        const response = await fetch(
          "/api/spring/notifications/post-notification",
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId: currentUserId, notificationId: id }),
          }
        )
        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}))
          throw new Error(
            errorData.error ||
              `Falha ao marcar notificação ${id} como lida (API Next)`
          )
        }
        const data = await response.json()
        if (!data.success) {
          throw new Error(
            `API Next não retornou sucesso para notificação ${id}`
          )
        }
        return true
      })
    )

    let successCount = 0
    let failedIds: string[] = []
    let firstErrorMessage: string | null = null

    results.forEach((result, index) => {
      const notificationId = unreadIds[index]
      if (result.status === "fulfilled" && result.value === true) {
        successCount++
        setNotifications((prev) =>
          prev.map((n) => (n.id === notificationId ? { ...n, read: true } : n))
        )
      } else {
        failedIds.push(notificationId)
        if (!firstErrorMessage) {
          firstErrorMessage =
            result.status === "rejected"
              ? (result.reason as Error).message
              : "Falha desconhecida ao marcar (API Next)"
        }
        console.error(
          `Falha ao marcar notificação ${notificationId} como lida (via API Next):`,
          result.status === "rejected"
            ? result.reason
            : "API Next não retornou sucesso"
        )
      }
    })

    setIsMarkingRead(false)

    if (failedIds.length > 0) {
      const errorMsg = `Falha ao marcar ${failedIds.length} de ${
        unreadIds.length
      } notificações. ${
        firstErrorMessage ? `(Erro: ${firstErrorMessage})` : ""
      }`.trim()
      setError(errorMsg)
      toast.error(errorMsg)
    } else if (successCount > 0) {
      toast.success(
        `${successCount} ${
          successCount === 1 ? "notificação marcada" : "notificações marcadas"
        } como lida${successCount === 1 ? "" : "s"}.`
      )
    }
  }

  const unreadCount = notifications.filter((n) => !n.read).length

  if (!currentUserId) {
    return (
      <div className="border rounded-lg p-6 bg-card flex items-center justify-center h-[calc(100vh-10rem)]">
        <p className="text-muted-foreground">
          Selecione um usuário para ver as notificações.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-6 p-6 border rounded-lg bg-card shadow-sm">
      <div className="flex justify-between items-center gap-4">
        <div className="flex items-center gap-2">
          <Bell className="h-5 w-5 text-primary" />
          <h2 className="text-lg font-semibold text-card-foreground">
            Notificações ({notifications.length})
          </h2>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={fetchNotifications}
            size="sm"
            variant="ghost"
            title="Atualizar notificações"
            disabled={isLoading || isMarkingRead}
          >
            <RefreshCw
              className={`h-4 w-4 ${
                isLoading || isMarkingRead ? "animate-spin" : ""
              }`}
            />
          </Button>
          {unreadCount > 0 && (
            <Button
              onClick={handleMarkAsRead}
              size="sm"
              variant="outline"
              disabled={isLoading || isMarkingRead}
              className="transition-all min-w-[200px]"
            >
              {isMarkingRead ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Check className="mr-2 h-4 w-4" />
              )}
              Marcar todas lidas ({unreadCount})
            </Button>
          )}
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>Erro</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {isLoading ? (
        <div className="flex justify-center items-center h-[calc(100vh-24rem)] text-muted-foreground">
          <Loader2 className="h-8 w-8 animate-spin" />
        </div>
      ) : notifications.length === 0 && !error ? (
        <div className="flex flex-col items-center justify-center h-[calc(100vh-24rem)] text-muted-foreground rounded-lg border bg-muted/50">
          <Bell className="h-12 w-12 mb-4 opacity-20" />
          <p>Nenhuma notificação encontrada.</p>
        </div>
      ) : (
        <ScrollArea className="h-[calc(100vh-22rem)] rounded-lg border bg-muted/50">
          <ul className="p-4 space-y-3">
            {notifications.map((notification) => (
              <li
                key={notification.id}
                className={`p-3 rounded-md border transition-colors duration-200 ${
                  notification.read
                    ? "bg-background text-muted-foreground border-border/50"
                    : "bg-primary/10 font-medium text-primary border-primary/50 shadow-sm"
                }`}
              >
                <p className="text-sm">{notification.message}</p>
                <p className="text-xs text-muted-foreground/80 mt-1">
                  {new Date(notification.createdAt).toLocaleString("pt-BR", {
                    dateStyle: "short",
                    timeStyle: "short",
                  })}
                </p>
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}
    </div>
  )
}
