"use client"

import { UserFollowManager } from "@/components/user-follow-manager"

export default function Home() {
  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center p-6">
      <div className="w-full max-w-5xl">
        <UserFollowManager />
      </div>
    </div>
  )
}
