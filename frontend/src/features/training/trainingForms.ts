import type {
  TrainingParticipantForm,
  TrainingSessionForm,
} from "./trainingTypes";

export type TrainingSessionErrors = Partial<
  Record<"title" | "trainingDate" | "endTime", string>
>;
export type TrainingParticipantErrors = Partial<
  Record<"studentNo" | "name" | "durationHours", string>
>;

export function validateTrainingSessionForm(
  form: TrainingSessionForm,
): TrainingSessionErrors {
  const errors: TrainingSessionErrors = {};
  if (!form.title.trim()) errors.title = "请填写培训标题";
  else if (form.title.trim().length > 100) {
    errors.title = "培训标题不能超过 100 个字符";
  }
  if (!form.trainingDate) errors.trainingDate = "请选择培训日期";
  if (form.startTime && form.endTime && form.endTime < form.startTime) {
    errors.endTime = "结束时间不能早于开始时间";
  }
  return errors;
}

export function validateParticipantForm(
  form: TrainingParticipantForm,
): TrainingParticipantErrors {
  const errors: TrainingParticipantErrors = {};
  const studentNo = form.studentNo.trim();
  if (studentNo && !/^\d{6,32}$/.test(studentNo)) {
    errors.studentNo = "学号应为 6 至 32 位数字";
  }
  if (!form.name.trim()) errors.name = "请填写姓名";
  const duration = Number(form.durationHours);
  if (!Number.isFinite(duration) || duration < 0) {
    errors.durationHours = "时长不能小于 0";
  }
  return errors;
}
