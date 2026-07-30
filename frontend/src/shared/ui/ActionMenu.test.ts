import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { nextTick } from "vue";
import ActionMenu from "./ActionMenu.vue";

afterEach(() => {
  document.body.innerHTML = "";
});

function mountMenu() {
  return mount(ActionMenu, {
    attachTo: document.body,
    slots: {
      default: `
        <button role="menuitem" type="button">编辑</button>
        <button role="menuitem" type="button">删除</button>
      `,
    },
  });
}

describe("ActionMenu", () => {
  it("opens and focuses the first menu item", async () => {
    const wrapper = mountMenu();

    await wrapper.get('button[aria-haspopup="menu"]').trigger("click");
    await nextTick();

    expect(document.querySelector('[role="menu"]')).not.toBeNull();
    expect(document.activeElement?.textContent).toBe("编辑");
    wrapper.unmount();
  });

  it("supports arrow navigation and Escape focus restoration", async () => {
    const wrapper = mountMenu();
    const trigger = wrapper.get<HTMLButtonElement>(
      'button[aria-haspopup="menu"]',
    );
    await trigger.trigger("click");
    await nextTick();

    document.activeElement?.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }),
    );
    expect(document.activeElement?.textContent).toBe("删除");

    document.activeElement?.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
    );
    await nextTick();
    expect(document.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(trigger.element);
    wrapper.unmount();
  });
});
