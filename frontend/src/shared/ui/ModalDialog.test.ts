// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { defineComponent, nextTick, ref } from "vue";
import ModalDialog from "./ModalDialog.vue";

const DialogHarness = defineComponent({
  components: { ModalDialog },
  setup() {
    const open = ref(false);
    return { open };
  },
  template: `
    <div>
      <button id="opener" type="button" @click="open = true">打开</button>
      <ModalDialog
        :open="open"
        title="测试弹窗"
        @close="open = false"
      >
        <input id="first-field" aria-label="第一个字段" />
        <button id="last-action" type="button">完成</button>
      </ModalDialog>
    </div>
  `,
});

const NestedDialogHarness = defineComponent({
  components: { ModalDialog },
  setup() {
    const parentOpen = ref(false);
    const childOpen = ref(false);
    return { parentOpen, childOpen };
  },
  template: `
    <div>
      <button id="parent-opener" type="button" @click="parentOpen = true">
        打开父弹窗
      </button>
      <ModalDialog
        :open="parentOpen"
        title="父弹窗"
        @close="parentOpen = false"
      >
        <button id="child-opener" type="button" @click="childOpen = true">
          打开子弹窗
        </button>
        <ModalDialog
          :open="childOpen"
          title="子弹窗"
          @close="childOpen = false"
        >
          <input id="child-field" aria-label="子弹窗字段" />
        </ModalDialog>
      </ModalDialog>
    </div>
  `,
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function openDialog() {
  const wrapper = mount(DialogHarness, { attachTo: document.body });
  const opener = wrapper.get<HTMLButtonElement>("#opener");
  opener.element.focus();
  await opener.trigger("click");
  await nextTick();
  await nextTick();
  return wrapper;
}

describe("ModalDialog", () => {
  it("focuses the first interactive control when opened", async () => {
    const wrapper = await openDialog();

    expect(document.activeElement?.id).toBe("first-field");
    wrapper.unmount();
  });

  it("keeps tab focus inside the active dialog", async () => {
    const wrapper = await openDialog();
    const first = document.querySelector<HTMLInputElement>("#first-field")!;
    const last = document.querySelector<HTMLButtonElement>("#last-action")!;
    const close = document.querySelector<HTMLButtonElement>(
      'button[aria-label="关闭"]',
    )!;

    last.focus();
    document.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        bubbles: true,
        cancelable: true,
      }),
    );
    expect(document.activeElement).toBe(close);

    close.focus();
    document.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        shiftKey: true,
        bubbles: true,
        cancelable: true,
      }),
    );
    expect(document.activeElement).toBe(last);
    expect(first).not.toBe(document.activeElement);
    wrapper.unmount();
  });

  it("returns escaped focus to the active dialog on the next Tab", async () => {
    const wrapper = await openDialog();
    const opener = wrapper.get<HTMLButtonElement>("#opener");
    opener.element.focus();

    document.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        bubbles: true,
        cancelable: true,
      }),
    );

    expect(document.activeElement?.id).toBe("first-field");
    wrapper.unmount();
  });

  it("closes with Escape and restores focus to the opener", async () => {
    const wrapper = await openDialog();

    document.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
        cancelable: true,
      }),
    );
    await nextTick();
    await nextTick();

    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement?.id).toBe("opener");
    wrapper.unmount();
  });

  it("only closes the top dialog when dialogs are nested", async () => {
    const wrapper = mount(NestedDialogHarness, { attachTo: document.body });
    await wrapper.get("#parent-opener").trigger("click");
    await nextTick();
    await nextTick();
    document.querySelector<HTMLButtonElement>("#child-opener")!.click();
    await nextTick();
    await nextTick();

    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2);
    expect(document.activeElement?.id).toBe("child-field");

    document.dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Escape",
        bubbles: true,
        cancelable: true,
      }),
    );
    await nextTick();
    await nextTick();

    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1);
    expect(document.activeElement?.id).toBe("child-opener");
    wrapper.unmount();
  });

  it("keeps page scrolling locked until all nested dialogs close", async () => {
    const previousHtmlOverflow = document.documentElement.style.overflow;
    const previousBodyOverflow = document.body.style.overflow;
    document.documentElement.style.overflow = "auto";
    document.body.style.overflow = "scroll";
    const wrapper = mount(NestedDialogHarness, { attachTo: document.body });

    try {
      await wrapper.get("#parent-opener").trigger("click");
      await nextTick();
      await nextTick();
      expect(document.documentElement.style.overflow).toBe("hidden");
      expect(document.body.style.overflow).toBe("hidden");

      document.querySelector<HTMLButtonElement>("#child-opener")!.click();
      await nextTick();
      await nextTick();

      const dialogs = document.querySelectorAll<HTMLElement>('[role="dialog"]');
      dialogs[1].querySelector<HTMLButtonElement>('button[aria-label="关闭"]')!.click();
      await nextTick();
      await nextTick();
      expect(document.documentElement.style.overflow).toBe("hidden");
      expect(document.body.style.overflow).toBe("hidden");

      dialogs[0].querySelector<HTMLButtonElement>('button[aria-label="关闭"]')!.click();
      await nextTick();
      await nextTick();
      expect(document.documentElement.style.overflow).toBe("auto");
      expect(document.body.style.overflow).toBe("scroll");
    } finally {
      wrapper.unmount();
      document.documentElement.style.overflow = previousHtmlOverflow;
      document.body.style.overflow = previousBodyOverflow;
    }
  });
});
