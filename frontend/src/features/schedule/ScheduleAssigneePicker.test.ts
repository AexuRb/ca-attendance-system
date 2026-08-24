// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { defineComponent, nextTick, ref } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ScheduleAssigneePicker from "./ScheduleAssigneePicker.vue";
import type { ScheduleAssigneeOption } from "./scheduleAssignees";

const candidates: ScheduleAssigneeOption[] = [
  { studentNo: "A001", name: "Alpha" },
  { studentNo: "B002", name: "Bravo" },
  { studentNo: "C003", name: "Charlie" },
];

const DialogHarness = defineComponent({
  components: { ModalDialog, ScheduleAssigneePicker },
  setup() {
    const open = ref(false);
    const selected = ref<ScheduleAssigneeOption[]>([]);
    return { candidates, open, selected };
  },
  template: `
    <div>
      <button id="open-picker" type="button" @click="open = true">Open</button>
      <button id="close-picker" type="button" @click="open = false">Close</button>
      <ModalDialog :open="open" title="Choose assignees" @close="open = false">
        <ScheduleAssigneePicker
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
  return mount(ScheduleAssigneePicker, {
    attachTo: document.body,
    props: { candidates, modelValue: [] },
  });
}

function options(wrapper: ReturnType<typeof mountPicker>) {
  return wrapper.findAll<HTMLButtonElement>('[role="option"]');
}

describe("ScheduleAssigneePicker", () => {
  it("supports the same listbox keyboard contract for multi-selection", async () => {
    const wrapper = mountPicker();
    const items = () => options(wrapper);

    expect(wrapper.find('[role="listbox"]').attributes("aria-label")).toBe(
      "可选排班人员",
    );
    expect(wrapper.find('input[aria-label="搜索排班人员"]').exists()).toBe(true);
    expect(items().map((item) => item.attributes("tabindex"))).toEqual([
      "0",
      "-1",
      "-1",
    ]);

    await items()[0].trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(items()[1].element);
    await items()[1].trigger("keydown", { key: "End" });
    expect(document.activeElement).toBe(items()[2].element);
    await items()[2].trigger("keydown", { key: "Home" });
    expect(document.activeElement).toBe(items()[0].element);
    await items()[0].trigger("keydown", { key: "ArrowUp" });
    expect(document.activeElement).toBe(items()[2].element);

    await items()[2].trigger("keydown", { key: "Enter" });
    await wrapper.setProps({ modelValue: [candidates[2]] });
    await items()[1].trigger("keydown", { key: " " });
    expect(wrapper.emitted("update:modelValue")).toEqual([
      [[candidates[2]]],
      [[candidates[2], candidates[1]]],
    ]);
    wrapper.unmount();
  });

  it("clears its search and restores all options after the dialog reopens", async () => {
    const wrapper = mount(DialogHarness, { attachTo: document.body });

    await wrapper.get("#open-picker").trigger("click");
    await nextTick();
    await nextTick();
    const search = document.querySelector<HTMLInputElement>(
      'input[aria-label="搜索排班人员"]',
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
      document.querySelector<HTMLInputElement>(
        'input[aria-label="搜索排班人员"]',
      )!.value,
    ).toBe("");
    expect(document.querySelectorAll('[role="option"]')).toHaveLength(3);
    wrapper.unmount();
  });
});
