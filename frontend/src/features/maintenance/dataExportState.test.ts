import { describe, expect, it } from "vitest";
import { exportSelectionFingerprint } from "./dataExportState";

describe("custom export selection state", () => {
  const base = {
    source: "attendance",
    fields: ["name", "validHours"],
    filters: { from: "2026-01-01", to: "2026-08-10" },
    filename: "统计甲",
  };

  it("changes when preview data inputs change", () => {
    const fingerprint = exportSelectionFingerprint(base);

    expect(
      exportSelectionFingerprint({ ...base, source: "training" }),
    ).not.toBe(fingerprint);
    expect(
      exportSelectionFingerprint({
        ...base,
        fields: ["validHours", "name"],
      }),
    ).not.toBe(fingerprint);
    expect(
      exportSelectionFingerprint({
        ...base,
        filters: { ...base.filters, to: "2026-08-09" },
      }),
    ).not.toBe(fingerprint);
  });

  it("ignores filename-only changes", () => {
    expect(
      exportSelectionFingerprint({ ...base, filename: "统计乙" }),
    ).toBe(exportSelectionFingerprint(base));
  });
});
