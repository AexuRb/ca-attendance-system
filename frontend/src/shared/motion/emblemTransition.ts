export type EmblemKind = "large" | "small";
type Pose = { x: number; y: number; width: number; height: number };
type Emblem = { element: HTMLImageElement; kind: EmblemKind; pose: Pose; time: number };
type Flight = { image: HTMLImageElement; source: Emblem; target?: Emblem; deadline?: number; animations: Animation[]; timer: ReturnType<typeof setTimeout> };

/** Only the public emblem is copied. Page DOM, form values and private content never enter the overlay. */
export function createEmblemTransition(root: HTMLElement) {
  const doc = root.ownerDocument;
  const view = doc.defaultView!;
  const preference = view.matchMedia?.("(prefers-reduced-motion: reduce)");
  let current: Emblem | undefined;
  let flight: Flight | undefined;
  let disposed = false;
  const supported = typeof root.animate === "function";
  const pose = (element: Element): Pose => {
    const rect = element.getBoundingClientRect();
    return { x: rect.left, y: rect.top, width: rect.width, height: rect.height };
  };
  function visibleEmblem(): Emblem | undefined {
    for (const element of root.querySelectorAll<HTMLImageElement>("img[data-shared-emblem]")) {
      const rect = pose(element);
      const css = view.getComputedStyle(element);
      if (rect.width < 8 || rect.height < 8 || css.display === "none" || css.visibility === "hidden"
          || rect.x + rect.width <= 0 || rect.y + rect.height <= 0 || rect.x >= view.innerWidth || rect.y >= view.innerHeight) continue;
      return { element, kind: element.dataset.sharedEmblem as EmblemKind, pose: rect, time: performance.now() };
    }
  }
  function cancel() {
    const previous = flight;
    flight = undefined;
    if (!previous) return;
    clearTimeout(previous.timer);
    previous.animations.forEach(animation => animation.cancel());
    previous.source.element.removeAttribute("data-emblem-suppressed");
    previous.target?.element.removeAttribute("data-emblem-suppressed");
    previous.image.remove();
    doc.documentElement.removeAttribute("data-emblem-transition");
  }
  function hold(source: Emblem) {
    if (!supported || preference?.matches || doc.visibilityState === "hidden") return;
    const image = doc.createElement("img");
    image.className = "emblem-transition-image";
    image.alt = "";
    image.setAttribute("aria-hidden", "true");
    image.setAttribute("role", "presentation");
    image.draggable = false;
    image.src = source.element.currentSrc || source.element.src;
    image.style.width = `${source.pose.width}px`;
    image.style.height = `${source.pose.height}px`;
    image.style.transform = `translate3d(${source.pose.x}px, ${source.pose.y}px, 0)`;
    doc.body.append(image);
    source.element.setAttribute("data-emblem-suppressed", "true");
    flight = { image, source, animations: [], timer: setTimeout(cancel, 1800) };
    doc.documentElement.setAttribute("data-emblem-transition", "pending");
  }
  function enterParts(next: Emblem, active: Flight, duration: number) {
    const page = next.element.closest(".overview-shell, .public-kiosk, .public-auth, .setup-page");
    const parts = page?.querySelectorAll<HTMLElement>(next.kind === "small"
      ? ".overview-sidebar, .overview-content, .overview-brand>strong, .overview-topbar-actions, .kiosk-focus-brand>div, .kiosk-focus-header-actions, .public-kiosk-shell>section"
      : ".auth-form, .setup-form, .auth-header-actions, .auth-entry-brand>strong, .auth-entry-brand>span, .password-brand>strong, .password-brand>span, .setup-brand>strong, .setup-brand>span");
    parts?.forEach(part => {
      if (part.contains(next.element)) return;
      const entrance = part.animate([{ opacity: 0, transform: "translateY(6px)" }, { opacity: 1, transform: "none" }], {
        duration: Math.max(120, duration - 180), delay: Math.min(100, duration / 4), easing: "cubic-bezier(.22,.72,.16,1)", fill: "backwards",
      });
      entrance.id = "emblem-content";
      active.animations.push(entrance);
    });
  }
  function move(next: Emblem, remaining?: number) {
    const active = flight!;
    active.target = next;
    next.element.setAttribute("data-emblem-suppressed", "true");
    doc.documentElement.setAttribute("data-emblem-transition", "moving");
    // Read after normalizing legacy route transforms, so the landing position is the final layout position.
    next.pose = pose(next.element);
    const from = active.source.pose;
    const to = next.pose;
    // Rasterize the SVG at the larger endpoint so enlarging the compact emblem stays sharp.
    const width = Math.max(from.width, to.width);
    const height = Math.max(from.height, to.height);
    active.image.style.width = `${width}px`;
    active.image.style.height = `${height}px`;
    // Production CSS may shorten 640ms to .64s; WAAPI always expects milliseconds.
    const durationToken = view.getComputedStyle(doc.documentElement).getPropertyValue("--ui-duration-emblem").trim();
    const configuredDuration = /^\d*\.?\d+(ms|s)$/.test(durationToken)
      ? Number.parseFloat(durationToken) * (durationToken.endsWith("ms") ? 1 : 1000) : NaN;
    const duration = remaining ?? (Number.isFinite(configuredDuration) && configuredDuration > 0 ? configuredDuration : 640);
    active.deadline = performance.now() + duration;
    const transform = (x: number, y: number, sx: number, sy: number) => `translate3d(${x}px, ${y}px, 0) scale(${sx}, ${sy})`;
    active.image.style.transform = transform(from.x, from.y, from.width / width, from.height / height);
    try {
      const animation = active.image.animate([
        { transform: transform(from.x, from.y, from.width / width, from.height / height) },
        { transform: transform(to.x, to.y, to.width / width, to.height / height) },
      ], { duration, easing: "cubic-bezier(.22,.72,.16,1)", fill: "forwards" });
      animation.id = "emblem-flight";
      active.animations.push(animation);
      enterParts(next, active, duration);
      clearTimeout(active.timer);
      active.timer = setTimeout(() => { if (flight === active) cancel(); }, duration + 100);
      void animation.finished.then(() => { if (flight === active) cancel(); }, () => { if (flight === active) cancel(); });
    } catch { cancel(); }
  }
  function refresh() {
    if (disposed) return;
    const next = visibleEmblem();
    if (!next) return;
    if (flight?.target?.element === next.element) return;
    if (flight?.source.element === next.element) return;
    if (flight?.target?.kind === next.kind) {
      const active = flight;
      const destination = active.target!;
      const remaining = Math.max(120, (active.deadline || performance.now()) - performance.now());
      destination.element.removeAttribute("data-emblem-suppressed");
      if (["x", "y", "width", "height"].every(key => Math.abs(next.pose[key as keyof Pose] - destination.pose[key as keyof Pose]) < 1)) {
        // Logout's safe login fallback may be replaced by the actual login route during the same flight.
        next.element.setAttribute("data-emblem-suppressed", "true");
        active.target = next;
        current = next;
        try { enterParts(next, active, remaining); } catch { cancel(); }
      } else {
        const source = { ...active.source, pose: pose(active.image), time: performance.now() };
        cancel(); hold(source); current = next;
        if (flight) move(next, remaining);
      }
      return;
    }
    const previous = current;
    if (flight?.target) cancel();
    if (!flight && previous && next.element !== previous.element && next.kind !== previous.kind && performance.now() - previous.time < 1800) hold(previous);
    current = next;
    if (!flight) return;
    if (flight.source.kind === next.kind) { cancel(); return; }
    move(next);
  }
  function prepare(nextKind?: EmblemKind) {
    // Query synchronization must not restart an already-arriving emblem.
    if (flight?.target?.kind === nextKind || (flight && !flight.target && nextKind && flight.source.kind !== nextKind)) return;
    const next = visibleEmblem();
    const previous = flight && next ? { ...next, pose: pose(flight.image) } : next || current;
    cancel();
    if (!previous || performance.now() - previous.time >= 1800) return;
    current = previous;
    if (nextKind && nextKind !== previous.kind) hold(previous);
  }
  function reset() { cancel(); current = visibleEmblem(); }
  function onScroll() {
    if (flight?.target) {
      const actual = pose(flight.target.element);
      const expected = flight.target.pose;
      if (Math.abs(actual.x - expected.x) > 1 || Math.abs(actual.y - expected.y) > 1) reset();
    } else if (!flight) current = visibleEmblem();
  }
  const observer = new MutationObserver(refresh);
  observer.observe(root, { childList: true, subtree: true });
  root.addEventListener("load", refresh, true);
  doc.addEventListener("scroll", onScroll, true);
  doc.addEventListener("visibilitychange", reset);
  view.addEventListener("resize", reset);
  preference?.addEventListener?.("change", reset);
  refresh();
  return {
    prepare, refresh,
    destroy() {
      disposed = true;
      cancel();
      observer.disconnect();
      root.removeEventListener("load", refresh, true);
      doc.removeEventListener("scroll", onScroll, true);
      doc.removeEventListener("visibilitychange", reset);
      view.removeEventListener("resize", reset);
      preference?.removeEventListener?.("change", reset);
    },
  };
}
