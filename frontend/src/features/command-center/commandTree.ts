import type { Role } from "../../shared/types";
import {
  commandActions,
  commandObjects,
  commandShortcuts,
  commandTreePaths,
} from "./commandCatalog";
import { resolveCommand, suggestCommands } from "./commandParser";
import type {
  CommandInputState,
  CommandInputView,
  CommandNodeKind,
  CommandNodeSuggestion,
  CommandSuggestion,
} from "./commandTypes";

interface TokenRange {
  value: string;
  start: number;
  end: number;
}

interface CursorContext {
  activeIndex: number;
  activeRange?: TokenRange;
  before: string[];
  partial: string;
}

export function commandInputView(
  input: string,
  role: Role,
  caret = input.length,
  limit = 9,
): CommandInputView {
  if (!input.trimStart().startsWith("/")) {
    return searchView(input, role, limit);
  }

  const context = cursorContext(input, caret);
  const allowed = commandTreePaths.filter((item) => item.roles.includes(role));
  const paths = allowed.map((item) => ({ item, tokens: commandTokens(item.command) }));
  if (context.activeIndex === 0 && !context.before.length && context.partial) {
    paths.push(...commandShortcuts
      .filter((item) => item.roles.includes(role))
      .map((item) => ({
        item: {
          id: item.id,
          command: "/ " + item.keyword,
          description: item.description,
          roles: item.roles,
          execution: item.execution,
          keywords: [item.keyword],
        },
        tokens: [item.keyword],
      })));
  }
  const before = normalizeAcceptedTokens(context.before);
  const matchingPaths = paths.filter(({ tokens }) =>
    before.every((token, index) => tokens[index] === token),
  );
  const kind = nodeKind(context.activeIndex);
  const suggestions = uniqueNodes(
    matchingPaths
      .filter(({ tokens }) => tokens.length > context.activeIndex)
      .filter(({ tokens }) => tokenMatches(tokens[context.activeIndex] || "", context.partial, kind))
      .map(({ item, tokens }) => nodeFromPath(item, tokens, context.activeIndex, kind)),
  ).slice(0, limit);
  const resolution = resolveCommand(input, role);
  const accepted = normalizeAcceptedTokens(commandTokens(input));
  const hasDescendants = paths.some(({ tokens }) =>
    accepted.every((token, index) => tokens[index] === token) && tokens.length > accepted.length,
  );
  const state = commandState(resolution.kind, suggestions.length, hasDescendants);
  const rootShortcut = context.activeIndex === 0
    && Boolean(context.partial)
    && suggestions.some((item) => !commandActions.some((action) => action === item.label));

  return {
    mode: "command",
    state,
    statusMessage: statusMessage(state, resolution.kind === "resolved" ? undefined : resolution.message),
    path: commandPath(before, context.partial, kind, rootShortcut),
    prompt: promptFor(kind, suggestions.length, context.activeIndex === 0 && Boolean(context.partial)),
    suggestions,
  };
}

export function completeCommandInput(
  input: string,
  suggestion: CommandNodeSuggestion,
  caret = input.length,
): { value: string; caret: number } {
  if (suggestion.kind === "search") {
    return { value: suggestion.completion, caret: suggestion.completion.length };
  }

  const context = cursorContext(input, caret);
  const start = context.activeRange?.start ?? caret;
  const end = context.activeRange?.end ?? caret;
  const suffix = suggestion.appendSpace ? " " : "";
  const slash = input.indexOf("/");
  const prefix = suggestion.kind === "action" && slash >= 0
    ? input.slice(0, slash + 1) + " "
    : input.slice(0, start);
  const value = prefix + suggestion.completion + suffix + input.slice(end);
  return { value, caret: prefix.length + suggestion.completion.length + suffix.length };
}

function searchView(input: string, role: Role, limit: number): CommandInputView {
  const suggestions = suggestCommands(input, role, limit).map((item) => ({
    id: item.id,
    label: readableCommand(item.command),
    description: item.description,
    kind: "search" as const,
    completion: item.command,
    appendSpace: false,
    execution: item.execution,
  }));
  return {
    mode: "search",
    state: "search",
    statusMessage: "查找功能",
    path: [],
    prompt: input.trim() ? "匹配功能" : "常用功能",
    suggestions,
  };
}

