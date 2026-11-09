// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { describe, expect, it } from "vitest";
import ScheduleAssigneePicker from "./ScheduleAssigneePicker.vue";
import type { ScheduleAssigneeOption } from "./scheduleAssignees";

describe("ScheduleAssigneePicker", () => {
  it("retains multiple selections across pages and filtering", async () => {
    const candidates: ScheduleAssigneeOption[] = Array.from({ length: 13 }, (_, i) => ({ studentNo: String(9900000000 + i), name: `示例成员${i + 1}`, role: "MINISTER" }));
    const wrapper = mount(ScheduleAssigneePicker, { props: { candidates, modelValue: [], "onUpdate:modelValue": value => { void wrapper.setProps({ modelValue: value }); } } });
    expect(wrapper.findAll('[role="option"]')).toHaveLength(6);
    await wrapper.findAll('[role="option"]')[0].trigger("click");
    await wrapper.get('button[aria-label="下一页排班人员"]').trigger("click");
    await wrapper.findAll('[role="option"]')[0].trigger("click");
    expect(wrapper.props("modelValue").map(p => p.studentNo)).toEqual([candidates[0].studentNo, candidates[6].studentNo]);
    await wrapper.get("input").setValue("不存在的成员");
    expect(wrapper.find('[role="option"]').exists()).toBe(false);
    expect(wrapper.findAll('.member-picker-tag')).toHaveLength(2);
    await wrapper.get('button[aria-label="移除 示例成员1"]').trigger("click");
    expect(wrapper.props("modelValue")).toEqual([candidates[6]]);
    wrapper.unmount();
  });

  it("enters options from the search and rejects selection while disabled", async () => {
    const candidate: ScheduleAssigneeOption = { studentNo: "9900000001", name: "示例成员01", role: "MINISTER" };
    const wrapper = mount(ScheduleAssigneePicker, { attachTo: document.body, props: { candidates: [candidate], modelValue: [] } });
    await wrapper.get("input").trigger("keydown", { key: "ArrowDown" }); await nextTick();
    expect(document.activeElement).toBe(wrapper.get('[role="option"]').element);
    await wrapper.setProps({ disabled: true });
    await wrapper.get('[role="option"]').trigger("click");
    expect(wrapper.emitted("update:modelValue")).toBeUndefined();
    wrapper.unmount();
  });
});
