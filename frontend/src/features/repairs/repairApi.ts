import { api } from "../../shared/api";
import type { RepairPageRequest } from "./useRepairBoard";
import type { RepairPage } from "./repairTypes";

export function fetchRepairPage(request: RepairPageRequest) {
  const params = new URLSearchParams({
    status: request.status,
    page: String(request.page),
    pageSize: String(request.pageSize),
  });
  Object.entries(request.filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  return api<RepairPage>(`/api/repairs?${params}`, {
    signal: request.signal,
  });
}
