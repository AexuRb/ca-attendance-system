import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./app/router";
import { initializeAppearance } from "./appearance/appearanceStore";
import { useSession } from "./app/session";
import { setUnauthorizedHandler } from "./shared/api";

async function start() {
  await initializeAppearance();
  setUnauthorizedHandler(() => {
    const session = useSession();
    session.expireSession();
    const current = router.currentRoute.value;
    if (current.name === "login") return;
    const query: Record<string, string> = { reason: "expired" };
    if (current.meta.auth) query.next = current.fullPath;
    void router.replace({ name: "login", query });
  });

  createApp(App).use(router).mount("#app");
}

void start();
