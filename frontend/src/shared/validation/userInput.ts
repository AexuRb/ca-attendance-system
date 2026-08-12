export const USER_INPUT_LIMITS = {
  studentNoMin: 6,
  studentNoMax: 32,
  nameMax: 64,
  passwordMin: 6,
  passwordMax: 64,
  phoneMax: 64,
  collegeMax: 128,
  gradeMax: 16,
  qqMax: 32,
  reasonMax: 500,
} as const;

interface MemberInput {
  studentNo?: string;
  name?: string;
  phone?: string;
  major?: string;
  grade?: string;
  qq?: string;
  reason?: string;
}

interface ProfileInput {
  phone?: string;
  college?: string;
  major?: string;
  grade?: string;
  qq?: string;
}

export type InputErrors = Record<string, string>;

export function validateMemberInput(
  input: MemberInput,
  options: { validateStudentNo?: boolean; requireReason?: boolean } = {},
): InputErrors {
  const errors: InputErrors = {};
  if (options.validateStudentNo !== false) {
    const studentNo = input.studentNo?.trim() || "";
    if (!/^\d{6,32}$/.test(studentNo)) {
      errors.studentNo = "学号必须为 6 至 32 位纯数字";
    }
  }
  const name = input.name?.trim() || "";
  if (!name) errors.name = "姓名不能为空";
  else if (name.length > USER_INPUT_LIMITS.nameMax) {
    errors.name = "姓名不能超过 64 个字符";
  }
  Object.assign(errors, validateProfileInput(input));
  if (options.requireReason) {
    const reason = input.reason?.trim() || "";
    if (!reason) errors.reason = "请填写修改原因";
    else if (reason.length > USER_INPUT_LIMITS.reasonMax) {
      errors.reason = "修改原因不能超过 500 个字符";
    }
  }
  return errors;
}

export function validateProfileInput(input: ProfileInput): InputErrors {
  const errors: InputErrors = {};
  checkMax(errors, "phone", input.phone, USER_INPUT_LIMITS.phoneMax, "联系方式");
  checkMax(
    errors,
    input.college !== undefined ? "college" : "major",
    input.college ?? input.major,
    USER_INPUT_LIMITS.collegeMax,
    "学院",
  );
  checkMax(errors, "grade", input.grade, USER_INPUT_LIMITS.gradeMax, "年级");
  checkMax(errors, "qq", input.qq, USER_INPUT_LIMITS.qqMax, "QQ");
  return errors;
}

export function validatePassword(value: string): string {
  if (
    !value.trim() ||
    value.length < USER_INPUT_LIMITS.passwordMin ||
    value.length > USER_INPUT_LIMITS.passwordMax
  ) {
    return "密码长度必须为 6 至 64 个字符";
  }
  return "";
}

export function focusFirstInvalid(
  root: HTMLFormElement | null,
  errors: InputErrors,
) {
  const firstName = Object.keys(errors)[0];
  if (!firstName) return;
  requestAnimationFrame(() => {
    root
      ?.querySelector<HTMLElement>(`[name="${firstName}"]`)
      ?.focus();
  });
}

function checkMax(
  errors: InputErrors,
  key: string,
  value: string | undefined,
  max: number,
  label: string,
) {
  if ((value?.trim().length || 0) > max) {
    errors[key] = `${label}不能超过 ${max} 个字符`;
  }
}
