// @vitest-environment jsdom
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import TrainingParticipantList from "./TrainingParticipantList.vue";
import TrainingSessionDrawer from "./TrainingSessionDrawer.vue";
import TrainingSessionHeader from "./TrainingSessionHeader.vue";
import TrainingSessionList from "./TrainingSessionList.vue";
import type { TrainingParticipant, TrainingSession } from "./trainingTypes";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("TrainingSessionList", () => {
  it("selects sessions and exposes bounded paging", async () => {
    const wrapper = mount(TrainingSessionList, {
      props: {
        items: [session(1), session(2)],
        selectedId: 1,
        total: 42,
        page: 2,
        pageSize: 20,
        hasMore: true,
        loading: false,
        error: "",
      },
    });

    expect(wrapper.findAll(".training-session-item")).toHaveLength(2);
    expect(wrapper.get('.training-session-item[aria-pressed="true"]').text())
      .toContain("培训 1");
    await wrapper.findAll(".training-session-item")[1].trigger("click");
    await wrapper.get('button[aria-label="下一页培训场次"]').trigger("click");

    expect(wrapper.emitted("select")?.[0]).toEqual([session(2)]);
    expect(wrapper.emitted("page")?.[0]).toEqual([3]);
  });

  it("renders loading, failure retry and empty feedback separately", async () => {
    const wrapper = mount(TrainingSessionList, {
      props: {
        items: [],
        selectedId: null,
        total: 0,
        page: 1,
        pageSize: 20,
        hasMore: false,
        loading: true,
        error: "",
      },
    });
    expect(wrapper.get('[aria-live="polite"]').text()).toContain("加载");

    await wrapper.setProps({ loading: false, error: "场次读取失败" });
    expect(wrapper.get('[role="alert"]').text()).toContain("场次读取失败");
    await wrapper.get('[role="alert"] button').trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);

    await wrapper.setProps({ error: "" });
    expect(wrapper.text()).toContain("暂无培训场次");
  });
});

describe("TrainingSessionHeader", () => {
  it("keeps edit visible and secondary session commands in an accessible menu", async () => {
    const wrapper = mount(TrainingSessionHeader, {
      attachTo: document.body,
      props: { session: session(8) },
    });

    expect(wrapper.text()).toContain("陈禹杭");
    expect(wrapper.text()).toContain("44 小时");
    const edit = wrapper.get(".training-overview-edit");
    expect(edit.text()).toContain("编辑培训");
    await edit.trigger("click");
    expect(wrapper.emitted("edit")).toHaveLength(1);
    await wrapper.get('button[aria-haspopup="menu"]').trigger("click");
    await flushPromises();

    const menu = document.querySelector('[role="menu"]') as HTMLElement;
    expect(menu.textContent).toContain("导出名单");
    expect(menu.textContent).toContain("归档培训");
    wrapper.unmount();
  });
});

describe("TrainingParticipantList", () => {
  it("searches, pages and emits the primary participant actions", async () => {
    const wrapper = mount(TrainingParticipantList, {
      props: {
        items: [participant(1), participant(2)],
        total: 22,
        page: 1,
        pageSize: 20,
        hasMore: true,
        loading: false,
        error: "",
        keyword: "",
      },
    });

    await wrapper.get('input[name="participant-search"]').setValue("主讲人");
    await wrapper.get("form").trigger("submit");
    await wrapper.get('button[aria-label="下一页参与名单"]').trigger("click");
    await wrapper.get('button[data-action="add-participant"]').trigger("click");
    await wrapper.get('button[data-action="import-participants"]').trigger("click");

    expect(wrapper.emitted("update:keyword")?.at(-1)).toEqual(["主讲人"]);
    expect(wrapper.emitted("search")).toHaveLength(1);
    expect(wrapper.emitted("page")?.[0]).toEqual([2]);
    expect(wrapper.emitted("add")).toHaveLength(1);
    expect(wrapper.emitted("import")).toHaveLength(1);
    expect(wrapper.findAll(".training-participant-row")).toHaveLength(2);
    expect(wrapper.get("details").text()).toContain("负责演示");
  });

  it("does not confuse empty, loading and failed participant states", async () => {
    const wrapper = mount(TrainingParticipantList, {
      props: {
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
        hasMore: false,
        loading: false,
        error: "",
        keyword: "未匹配",
      },
    });
    expect(wrapper.text()).toContain("没有匹配的参与记录");

    await wrapper.setProps({ loading: true });
    expect(wrapper.get('[aria-live="polite"]').text()).toContain("加载");

    await wrapper.setProps({ loading: false, error: "名单读取失败" });
    expect(wrapper.get('[role="alert"]').text()).toContain("名单读取失败");
  });
});

describe("TrainingSessionDrawer", () => {
  it("closes after choosing a session from the mobile directory", async () => {
    const wrapper = mount(TrainingSessionDrawer, {
      attachTo: document.body,
      props: {
        open: true,
        items: [session(3)],
        selectedId: null,
        total: 1,
        page: 1,
        pageSize: 20,
        hasMore: false,
        loading: false,
        error: "",
      },
    });

    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    expect(document.documentElement.style.overflow).toBe("hidden");
    (document.querySelector(".training-session-item") as HTMLButtonElement).click();
    await flushPromises();
    expect(wrapper.emitted("select")?.[0]).toEqual([session(3)]);
    expect(wrapper.emitted("close")).toHaveLength(1);
    await wrapper.setProps({ open: false });
    expect(document.documentElement.style.overflow).toBe("");
    wrapper.unmount();
  });
});

function session(id: number): TrainingSession {
  return {
    id,
    title: `培训 ${id}`,
    trainingDate: "2026-08-12",
    startTime: "14:00",
    endTime: "16:00",
    location: "协会活动室",
    speaker: "陈禹杭",
    description: "培训说明",
    status: "COMPLETED",
    participantCount: 22,
    totalDurationHours: 44,
    createdAt: "2026-08-12T10:00:00",
    updatedAt: "2026-08-12T10:00:00",
  };
}

function participant(id: number): TrainingParticipant {
  return {
    id,
    sessionId: 1,
    studentNo: `100000000${id}`,
    name: `参与成员 ${id}`,
    durationHours: 2,
    remark: id === 1 ? "主讲人，负责演示" : "",
  };
}
