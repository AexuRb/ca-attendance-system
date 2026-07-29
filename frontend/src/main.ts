import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./app/router";
import { useSession } from "./app/session";
import { setUnauthorizedHandler } from "./shared/api";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/service-status.css";
import "./styles/components.css";
import "./styles/layouts.css";
import "./styles/kiosk.css";
import "./styles/admin-shell.css";
import "./styles/today.css";
import "./styles/schedule.css";
import "./styles/admin-details.css";
import "./styles/kiosk-theme.css";
import "./styles/admin-theme.css";
import "./styles/auth.css";

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
