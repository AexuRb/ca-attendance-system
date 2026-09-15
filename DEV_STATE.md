# 开发状态

更新：2026-09-15。

## 当前任务：3.3.0发行交付

- 用户已明确授权打包发布，包括本轮提交、推送与Release。三套布局及各批视觉已确认，不重开设计。只保留学院，不增加专业。
- 版本统一3.3.0；安装版、便携版和SHA256SUMS已生成并本地核验。实际远端状态以[Release](https://github.com/AexuRb/ca-attendance-system/releases/tag/v3.3.0)和[CI](https://github.com/AexuRb/ca-attendance-system/actions)为准，不用本地文件存在代替上传成功。
- 本地前端测试/typecheck/build、后端package及依赖上界、桌面测试、脚本测试/语法检查、完整桌面npm审计和前端生产审计通过。真实权限回归、API/核心UI冒烟、性能基线通过。数量从本轮日志/CI报告读取；不沿用历史XML计数。
- 成品win-unpacked以isPackaged=true/3.3.0启动，内置Java/JAR、空库初始化、窗口重启、签到快捷键和退出通过；桌面稳定性五类场景通过。运行数据均隔离，未改已安装程序。
- 本轮发现发布脚本沿用旧布局选择器，已更新成员/考勤/日志性能定位、角色导航、数据中心tab与弹窗定位，业务断言保留。桌面构建依赖xmldom/fast-uri/js-yaml兼容升级后审计清零。
- 安装向导、跨版本安装覆盖、实际托盘点击、多显示器、物理断网及3000新账号吞吐未完整实测，已在[3.3.0发布说明](docs/releases/v3.3.0.md)披露。安装包未签名，主库与备份未加密。

## 后续

- 发布后核对Release资产、版本、大小与校验值；若CI或上传失败先解决，不将候选产物称为已发布。
- 根据实际安装反馈处理问题；未覆盖的原生/硬件场景保留为后续验收，不将H称为全部组合完成。
- 4177继续作为本地虚构数据预览，与发行包和真实数据库验收区分。

## 证据

- 本轮本地日志：tasks/release-3.3.0/（不提交）。smoke-verified、role-ui-complete、performance-verified为各自最终通过结果，前次选择器失败日志保留。
- [发布前状态](tasks/release-3.3.0/DEV_STATE.before.md)、[阶段汇总](docs/verification/多主题前端验收记录.md)。
- [导入HTTP](tasks/import-fields-20260915/review.md)、[桌面源码细节](tasks/desktop-details-20260915/review.md)。
