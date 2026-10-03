import type { Metadata } from "next";
import { Do_Hyeon, Geist_Mono, Gowun_Batang, JetBrains_Mono, Noto_Sans_KR, Orbitron, Share_Tech_Mono, Silkscreen } from "next/font/google";
import { AppHeader } from "@/components/AppHeader";
import { AppFooter } from "@/components/AppFooter";
import { ToastProvider } from "@/components/ui/Toast";
import { APP_THEME_BOOT_SCRIPT } from "@/lib/appTheme";
import "./globals.css";

const notoSansKr = Noto_Sans_KR({
  variable: "--font-noto-sans-kr",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// docs/UX_STRATEGY.md "앱 테마" — theme-only faces aren't preloaded; a browser
// downloads one only once a theme that uses it is picked.
const doHyeon = Do_Hyeon({ variable: "--font-do-hyeon", weight: "400", preload: false });
const silkscreen = Silkscreen({ variable: "--font-silkscreen", weight: ["400", "700"], preload: false });
const gowunBatang = Gowun_Batang({ variable: "--font-gowun-batang", weight: ["400", "700"], preload: false });
const orbitron = Orbitron({ variable: "--font-orbitron", preload: false });
const shareTechMono = Share_Tech_Mono({ variable: "--font-share-tech-mono", weight: "400", preload: false });
const jetbrainsMono = JetBrains_Mono({ variable: "--font-jetbrains-mono", preload: false });
const themeFonts = [doHyeon, silkscreen, gowunBatang, orbitron, shareTechMono, jetbrainsMono].map((f) => f.variable).join(" ");

export const metadata: Metadata = {
  title: "SysDrill",
  description: "설계 → 꼬리설계 → 장애 대응까지, 반복 가능한 시스템 설계 훈련 플랫폼",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  // suppressHydrationWarning: the boot script sets <html data-theme> before hydration.
  return (
    <html
      lang="ko"
      className={`${notoSansKr.variable} ${geistMono.variable} ${themeFonts} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: APP_THEME_BOOT_SCRIPT }} />
      </head>
      <body className="min-h-full flex flex-col">
        <ToastProvider>
          <AppHeader />
          {children}
          <AppFooter />
        </ToastProvider>
      </body>
    </html>
  );
}
