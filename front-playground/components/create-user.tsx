"use client"

import { useState } from "react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { toast } from "sonner"
import { Loader2 } from "lucide-react"

interface CreateUserFormProps {
  onSuccess?: () => void // Callback para sucesso
}

export function CreateUserForm({ onSuccess }: CreateUserFormProps) {
  const [name, setName] = useState("")
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsLoading(true)

    try {
      const response = await fetch("/api/spring/users", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ name }),
      })

      const data = await response.json()

      if (!response.ok) {
        throw new Error(data.error || "Falha ao criar usuário")
      }

      toast.success(`Usuário "${data.name}" criado com sucesso!`)
      setName("")
      onSuccess?.() // Chamar callback de sucesso
    } catch (error) {
      console.error("Erro no formulário:", error)
      toast.error(
        error instanceof Error ? error.message : "Ocorreu um erro desconhecido"
      )
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      id="create-user-dialog-form"
      className="space-y-4"
    >
      <div className="space-y-2">
        <Label
          htmlFor="create-user-name"
          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
        >
          Nome do Usuário
        </Label>
        <div className="relative">
          <Input
            id="create-user-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Digite o nome completo"
            required
            disabled={isLoading}
            className="w-full focus-visible:ring-1 focus-visible:ring-primary"
          />
          {isLoading && (
            <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />
          )}
        </div>
      </div>
    </form>
  )
}
