"use client";

import { useEffect } from "react";
import * as Sentry from "@sentry/nextjs";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

/**
 * docs/COMMERCIALIZATION.md — route-segment error boundary (renders inside
 * the existing root layout, so AppHeader/Tailwind/fonts are already there).
 * Next 16 (this repo's pinned version, node_modules/next/dist/docs/) names
 * the retry callback `retry`, not the classic `reset`.
 */
export default function ErrorPage({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
    Sentry.captureException(error);
  }, [error]);

  return (
    <div className="mx-auto flex min-h-[60vh] max-w-md flex-col justify-center gap-4 p-8">
      <Card className="flex flex-col gap-4">
        <Alert variant="danger">문제가 발생했어요. 페이지를 표시하는 중 오류가 났습니다.</Alert>
        <div className="flex gap-2">
          <Button onClick={() => retry()}>다시 시도</Button>
          <Button href="/dashboard" variant="secondary">
            홈으로
          </Button>
        </div>
      </Card>
    </div>
  );
}
