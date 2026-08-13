import { api } from "../../shared/api";
import type {
  TrainingPage,
  TrainingParticipant,
  TrainingSession,
} from "./trainingTypes";
import type {
  TrainingParticipantPageRequest,
  TrainingSessionPageRequest,
} from "./useTrainingWorkspace";

export function fetchTrainingSessionPage(request: TrainingSessionPageRequest) {
  const params = new URLSearchParams({
    page: String(request.page),
    pageSize: String(request.pageSize),
  });
  Object.entries(request.filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  return api<TrainingPage<TrainingSession>>(`/api/trainings/page?${params}`, {
    signal: request.signal,
  });
}

export function fetchTrainingParticipantPage(
  request: TrainingParticipantPageRequest,
) {
  const params = new URLSearchParams({
    page: String(request.page),
    pageSize: String(request.pageSize),
  });
  if (request.keyword) params.set("keyword", request.keyword);
  return api<TrainingPage<TrainingParticipant>>(
    `/api/trainings/${request.sessionId}/participants/page?${params}`,
    { signal: request.signal },
  );
}
