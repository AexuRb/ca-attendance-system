let cancelPending: (() => void) | undefined;

export function cancelScrollRestoration() { cancelPending?.(); }

/** Wait for async lists to provide enough height, without moving the user's scroll later. */
export function restoreScrollPosition(position: { left: number; top: number }) {
  cancelScrollRestoration();
  return new Promise<{ left: number; top: number; behavior: "instant" } | false>((resolve) => {
    const started = performance.now();
    let frame = 0;
    const finish = (restore: boolean) => {
      cancelAnimationFrame(frame);
      window.removeEventListener("wheel", cancel);
      window.removeEventListener("pointerdown", cancel);
      window.removeEventListener("keydown", cancel);
      cancelPending = undefined;
      resolve(restore ? { ...position, behavior: "instant" } : false);
    };
    const cancel = () => finish(false);
    cancelPending = cancel;
    window.addEventListener("wheel", cancel, { passive: true, once: true });
    window.addEventListener("pointerdown", cancel, { once: true });
    window.addEventListener("keydown", cancel, { once: true });
    const check = () => {
      const enoughHeight = document.documentElement.scrollHeight - innerHeight >= position.top - 1;
      const loading = document.querySelector(".mw-main .loading-block");
      if ((enoughHeight && !loading) || performance.now() - started >= 1200) finish(true);
      else frame = requestAnimationFrame(check);
    };
    frame = requestAnimationFrame(check);
  });
}
