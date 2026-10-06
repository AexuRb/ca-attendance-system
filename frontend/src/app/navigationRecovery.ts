import { ref, watch, type InjectionKey } from "vue";
import type { RouteLocationNormalized, Router } from "vue-router";
import { useSession } from "./session";

// A failed navigation is transient intent, never persisted in history or storage.
export function createNavigationRecovery(router: Router, reloadDocument = reloadAt) {
  const failure = ref<{ target: string; hasCurrentPage: boolean } | null>(null);
  const pending = ref(false);
  const session = useSession();
  let latestTarget: RouteLocationNormalized | null = null;

  function dismiss() { failure.value = null; }
  const stopSessionWatch = watch(() => {
    const user = session.state.user;
    return user ? `${user.id}:${user.role}` : null;
  }, () => {
    latestTarget = null;
    dismiss();
  }, { flush: "sync" });
  const removeBefore = router.beforeEach(to => {
    latestTarget = to;
    if (failure.value?.target !== to.fullPath) dismiss();
  });
  const removeError = router.onError((_error, to) => {
    if (to === latestTarget) {
      failure.value = { target: to.fullPath, hasCurrentPage: router.currentRoute.value.matched.length > 0 };
    }
  });
  const removeAfter = router.afterEach((to, _from, navigationFailure) => {
    if (to === latestTarget && !navigationFailure) dismiss();
  });

  async function retry() {
    if (!failure.value || pending.value) return;
    const target = failure.value.target;
    pending.value = true;
    // Close first so an existing unsaved-changes guard can own its confirmation.
    dismiss();
    try { await router.push(target); }
    catch { /* onError presents a recoverable failure without internal details. */ }
    finally { pending.value = false; }
  }

  function reload() {
    if (!failure.value || pending.value) return;
    const target = router.resolve(failure.value.target);
    const query = { ...target.query };
    // These search values normally disappear once their page mounts. A failed
    // chunk never mounts it, so remove them before creating a fresh document.
    delete query.keyword;
    delete query.participantKeyword;
    delete query.intent;
    const href = router.resolve({ path: target.path, query, hash: target.hash }).href;
    reloadDocument(href);
  }

  return { failure, pending, dismiss, retry, reload, dispose() {
    stopSessionWatch(); removeBefore(); removeError(); removeAfter(); dismiss();
  } };
}

export function reloadAt(href: string, location: Pick<Location, "href" | "replace"> = window.location) {
  const target = new URL(href, location.href);
  // A hash-only replacement would reuse the failed module cache. Changing only
  // the document query requests a fresh document without pre-mutating history;
  // beforeunload can then cancel while the current URL and workspace still agree.
  target.searchParams.set("_reload", crypto.randomUUID());
  location.replace(target.href);
}

export type NavigationRecovery = ReturnType<typeof createNavigationRecovery>;
export const navigationRecoveryKey: InjectionKey<NavigationRecovery> = Symbol("navigationRecovery");
