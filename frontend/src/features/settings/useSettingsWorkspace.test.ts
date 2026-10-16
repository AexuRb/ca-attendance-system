// @vitest-environment jsdom
import { defineComponent } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSettingsWorkspace } from "./useSettingsWorkspace";

const mocks = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), notify: vi.fn() }));
vi.mock("../../shared/api", () => ({ get: mocks.get, put: mocks.put }));
vi.mock("../../shared/composables/useToast", () => ({ notify: mocks.notify }));
vi.mock("../../app/session", () => ({ useSession: () => ({ user: { value: { role: "ADMIN" } } }) }));
vi.mock("../../appearance/appearanceStore", () => ({
  useAppearance: () => ({ state: { active: "SPATIAL" } }), changeGlobalAppearance: vi.fn(),
}));
vi.mock("vue-router", () => ({ useRoute: () => ({ query: {} }), onBeforeRouteLeave: vi.fn() }));

afterEach(() => { vi.resetAllMocks(); document.body.innerHTML = ""; });
const harness = defineComponent({
  setup: () => useSettingsWorkspace(),
  template: `<input v-if="periods.length" name="start" v-model="periods[0].startTime" />
    <button @click="savePeriods">保存时段</button><button @click="saveWeekdays">保存星期</button>
    <button @click="saveAttendancePolicy">保存规则</button>
    <span class="error">{{periodSaveError}}</span><span class="dirty">{{periodsDirty}}</span>`,
});
async function load() {
  mocks.get.mockImplementation(async (url: string) => url.endsWith("weekdays")
    ? Array.from({ length: 7 }, (_, i) => ({ weekday: i + 1, enabled: i === 0 }))
    : url.endsWith("duty-periods") ? [{ startTime: "14:00", endTime: "15:00", enabled: true }]
    : { requireDutyDay: false, requireDutyPeriod: false });
  const wrapper = mount(harness);
  await flushPromises();
  return wrapper;
}

describe("settings save feedback", () => {
  it("does not submit unchanged section drafts", async () => {
    const wrapper = await load();
    for (const button of wrapper.findAll("button")) await button.trigger("click");
    await flushPromises();
    expect(mocks.put).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("retains the failed period draft, shows its error and clears it after a successful retry", async () => {
    const wrapper = await load();
    await wrapper.get("input").setValue("14:30");
    mocks.put.mockRejectedValueOnce(new Error("时段保存失败"));
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.get(".error").text()).toBe("时段保存失败");
    expect(wrapper.get<HTMLInputElement>("input").element.value).toBe("14:30");
    expect(wrapper.get(".dirty").text()).toBe("true");
    mocks.put.mockResolvedValueOnce([{ startTime: "14:30", endTime: "15:00", enabled: true }]);
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(mocks.put).toHaveBeenLastCalledWith("/api/settings/duty-periods", {
      periods: [{ startTime: "14:30", endTime: "15:00", enabled: true }],
    });
    expect(wrapper.get(".error").text()).toBe("");
    expect(wrapper.get(".dirty").text()).toBe("false");
    wrapper.unmount();
  });
});
