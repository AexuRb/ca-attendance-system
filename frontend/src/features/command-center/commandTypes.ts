import type { RouteLocationRaw } from "vue-router";
import type { Role } from "../../shared/types";

export type CommandExecution = "navigate" | "prefill" | "confirm";

export interface CommandSuggestion {
  id: string;
  command: string;
  description: string;
  execution: CommandExecution;
  roles: Role[];
  keywords: string[];
}

export interface ResolvedCommand {
  kind: "resolved";
  canonical: string;
  feedback: string;
  execution: CommandExecution;
  target: RouteLocationRaw;
}

export interface CommandFailure {
  kind: "invalid" | "forbidden" | "dangerous";
  message: string;
}

export type CommandResolution = ResolvedCommand | CommandFailure;

export type CommandNodeKind = "action" | "object" | "parameter" | "search";
export type CommandInputState = "search" | "incomplete" | "executable" | "extensible" | "invalid";

export interface CommandNodeSuggestion {
  id: string;
  label: string;
  description: string;
  kind: CommandNodeKind;
  completion: string;
  appendSpace: boolean;
  execution?: CommandExecution;
}

export interface CommandInputView {
  mode: "search" | "command";
  state: CommandInputState;
  statusMessage: string;
  path: string[];
  prompt: string;
  suggestions: CommandNodeSuggestion[];
}
