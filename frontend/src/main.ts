import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./app/router";
import { initializeAppearance } from "./appearance/appearanceStore";
import { useSession } from "./app/session";
import { setUnauthorizedHandler } from "./shared/api";
import { createNavigationRecovery, navigationRecoveryKey } from "./app/navigationRecovery";

async function start() {
  await initializeAppearance();
  setUnauthorizedHandler(() => {
    const session = useSession();
    session.expireSession();
    // Startup owns its original destination until entry/session checks finish.
    if (!session.state.ready) return;
    const current = router.currentRoute.value;
    if (current.name === "login") return;
    const query: Record<string, string> = { reason: "expired" };
    if (current.meta.auth) query.next = current.fullPath;
    void router.replace({ name: "login", query });
  });

  const recovery = createNavigationRecovery(router);
  createApp(App).provide(navigationRecoveryKey, recovery).use(router).mount("#app");
}

void start();
