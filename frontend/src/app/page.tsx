"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";

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
    status === "up" ? "text-success" : status === "down" ? "text-danger" : "text-foreground-muted";

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-8 font-sans">
      <h1 className="text-2xl font-semibold text-foreground">
        SysDrill
      </h1>
      <p className="text-foreground-muted">
        Backend health:{" "}
        <span className={`font-mono font-medium ${statusColor}`}>
          {status}
        </span>
      </p>
      {detail && (
        <pre className="max-w-md overflow-x-auto rounded-lg border border-border bg-surface p-3 text-xs text-foreground-muted">
          {detail}
        </pre>
      )}
      <Button href="/dashboard">훈련 시작하기</Button>
    </div>
  );
}
