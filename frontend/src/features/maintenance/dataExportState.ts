import type { ExportRequest } from "./dataCenterTypes";

export function exportSelectionFingerprint(
  request: Pick<ExportRequest, "source" | "fields" | "filters"> & {
    filename?: string;
  },
): string {
  return JSON.stringify({
    source: request.source,
    fields: [...request.fields],
    filters: Object.entries(request.filters).sort(([left], [right]) =>
      left.localeCompare(right),
    ),
  });
}
