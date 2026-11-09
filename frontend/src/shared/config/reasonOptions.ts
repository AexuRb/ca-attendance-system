export const reasonOptions = {
  reviewReject: ["未实际值班", "提交时间异常", "重复提交", "提交信息有误"],
  attendanceCreate: ["忘记签到", "签到台异常", "线下值班补登", "历史记录补齐"],
  attendanceEdit: ["更正签到／签退时间", "补填漏签退", "更正审核结果", "按当前规则重新评估"],
  attendanceDelete: ["重复记录", "误补录", "记录归属错误"],
  memberEdit: ["基础资料更正", "联系方式更新", "职务调整", "账号状态调整"],
  memberEnable: ["恢复正常使用", "误停用恢复", "重新参与值班", "账号异常已处理"],
  memberDisable: ["毕业离会", "退出协会", "暂停参与值班", "账号异常处理"],
  memberDelete: ["误建账号", "重复账号", "错误导入账号"],
} as const;
