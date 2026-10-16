export interface CreditEntry { name: string; contribution: string }
export interface CreditsSetting { intro: string; contributors: CreditEntry[]; revision: string }
export const fixedContributor: Readonly<CreditEntry> = Object.freeze({ name: "2025 会长-陈禹杭", contribution: "项目发起、产品设计与开发" });
export const acknowledgements = {
  title: "鸣谢",
  intro: "感谢为本系统的诞生与完善付出时间和心力的每一位贡献者。",
  contributors: [fixedContributor],
};