function cursorContext(input: string, caret: number): CursorContext {
  const safeCaret = Math.max(0, Math.min(caret, input.length));
  const slash = input.indexOf("/");
  const ranges: TokenRange[] = [];
  const expression = /\S+/g;
  expression.lastIndex = Math.max(0, slash + 1);
  let match = expression.exec(input);
  while (match) {
    ranges.push({ value: match[0], start: match.index, end: match.index + match[0].length });
    match = expression.exec(input);
  }

  const containing = ranges.findIndex((range) => safeCaret >= range.start && safeCaret <= range.end);
  if (containing >= 0) {
    const range = ranges[containing]!;
    return {
      activeIndex: containing,
      activeRange: range,
      before: ranges.slice(0, containing).map((item) => item.value),
      partial: range.value.slice(0, Math.max(0, safeCaret - range.start)),
    };
  }

  const beforeRanges = ranges.filter((range) => range.end < safeCaret);
  return {
    activeIndex: beforeRanges.length,
    before: beforeRanges.map((item) => item.value),
    partial: "",
  };
}

function normalizeAcceptedTokens(tokens: string[]): string[] {
  return tokens.map((token, index) => {
    if (index !== 1) return token;
    const object = commandObjects.find((item) =>
      item.canonical === token || item.aliases.includes(token),
    );
    return object?.canonical || token;
  });
}

function tokenMatches(candidate: string, partial: string, kind: CommandNodeKind): boolean {
  if (!partial) return true;
  if (candidate.startsWith(partial) || candidate.includes(partial)) return true;
  if (kind !== "object") return false;
  const object = commandObjects.find((item) => item.canonical === candidate);
  return Boolean(object?.aliases.some((alias) => alias.includes(partial)));
}

function nodeFromPath(
  item: CommandSuggestion,
  tokens: string[],
  index: number,
  kind: CommandNodeKind,
): CommandNodeSuggestion {
  const needsManualParameter = tokens.length === index + 1 && ["查找", "预览"].includes(tokens[0] || "");
  const shortcut = index === 0
    && tokens.length === 1
    && !commandActions.some((action) => action === tokens[0]);
  return {
    id: item.id + "-" + index,
    label: tokens[index] || "",
    description: shortcut ? item.description : descriptionFor(kind, item),
    kind,
    completion: tokens[index] || "",
    appendSpace: tokens.length > index + 1 || needsManualParameter,
    execution: tokens.length === index + 1 && !needsManualParameter ? item.execution : undefined,
  };
}

function uniqueNodes(items: CommandNodeSuggestion[]): CommandNodeSuggestion[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.label)) return false;
    seen.add(item.label);
    return true;
  });
}

function commandPath(
  before: string[],
  partial: string,
  kind: CommandNodeKind,
  rootShortcut: boolean,
): string[] {
  if (kind === "action") return [rootShortcut ? "快捷命令" : "选择动作"];
  const path = [...before];
  if (partial) path.push(partial);
  if (!partial) path.push(kind === "object" ? "选择功能" : "选择参数");
  return path;
}

function nodeKind(index: number): CommandNodeKind {
  if (index === 0) return "action";
  if (index === 1) return "object";
  return "parameter";
}

function promptFor(kind: CommandNodeKind, count: number, includesShortcut = false): string {
  if (!count) return "没有可补全参数";
  if (kind === "action") return includesShortcut ? "选择命令或快捷词" : "选择命令动作";
  if (kind === "object") return "选择目标功能";
  return "选择命令参数";
}

function descriptionFor(kind: CommandNodeKind, item: CommandSuggestion): string {
  if (kind === "action") return "选择命令动作";
  if (kind === "object") return item.description;
  return item.description;
}

function commandState(
  resolution: "resolved" | "invalid" | "forbidden" | "dangerous",
  suggestionCount: number,
  hasDescendants: boolean,
): CommandInputState {
  if (resolution === "resolved") return hasDescendants ? "extensible" : "executable";
  return suggestionCount ? "incomplete" : "invalid";
}

function statusMessage(state: CommandInputState, failure?: string): string {
  if (state === "search") return "查找功能";
  if (state === "incomplete") return "继续补充命令";
  if (state === "executable") return "可以执行";
  if (state === "extensible") return "可以执行，也可继续补充范围";
  return failure || "命令需要检查";
}

function commandTokens(command: string): string[] {
  return command.replace(/^\/\s*/, "").trim().split(/\s+/).filter(Boolean);
}

function readableCommand(command: string): string {
  return command.replace(/^\/\s*/, "");
}
