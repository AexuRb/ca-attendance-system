import { describe, expect, it } from "vitest";
import {
  filterAccountCandidates,
  mergeAccountCandidates,
  type AccountCandidate,
} from "./accountCandidates";

const candidates: AccountCandidate[] = [
  { id: 1, studentNo: "1001", name: "张三", role: "MEMBER" },
  { id: 2, studentNo: "1002", name: "李四", role: "MINISTER" },
];

describe("account candidates", () => {
  it("filters by name or student number", () => {
    expect(filterAccountCandidates(candidates, "张")).toEqual([candidates[0]]);
    expect(filterAccountCandidates(candidates, "1002")).toEqual([candidates[1]]);
  });

  it("keeps a historical selection visible when it is no longer active", () => {
    const historical: AccountCandidate = {
      id: 3,
      studentNo: "",
      name: "已停用负责人",
      role: "MINISTER",
      inactive: true,
    };

    expect(mergeAccountCandidates(candidates, historical)).toContainEqual(
      historical,
    );
  });
});
