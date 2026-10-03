import type { Extension } from "@codemirror/state";
import { oneDark } from "@codemirror/theme-one-dark";
import { tags as t } from "@lezer/highlight";
import { dracula } from "@uiw/codemirror-theme-dracula";
import { githubDark, githubLight } from "@uiw/codemirror-theme-github";
import { monokai } from "@uiw/codemirror-theme-monokai";
import { nord } from "@uiw/codemirror-theme-nord";
import { tokyoNight } from "@uiw/codemirror-theme-tokyo-night";
import { createTheme } from "@uiw/codemirror-themes";
import type { AppThemeId } from "@/lib/appTheme";

export type EditorTheme = { id: string; label: string; extension: Extension };

type Palette = {
  background: string;
  foreground: string;
  caret: string;
  selection: string;
  lineHighlight: string;
  gutter: string;
  keyword: string;
  string: string;
  number: string;
  comment: string;
  fn: string;
  type: string;
  property: string;
};

/** One editor theme per app theme, drawn from the same palette as its `[data-theme]` tokens in globals.css. */
function appEditorTheme(palette: Palette): Extension {
  return createTheme({
    theme: "dark",
    settings: {
      background: palette.background,
      foreground: palette.foreground,
      caret: palette.caret,
      selection: palette.selection,
      selectionMatch: palette.selection,
      lineHighlight: palette.lineHighlight,
      gutterBackground: palette.background,
      gutterForeground: palette.gutter,
      gutterActiveForeground: palette.foreground,
      gutterBorder: "transparent",
      // Each app theme sets its own mono face (JetBrains Mono, Share Tech Mono, ...).
      fontFamily: "var(--app-font-mono), ui-monospace, monospace",
    },
    styles: [
      { tag: t.comment, color: palette.comment, fontStyle: "italic" },
      { tag: [t.keyword, t.controlKeyword, t.moduleKeyword, t.definitionKeyword, t.modifier], color: palette.keyword },
      { tag: [t.string, t.special(t.string), t.regexp], color: palette.string },
      { tag: [t.number, t.bool, t.null, t.atom], color: palette.number },
      { tag: [t.function(t.variableName), t.function(t.propertyName)], color: palette.fn },
      { tag: [t.typeName, t.className, t.namespace], color: palette.type },
      { tag: [t.propertyName, t.attributeName], color: palette.property },
      { tag: [t.operator, t.punctuation, t.bracket], color: palette.gutter },
      { tag: t.self, color: palette.keyword, fontStyle: "italic" },
    ],
  });
}

export const APP_EDITOR_THEMES: Record<AppThemeId, EditorTheme> = {
  navy: {
    id: "app-navy",
    label: "SysDrill 기본",
    extension: appEditorTheme({
      background: "#0b1626",
      foreground: "#e2e8f0",
      caret: "#2f80ff",
      selection: "#2f80ff40",
      lineHighlight: "#13223880",
      gutter: "#4b5d78",
      keyword: "#5ea2ff",
      string: "#34d399",
      number: "#f59e0b",
      comment: "#5b6b84",
      fn: "#7dd3fc",
      type: "#c4b5fd",
      property: "#93c5fd",
    }),
  },
  quest: {
    id: "app-quest",
    label: "Quest",
    extension: appEditorTheme({
      background: "#1a1540",
      foreground: "#f4f2ff",
      caret: "#ffc24b",
      selection: "#6e4bff55",
      lineHighlight: "#2a245780",
      gutter: "#6b63a8",
      keyword: "#a58bff",
      string: "#3df5b0",
      number: "#ffc24b",
      comment: "#7f78b8",
      fn: "#ff8fd8",
      type: "#7ad7ff",
      property: "#c9bfff",
    }),
  },
  arcade: {
    id: "app-arcade",
    label: "Arcade",
    extension: appEditorTheme({
      background: "#0b0b1a",
      foreground: "#ffffff",
      caret: "#ffe600",
      selection: "#33e1ff40",
      lineHighlight: "#1e1e4a80",
      gutter: "#55557a",
      keyword: "#ff3ea5",
      string: "#39ff88",
      number: "#ffe600",
      comment: "#6a6a9a",
      fn: "#33e1ff",
      type: "#b388ff",
      property: "#8ff3ff",
    }),
  },
  rpg: {
    id: "app-rpg",
    label: "RPG",
    extension: appEditorTheme({
      background: "#0d1430",
      foreground: "#f2f0fa",
      caret: "#f5c451",
      selection: "#f5c45133",
      lineHighlight: "#1e2a5580",
      gutter: "#5a5f8a",
      keyword: "#f5c451",
      string: "#5be37d",
      number: "#ff9f43",
      comment: "#7c76a0",
      fn: "#8fb8ff",
      type: "#e0a3ff",
      property: "#d8d4ee",
    }),
  },
  tactical: {
    id: "app-tactical",
    label: "Tactical",
    extension: appEditorTheme({
      background: "#061a1e",
      foreground: "#d8f3f1",
      caret: "#3cf0e0",
      selection: "#3cf0e033",
      lineHighlight: "#0f2c3380",
      gutter: "#3d6b6a",
      keyword: "#3cf0e0",
      string: "#9dff5c",
      number: "#ffb020",
      comment: "#4f7f7c",
      fn: "#7ff7ee",
      type: "#b4e6ff",
      property: "#a7d8d5",
    }),
  },
};

/** Build-mode code editor color themes beyond the app-matched ones — a per-viewer display preference. */
export const EXTRA_EDITOR_THEMES: EditorTheme[] = [
  { id: "one-dark", label: "One Dark", extension: oneDark },
  { id: "dracula", label: "Dracula", extension: dracula },
  { id: "tokyo-night", label: "Tokyo Night", extension: tokyoNight },
  { id: "nord", label: "Nord", extension: nord },
  { id: "monokai", label: "Monokai", extension: monokai },
  { id: "github-dark", label: "GitHub Dark", extension: githubDark },
  { id: "github-light", label: "GitHub Light", extension: githubLight },
];

/** Select value meaning "whatever matches the current app theme". */
export const AUTO_EDITOR_THEME = "auto";

/** `id` is a picked theme id or `AUTO_EDITOR_THEME`; unknown ids fall back to the app theme's editor. */
export function resolveEditorTheme(id: string, appTheme: AppThemeId): EditorTheme {
  return (
    Object.values(APP_EDITOR_THEMES).find((theme) => theme.id === id) ??
    EXTRA_EDITOR_THEMES.find((theme) => theme.id === id) ??
    APP_EDITOR_THEMES[appTheme]
  );
}
