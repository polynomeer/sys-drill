"use client";

import { useEffect, useState } from "react";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

type HealthStatus = "checking" | "up" | "down";

export default function Home() {
  const [status, setStatus] = useState<HealthStatus>("checking");
  const [detail, setDetail] = useState<string>("");

  useEffect(() => {
    fetch(`${API_BASE_URL}/actuator/health`)
      .then(async (res) => {
        const body = await res.json();
        setStatus(res.ok && body.status === "UP" ? "up" : "down");
        setDetail(JSON.stringify(body));
      })
      .catch((err) => {
        setStatus("down");
        setDetail(String(err));
      });
  }, []);

  const statusColor =
    status === "up"
      ? "text-green-600 dark:text-green-400"
      : status === "down"
        ? "text-red-600 dark:text-red-400"
        : "text-zinc-500";

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-zinc-50 p-8 font-sans dark:bg-black">
      <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
        SysDrill
      </h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        Backend health:{" "}
        <span className={`font-mono font-medium ${statusColor}`}>
          {status}
        </span>
      </p>
      {detail && (
        <pre className="max-w-md overflow-x-auto rounded bg-black/[.04] p-3 text-xs text-zinc-700 dark:bg-white/[.06] dark:text-zinc-300">
          {detail}
        </pre>
      )}
    </div>
  );
}
