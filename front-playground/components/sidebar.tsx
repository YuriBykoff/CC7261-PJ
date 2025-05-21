"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { MessageSquare, UserPlus, Database, Bell } from "lucide-react"
import { UserSelect } from "./user-select"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { CreateUserForm } from "./create-user"
import { useState } from "react"

const platformNavigation = [
  { name: "Follow Manager", href: "/", icon: UserPlus },
  { name: "Posts Manager", href: "/posts", icon: Database },
  {
    name: "Notifications Manager",
    href: "/notifications",
    icon: Bell,
  },
  { name: "Messages Manager", href: "/messages", icon: MessageSquare },
]

export function Sidebar() {
  const pathname = usePathname()
  const [isDialogOpen, setIsDialogOpen] = useState(false)

  return (
    <aside className="fixed top-0 left-0 z-20 h-full w-64 bg-background border-r border-border/40 flex flex-col shadow-lg">
      <div className="flex-1 overflow-y-auto py-5 px-4 space-y-8">
        <div className="space-y-3">
          <div className="flex items-center justify-between px-2 mb-1.5">
            <h3 className="text-xs font-medium text-muted-foreground">
              USUÁRIO ATIVO
            </h3>
          </div>
          <UserSelect />

          <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
            <DialogTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                className="w-full mt-2 justify-start group hover:border-primary/50 transition-all duration-200"
              >
                <UserPlus className="mr-2 h-4 w-4 group-hover:text-primary transition-colors" />
                <span className="group-hover:translate-x-0.5 transition-transform duration-200">
                  Criar Novo Usuário
                </span>
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Criar Novo Usuário</DialogTitle>
                <DialogDescription>
                  Preencha o nome do novo usuário abaixo.
                </DialogDescription>
              </DialogHeader>
              <div className="py-4">
                <CreateUserForm onSuccess={() => setIsDialogOpen(false)} />
              </div>
              <DialogFooter>
                <Button type="submit" form="create-user-dialog-form">
                  Salvar Usuário
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        <div className="space-y-3">
          <div className="flex items-center px-2 mb-1">
            <Database
              className="h-3.5 w-3.5 mr-1.5 text-muted-foreground"
              strokeWidth={2.5}
            />
            <h3 className="text-xs font-medium text-muted-foreground">
              API SPRINGBOOT
            </h3>
          </div>
          <nav className="space-y-1">
            {platformNavigation.map((item) => (
              <Link
                key={item.name}
                href={item.href}
                className={cn(
                  "group flex items-center justify-between py-2 px-3 text-sm rounded-md transition-all duration-200",
                  pathname === item.href
                    ? "bg-primary/10 text-primary font-medium"
                    : "text-foreground/70 hover:bg-primary/5 hover:text-foreground"
                )}
              >
                <div className="flex items-center">
                  <item.icon
                    className={cn(
                      "mr-3 h-4 w-4 transition-transform duration-200",
                      pathname === item.href
                        ? "text-primary"
                        : "text-muted-foreground group-hover:text-primary group-hover:scale-110"
                    )}
                  />
                  <span
                    className={
                      pathname === item.href
                        ? "text-primary"
                        : "group-hover:translate-x-0.5 transition-transform duration-200"
                    }
                  >
                    {item.name}
                  </span>
                </div>
                {pathname === item.href && (
                  <div className="h-1.5 w-1.5 rounded-full bg-primary"></div>
                )}
              </Link>
            ))}
          </nav>
        </div>
      </div>
    </aside>
  )
}
