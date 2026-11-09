// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { defineComponent, nextTick, ref } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import AccountPicker from "./AccountPicker.vue";
import type { AccountCandidate } from "./accountCandidates";

const candidates: AccountCandidate[] = [
  { id: 1, studentNo: "A001", name: "Alpha" },
  { id: 2, studentNo: "B002", name: "Bravo" },
  { id: 3, studentNo: "C003", name: "Charlie" },
];

const DialogHarness = defineComponent({
  components: { AccountPicker, ModalDialog },
  setup() {
    const open = ref(false);
    const selected = ref<AccountCandidate | null>(null);
    return { candidates, open, selected };
  },
  template: `
    <div>
      <button id="open-picker" type="button" @click="open = true">Open</button>
      <button id="close-picker" type="button" @click="open = false">Close</button>
      <ModalDialog :open="open" title="Choose account" @close="open = false">
        <AccountPicker
          v-model="selected"
          :candidates="candidates"
          :open="open"
        />
      </ModalDialog>
    </div>
  `,
});

afterEach(() => {
  document.body.innerHTML = "";
});

function mountPicker() {
  return mount(AccountPicker, {
    attachTo: document.body,
    props: { candidates, modelValue: null },
  });
}

function options(wrapper: ReturnType<typeof mountPicker>) {
  return wrapper.findAll<HTMLButtonElement>('[role="option"]');
}

describe("AccountPicker", () => {
  it("paginates a large list and enters the current page from the search field", async () => {
    const many = Array.from({ length: 14 }, (_, i) => ({ id: i + 1, name: `示例成员${i + 1}`, studentNo: String(9900000000 + i) }));
    const wrapper = mount(AccountPicker, { attachTo: document.body, props: { candidates: many, modelValue: null } });
    expect(options(wrapper)).toHaveLength(6);
    await wrapper.get('button[aria-label="下一页成员"]').trigger("click");
    expect(options(wrapper)[0].attributes("data-option-id")).toBe("7");
    await wrapper.get("input").trigger("keydown", { key: "ArrowDown" }); await nextTick();
    expect(document.activeElement).toBe(options(wrapper)[0].element);
    await options(wrapper)[0].trigger("keydown", { key: "Enter" });
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([many[6]]);
    wrapper.unmount();
  });

  it("keeps the selection through a failed load and closes the expanded chooser before its parent", async () => {
    const wrapper = mount(AccountPicker, { attachTo: document.body, props: { candidates, modelValue: candidates[0], compactSelection: true } });
    await wrapper.get('button[data-button-kind="compact"]').trigger("click");
    await wrapper.setProps({ error: "请重试" });
    expect(wrapper.get('[role="alert"]').text()).toContain("加载失败");
    expect(wrapper.find('[role="option"]').exists()).toBe(false);
    expect(wrapper.get('.account-picker-current').text()).toContain("Alpha");
    await wrapper.get('.member-picker-state button').trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
    await wrapper.setProps({ error: "" });
    const event = new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true });
    options(wrapper)[0].element.dispatchEvent(event); await nextTick();
    expect(event.cancelBubble || event.defaultPrevented).toBe(true);
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false);
    await wrapper.get('button[data-button-kind="compact"]').trigger("click");
    await wrapper.get("input").setValue("Bravo");
    expect(options(wrapper)).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps a compact selection stable until the user searches for another account", async () => {
    const wrapper = mount(AccountPicker, {
      attachTo: document.body,
      props: { candidates, modelValue: candidates[0], compactSelection: true },
    });

    expect(wrapper.find(".account-picker-current").text()).toContain("Alpha");
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false);
    await wrapper.get('button[data-button-kind="compact"]').trigger("click");
    expect(options(wrapper).map((item) => item.text())).toEqual([
      expect.stringContaining("Bravo"),
      expect.stringContaining("Charlie"),
    ]);
    await options(wrapper)[0].trigger("click");
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([candidates[1]]);
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("uses a single roving tab stop and supports listbox keyboard selection", async () => {
    const wrapper = mountPicker();
    const items = () => options(wrapper);

    expect(wrapper.find('[role="listbox"]').attributes("aria-label")).toBe(
      "选择账号候选列表",
    );
    expect(wrapper.find('input[aria-label="选择账号"]').exists()).toBe(true);
    expect(items().map((item) => item.attributes("tabindex"))).toEqual([
      "0",
      "-1",
      "-1",
    ]);

    await items()[0].trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(items()[1].element);
    expect(items().map((item) => item.attributes("tabindex"))).toEqual([
      "-1",
      "0",
      "-1",
    ]);

    await items()[1].trigger("keydown", { key: "ArrowDown" });
    await items()[2].trigger("keydown", { key: "End" });
    expect(document.activeElement).toBe(items()[2].element);

    await items()[2].trigger("keydown", { key: "Home" });
    expect(document.activeElement).toBe(items()[0].element);

    await items()[0].trigger("keydown", { key: "ArrowUp" });
    expect(document.activeElement).toBe(items()[2].element);

    await items()[2].trigger("keydown", { key: "Enter" });
    await items()[2].trigger("keydown", { key: " " });
    expect(wrapper.emitted("update:modelValue")).toEqual([
      [candidates[2]],
      [candidates[2]],
    ]);
    wrapper.unmount();
  });

  it("clears its search and restores all options after the dialog reopens", async () => {
    const wrapper = mount(DialogHarness, { attachTo: document.body });

    await wrapper.get("#open-picker").trigger("click");
    await nextTick();
    await nextTick();
    const search = document.querySelector<HTMLInputElement>(
      'input[aria-label="选择账号"]',
    )!;
    search.value = "bravo";
    search.dispatchEvent(new Event("input", { bubbles: true }));
    await nextTick();
    expect(document.querySelectorAll('[role="option"]')).toHaveLength(1);

    await wrapper.get("#close-picker").trigger("click");
    await nextTick();
    await wrapper.get("#open-picker").trigger("click");
    await nextTick();
    await nextTick();

    expect(
      document.querySelector<HTMLInputElement>('input[aria-label="选择账号"]')!
        .value,
    ).toBe("");
    expect(document.querySelectorAll('[role="option"]')).toHaveLength(3);
    wrapper.unmount();
  });
});
