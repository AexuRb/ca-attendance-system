import type { InjectionKey } from "vue";

// A migrated workspace can reuse dialog behavior without loading its old visual shell.
export const memberPresentationKey: InjectionKey<boolean> = Symbol("member-presentation");
