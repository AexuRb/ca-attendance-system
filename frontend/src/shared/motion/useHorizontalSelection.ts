import { onBeforeUnmount, onMounted, watch, type Ref } from "vue";

export function useHorizontalSelection(group: Readonly<Ref<HTMLElement | null>>, selection: Readonly<Ref<string | number>>) {
  const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)");
  let animation: Animation | undefined;

  function stop() {
    animation?.cancel();
    animation = undefined;
  }

  watch(selection, (next, previous) => {
    if (!group.value || reducedMotion?.matches) {
      stop();
      return;
    }
    const marks = [...group.value.querySelectorAll<HTMLElement>(":scope > :is(a, button) > [data-horizontal-mark]")];
    const target = marks.find(mark => mark.dataset.horizontalMark === String(next));
    if (!target?.animate) {
      stop();
      return;
    }
    // Measure before cancelling so a quick second choice starts where the highlight was seen.
    const start = marks.find(mark => mark.dataset.horizontalMark === String(previous))?.getBoundingClientRect();
    stop();
    if (!start?.width) return;

    const end = target.getBoundingClientRect();
    const targetWidth = target.offsetWidth;
    if (!end.width || !targetWidth) return;
    const zoom = end.width / targetWidth;
    const shift = (start.left - end.left) / zoom;
    const from = Math.abs(start.top - end.top) < 2
      ? { transform: `translateX(${shift}px) scaleX(${start.width / end.width})` }
      : { opacity: 0.35 };
    const running = target.animate([from, { transform: "none", opacity: 1 }], {
      duration: 180,
      easing: "cubic-bezier(.2,.7,.25,1)",
    });
    animation = running;
    void running.finished.then(() => {
      if (animation === running) animation = undefined;
    }, () => {});
  }, { flush: "post" });

  onMounted(() => {
    reducedMotion?.addEventListener("change", stop);
    window.addEventListener("resize", stop);
  });
  onBeforeUnmount(() => {
    stop();
    reducedMotion?.removeEventListener("change", stop);
    window.removeEventListener("resize", stop);
  });
}
