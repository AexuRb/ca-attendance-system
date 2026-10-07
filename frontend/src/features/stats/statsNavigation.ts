import { createPrivateNavigationState } from "../../shared/navigation/privateNavigationState";

// Only opaque keys enter browser history; identities and result position stay in memory.
export const statsVisits = createPrivateNavigationState<{
  keyword: string; sort: string; memberId?: number; top: number; left: number; pageTop: number;
}>();
export const statsRecordLinks = createPrivateNavigationState<{ keyword: string; userId: number; from: string; to: string; preset: string }>();
