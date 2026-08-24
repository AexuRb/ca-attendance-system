// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import KioskHeader from "./KioskHeader.vue";

function pad(value: number) {
  return String(value).padStart(2, "0");
}

function localIso(date: Date) {
  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absoluteOffset = Math.abs(offsetMinutes);

  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${String(
      date.getMilliseconds(),
    ).padStart(3, "0")}`,
    `${sign}${pad(Math.floor(absoluteOffset / 60))}:${pad(absoluteOffset % 60)}`,
  ].join("");
}

describe("KioskHeader", () => {
  it("uses a local-offset datetime matching the displayed local clock", () => {
    const now = new Date(2026, 7, 21, 12, 34, 56, 789);
    const wrapper = mount(KioskHeader, {
      props: { online: true, now },
    });

    const time = wrapper.get("time");

    expect(time.text()).toBe(
      new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(now),
    );
    expect(time.attributes("datetime")).toBe(localIso(now));
    expect(time.attributes("datetime")).not.toMatch(/Z$/);
  });
});
