// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DutyTimeWorkspace from "./DutyTimeWorkspace.vue";

const periods = [
  { startTime: "14:00", endTime: "16:00", enabled: true },
  { startTime: "16:00", endTime: "18:00", enabled: false },
];

function mountWorkspace() {
  return mount(DutyTimeWorkspace, {
    props: {
      periods,
      policy: { requireDutyDay: true, requireDutyPeriod: false },
      canEditPolicy: true,
      periodError: "",
      policyDirty: false,
      periodsDirty: false,
      policyPending: false,
      periodsPending: false,
    },
  });
}

describe("DutyTimeWorkspace", () => {
  it("keeps one visible time-period navigation target in both presentations", async () => {
    const wrapper = mountWorkspace();
    for (const refined of [false, true]) {
      await wrapper.setProps({ refined });
      expect(wrapper.findAll('#settings-periods')).toHaveLength(1);
      expect(wrapper.get('#settings-periods').text()).toContain('值班时间段');
      expect(wrapper.find('.duty-day-rail').exists()).toBe(refined);
    }
    wrapper.unmount();
  });
  it("shows clearly different rule and period states", () => {
    const wrapper = mountWorkspace();
    const rules = wrapper.findAll(".duty-policy-option");
    const tabs = wrapper.findAll(".duty-period-tab");

    expect(rules[0]?.classes()).toContain("active");
    expect(rules[0]?.text()).toContain("已限制");
    expect(rules[1]?.classes()).toContain("inactive");
    expect(rules[1]?.text()).toContain("已放开");
    expect(tabs[1]?.classes()).toContain("disabled");
    expect(tabs[1]?.text()).toContain("停用");
    expect(wrapper.findAll(".duty-calendar-compact-row")).toHaveLength(2);
  });

  it("proportionally positions refined duty periods", async () => {
    const wrapper = mountWorkspace();
    await wrapper.setProps({ refined: true });

    expect(wrapper.find(".duty-period-overview").exists()).toBe(false);
    expect(wrapper.findAll(".duty-period-cards .duty-period-tab")).toHaveLength(2);
    const blocks = wrapper.findAll(".duty-rail-block");
    expect(blocks).toHaveLength(2);
    expect(blocks[0]?.attributes("style")).toContain("--rail-left");
    expect(blocks[0]?.attributes("style")).toContain("--rail-width");
  });

  it("keeps adjacent short periods proportional without visual overlap", async () => {
    const wrapper = mountWorkspace();
    await wrapper.setProps({
      refined: true,
      periods: [
        { startTime: "14:00", endTime: "15:00", enabled: true },
        { startTime: "15:00", endTime: "16:00", enabled: true },
        { startTime: "16:00", endTime: "18:00", enabled: true },
      ],
    });
    const blocks = wrapper.findAll(".duty-rail-block");
    const percentage = (index: number, property: string) =>
      parseFloat((blocks[index]!.element as HTMLElement).style.getPropertyValue(property));
    const firstWidth = percentage(0, "--rail-width");
    expect(firstWidth).toBeCloseTo((60 / 1080) * 95.2);
    expect(percentage(0, "--rail-left") + firstWidth).toBeCloseTo(percentage(1, "--rail-left"));
    expect(percentage(2, "--rail-width")).toBeCloseTo(firstWidth * 2);
    expect(blocks[0]!.attributes("aria-label")).toContain("14:00—15:00");
    wrapper.unmount();
  });

  it("allows saving only changed, valid periods and keeps errors local", async () => {
    const wrapper = mountWorkspace();
    const save = wrapper.get<HTMLButtonElement>(".duty-save-periods");
    expect(save.element.disabled).toBe(true);
    await save.trigger("click");
    expect(wrapper.emitted("save-periods")).toBeUndefined();
    await wrapper.setProps({ periodsDirty: true });
    expect(save.element.disabled).toBe(false);
    await save.trigger("click");
    expect(wrapper.emitted("save-periods")).toHaveLength(1);
    await wrapper.setProps({ periodsPending: true });
    expect(save.element.disabled).toBe(true);
    await wrapper.setProps({ periodsPending: false, periodSaveError: "时段保存失败" });
    expect(wrapper.get(".settings-save-error").text()).toBe("时段保存失败");
    expect(wrapper.props("periods")).toEqual(periods);
    wrapper.unmount();
  });

  it("emits a policy update without mutating the prop", async () => {
    const wrapper = mountWorkspace();
    await wrapper.get('input[aria-label="强制值班时段"]').setValue(true);

    expect(wrapper.emitted("update:policy")).toEqual([
      [{ requireDutyDay: true, requireDutyPeriod: true }],
    ]);
    expect(wrapper.props("policy").requireDutyPeriod).toBe(false);
  });

  it("edits only the selected duty period", async () => {
    const wrapper = mountWorkspace();
    await wrapper.findAll(".duty-period-tab")[1]?.trigger("click");
    await wrapper.get('input[name="period-2-start"]').setValue("16:30");

    const updates = wrapper.emitted("update:periods");
    const latest = updates?.at(-1)?.[0] as typeof periods;
    expect(latest[0]?.startTime).toBe("14:00");
    expect(latest[1]?.startTime).toBe("16:30");
    expect(periods[1]?.startTime).toBe("16:00");
  });

  it("marks overlapping periods at every related surface", async () => {
    const wrapper = mount(DutyTimeWorkspace, {
      props: {
        periods: [
          { startTime: "14:00", endTime: "16:00", enabled: true },
          { startTime: "15:00", endTime: "17:00", enabled: true },
        ],
        policy: { requireDutyDay: true, requireDutyPeriod: true },
        canEditPolicy: true,
        periodError: "启用中的值班时间段不能重叠",
        policyDirty: false,
        periodsDirty: true,
        policyPending: false,
        periodsPending: false,
      },
    });

    expect(wrapper.findAll(".duty-period-tab.conflict")).toHaveLength(2);
    const blocks = wrapper.findAll(".duty-calendar-block.conflict");
    expect(blocks).toHaveLength(2);
    expect(blocks.every((block) => block.classes().includes("multi-lane"))).toBe(true);
    expect(blocks[0]?.attributes("style")).toContain("--period-lane-left: 0%");
    expect(blocks[1]?.attributes("style")).toContain("--period-lane-left: 50%");
    expect(wrapper.get(".duty-period-form").classes()).toContain("invalid");
    expect(wrapper.get(".duty-period-error").text()).toContain("重叠");
    expect(wrapper.get(".duty-dirty-state").text()).toBe("未保存");
  });

  it("does not mark a valid selected form when other periods conflict", async () => {
    const wrapper = mount(DutyTimeWorkspace, {
      props: {
        periods: [
          { startTime: "14:00", endTime: "16:00", enabled: true },
          { startTime: "15:00", endTime: "17:00", enabled: true },
          { startTime: "18:00", endTime: "20:00", enabled: true },
        ],
        policy: { requireDutyDay: true, requireDutyPeriod: true },
        canEditPolicy: true,
        periodError: "启用中的值班时间段不能重叠",
        policyDirty: false,
        periodsDirty: true,
        policyPending: false,
        periodsPending: false,
      },
    });

    await wrapper.findAll(".duty-period-tab")[2]?.trigger("click");

    expect(wrapper.get(".duty-period-form").classes()).not.toContain("invalid");
    expect(wrapper.get(".duty-period-error").text()).toContain("重叠");
    expect(wrapper.get<HTMLButtonElement>(".duty-save-periods").element.disabled).toBe(
      true,
    );
  });
});
