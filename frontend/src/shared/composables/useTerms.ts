import { computed, reactive } from "vue";
import { get } from "../api";
import type { AcademicTerm } from "../types";

const state = reactive({
  terms: [] as AcademicTerm[],
  currentTerm: null as AcademicTerm | null,
  selectedId: null as number | null,
  loaded: false,
});

export function useTerms() {
  const selectedTerm = computed(
    () =>
      state.terms.find((item) => item.id === state.selectedId) ||
      state.currentTerm ||
      state.terms[0] ||
      null,
  );

  async function loadTerms(force = false) {
    if (state.loaded && !force) return;
    const payload = await get<{
      terms: AcademicTerm[];
      currentTerm: AcademicTerm | null;
    }>("/api/terms");
    state.terms = payload.terms || [];
    state.currentTerm = payload.currentTerm || null;
    if (
      !state.selectedId ||
      !state.terms.some((item) => item.id === state.selectedId)
    ) {
      state.selectedId = state.currentTerm?.id || state.terms[0]?.id || null;
    }
    state.loaded = true;
  }

  return { state, selectedTerm, loadTerms };
}
