import type { Extension } from "@codemirror/state";
import { oneDark } from "@codemirror/theme-one-dark";
import { dracula } from "@uiw/codemirror-theme-dracula";
import { githubDark, githubLight } from "@uiw/codemirror-theme-github";
import { monokai } from "@uiw/codemirror-theme-monokai";
import { nord } from "@uiw/codemirror-theme-nord";
import { tokyoNight } from "@uiw/codemirror-theme-tokyo-night";

/** Build-mode code editor color themes — a per-viewer display preference, persisted via `saveEditorTheme`. */
export const EDITOR_THEMES: { id: string; label: string; extension: Extension }[] = [
  { id: "one-dark", label: "One Dark", extension: oneDark },
  { id: "dracula", label: "Dracula", extension: dracula },
  { id: "tokyo-night", label: "Tokyo Night", extension: tokyoNight },
  { id: "nord", label: "Nord", extension: nord },
  { id: "monokai", label: "Monokai", extension: monokai },
  { id: "github-dark", label: "GitHub Dark", extension: githubDark },
  { id: "github-light", label: "GitHub Light", extension: githubLight },
];

export function editorThemeById(id: string | null) {
  return EDITOR_THEMES.find((t) => t.id === id) ?? EDITOR_THEMES[0];
}
