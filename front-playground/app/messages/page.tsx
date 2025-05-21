"use client"

import { MessageManager } from "@/components/message-manager"

export default function Messages() {
  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-6">
      <div className="w-full max-w-5xl">
        <MessageManager />
      </div>
    </div>
  )
}
