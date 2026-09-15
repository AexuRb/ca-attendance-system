/** Animate only the work surface: navigation stays still and input is never locked. */
export function workspaceEntrance(target: HTMLElement, appearance: string, reduced: boolean): () => void {
  if (reduced || typeof target.animate !== "function") return () => {};
  const duration = appearance === "editorial" ? 240 : appearance === "spatial" ? 220 : 160;
  const distance = appearance === "classic" ? 2 : 4;
  const animation = target.animate([
    { opacity: 0.82, transform: `translateY(${distance}px)` },
    { opacity: 1, transform: "translateY(0)" },
  ], { duration, easing: "cubic-bezier(.22,.68,.2,1)", fill: "none" });
  return () => animation.cancel();
}
