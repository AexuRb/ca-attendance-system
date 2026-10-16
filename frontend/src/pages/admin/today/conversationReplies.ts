import { commandObjects } from '../../../features/command-center/commandCatalog';
import type { ResolvedCommand } from '../../../features/command-center/commandTypes';
import type { TodayDashboardData } from './types';
import type { QuerySummary } from './conversationQuery';

export function createReplyPicker() {
  const previous = new Map<string, number>();
  return (key: string, replies: string[]) => {
    const last = previous.get(key);
    const index = last === undefined ? Math.floor(Math.random() * replies.length)
      : (last + 1 + Math.floor(Math.random() * (replies.length - 1))) % replies.length;
    previous.set(key, index);
    return replies[index]!;
  };
}
export type ReplyPicker = ReturnType<typeof createReplyPicker>;

export function initialReply(data: TodayDashboardData, pick: ReplyPicker) {
  const pending = count(data.todayPendingCount), repairs = count(data.ongoingRepairCount);
  const key = `entry:${pending > 0}:${repairs > 0}`;
  if (!pending && !repairs) return pick(key, [
    '今天的值班记录没有待审核项，当前也没有进行中的维修。',
    '今天没有值班记录等着审核，维修这边也没有进行中的事项。',
    '今天的值班记录没有待审核项，当前也没有进行中的维修。需要查记录或登记维修，可以用下面的常用功能。',
  ]);
  if (!repairs) return pick(key, [
    `今天有 ${pending} 条值班记录待审核，当前没有进行中的维修。`,
    `今天还有 ${pending} 条值班记录等着审核，维修这边目前没有进行中的事项。`,
    `可以先核对今天这 ${pending} 条待审核值班记录，当前没有进行中的维修。`,
  ]);
  if (!pending) return pick(key, [
    `今天的值班记录没有待审核项，目前有 ${repairs} 项维修正在进行。`,
    `今天没有值班记录等着审核，维修这边还有 ${repairs} 项正在进行。`,
    `今天没有值班记录待审核，可以看看这 ${repairs} 项进行中维修的进展。`,
  ]);
  return pick(key, [
    `今天有 ${pending} 条值班记录待审核，目前有 ${repairs} 项维修正在进行。`,
    `今天还有 ${pending} 条值班记录等着审核，维修这边有 ${repairs} 项正在进行。`,
    `可以先核对今天这 ${pending} 条待审核值班记录，${repairs} 项进行中的维修也可以继续跟进。`,
  ]);
}

const destinations: Record<string, string[]> = {
  今日概览: ['今日事项可以在概览里查看。', '想看看今天有哪些事，可以回到概览。', '回到概览后，可以继续查记录或登记维修。'],
  签到审核: ['打开审核列表，核对待审核的签到和签退。', '要处理审核的话，可以先看看有哪些记录在等审核。', '先看记录详情，核对后再作审核决定。'],
  值班记录: ['打开值班记录，查看签到和签退明细。', '想核对值班情况，可以到记录里看看。', '需要找某条记录时，可以在列表里继续筛选。'],
  数据统计: ['打开统计明细，查看值班和培训时长。', '想看各人的时长，可以到统计里看看。', '在统计页选好范围，就能继续核对各人的时长。'],
  排班管理: ['打开排班表，查看或调整值班安排。', '要看看谁在哪个时段值班，可以打开排班表。', '先核对各时段的人选，需要调整时再修改。'],
  成员名册: ['打开成员名册，查看成员资料。', '要找人或改资料的话，可以先看看名册。', '在名册里找到对应成员，再查看或修改资料。'],
  个人资料: ['打开个人资料，查看或修改账号信息。', '想更新自己的资料，可以从这里进去。', '先看看当前资料，需要修改的内容填好后再保存。'],
  维修事务: ['打开维修记录，查看处理进展。', '想知道维修到哪一步了，可以看看记录。', '找到对应维修后，可以继续核对详情、跟进处理。'],
  维修协议: ['打开维修协议预览，核对内容。', '这份协议可以先打开看看。', '先核对协议里的信息，再继续后续操作。'],
  培训记录: ['打开培训记录，查看场次和参与情况。', '想看哪场培训、有哪些人参加，可以到记录里找。', '先找到对应场次，再核对培训详情和参加人员。'],
  系统设置: ['打开系统设置，调整需要修改的配置。', '要改系统配置的话，可以从这里进去。', '先核对当前配置，修改后再保存。'],
  操作日志: ['打开操作日志，查找具体操作记录。', '想核对谁做了什么，可以到日志里看看。', '先选好查询条件，再核对对应的操作记录。'],
  维修回收站: ['打开回收站，查看已删除的维修记录。', '想找回删除的维修，可以先到回收站看看。', '先核对已删除的记录，恢复或彻底删除前再确认。'],
  备份管理: ['打开备份列表，查看已有备份。', '要找之前的备份，可以到列表里看看。', '先核对备份时间和文件，再选择需要的操作。'],
  数据导出: ['选好数据范围和字段，再查看导出预览。', '想导出哪些内容，可以进去选好范围和字段。', '先选需要的内容，核对预览后再导出。'],
  数据中心: ['打开数据中心，选择导入或导出。', '需要整理数据的话，从数据中心选择要做的操作。', '先进入数据中心，再选择数据类型和操作方式。'],
  数据导入: ['先选择要导入的数据类型，再选择文件。', '需要导入哪些内容，可以从这里选好类型后继续。', '选好数据类型和文件，校验通过后再确认导入。'],
};

const newRecordReplies: Record<string, string[]> = {
  维修事务: ['填写这次维修的信息，核对后提交。', '先把这次维修记下来，填好信息后再提交。', '这次维修可以从这里登记，提交前再核对一下信息。'],
  成员名册: ['填写成员信息，核对后提交。', '要新增成员的话，先把信息填好，再提交。', '从这里填写新成员的信息，提交前再核对一遍。'],
  培训记录: ['填写这场培训的信息，核对后提交。', '先把这场培训记下来，填好信息后再提交。', '这场培训可以从这里登记，提交前再核对一下信息。'],
  值班记录: ['填写要补录的值班记录，核对后提交。', '要补一条值班记录的话，先把信息填好，再提交。', '从这里补录值班记录，提交前再核对一遍。'],
};

export function describeOperation(command: ResolvedCommand, pick: ReplyPicker) {
  const action = command.canonical.replace(/^\/\s*/, '').split(' ')[0]!;
  const argumentsText = command.canonical.slice(`/ ${action} `.length);
  const object = commandObjects.flatMap(o => [o.canonical, ...o.aliases].map(name => ({ name, canonical: o.canonical })))
    .filter(o => argumentsText === o.name || argumentsText.startsWith(o.name + ' '))
    .sort((a, b) => b.name.length - a.name.length)[0]?.canonical || '该操作';
  let replies: string[], button: string;
  const friendly = ({ 维修事务: '维修单', 成员名册: '成员', 培训记录: '培训', 值班记录: '值班记录', 排班管理: '排班', 数据统计: '统计数据' } as Record<string, string>)[object] || object;
  if (action === '新建') {
    replies = newRecordReplies[object] || [`填好${friendly}信息，核对后提交即可。`, `先把${friendly}信息填好，再提交就行。`, `准备新建${friendly}，填好信息后再提交。`];
    button = `填写${friendly}`;
  } else if (action === '导入') {
    replies = [`在数据中心选择${friendly}文件，校验通过后再确认导入。`, `要导入${friendly}的话，先选文件，检查结果没问题后再确认。`, `先选好${friendly}文件，查看校验结果、核对内容后再导入。`];
    if (object === '培训记录') replies = ['先在数据中心选择培训场次，再上传参与名单并校验。', '培训名单需要对应到一场培训，选好场次和文件后再校验。', '进入数据导入后先选培训，核对名单校验结果，再确认导入。'];
    button = '前往数据导入';
  } else if (action === '导出') {
    replies = [`核对${friendly}的导出范围和内容，确认后生成文件。`, `要导出${friendly}的话，可以先看看预览，核对后再导出。`, `先检查${friendly}的范围和内容，有需要调整的地方再改，确认后导出。`];
    button = '核对导出配置';
  } else if (action === '创建') {
    replies = ['核对备份信息，确认后创建完整备份。', '要做一次完整备份的话，先看看确认信息，核对后再创建。', '先检查这次备份的确认信息，确认无误后再创建完整备份。'];
    button = '核对并创建备份';
  } else if (action === '设置') {
    replies = [`查看${object}的当前配置，修改后保存。`, `要调整${object}的话，可以先看看现在的配置，改好后再保存。`, `先核对${object}的当前配置，只调整需要修改的内容，然后保存。`];
    button = `设置${object}`;
  } else if (action === '查找') {
    replies = [`关键词会带到${object}页面，打开后查看匹配记录。`, `可以带着刚才的关键词去${object}列表看看，具体结果在明细里核对。`, `打开${object}页面后，先按这个关键词查找，需要时再调整条件。`];
    button = `查看匹配${object === '成员名册' ? '成员' : object === '维修事务' ? '维修' : object}`;
  } else {
    replies = destinations[object] || [`打开${object}，查看具体内容。`, `想看看${object}，可以从下面的入口进去。`, `先查看${object}的当前情况，再继续操作。`];
    const range = command.canonical.split(' ').find(word => ['今天', '昨天', '本周', '上周', '本月', '上月', '本年'].includes(word));
    const prefix = range === '今天' ? '今日' : range === '昨天' ? '昨日' : range || '';
    button = action === '查看' ? object === '数据统计' ? '查看统计明细' : `查看${prefix}${object}` : action === '预览' ? '查看维修协议' : `打开${object}`;
  }
  return { text: pick(`${action}:${object}`, replies), button };
}

export function summaryReply(result: QuerySummary, pick: ReplyPicker) {
  const { empty, key, subject, range, keyword } = result;
  const period = range === '这个日期范围内' ? '这段时间' : range;
  const readable = subject.replace(/^(待审核|未签退|进行中|已完成|已取消|启用|停用)/, '$1的');
  const record = key === 'repairs' ? readable + '记录' : readable;
  const selected = keyword && range ? `${period}内匹配关键词的${readable}` : `${period || '当前'}${readable}`;
  let replies: string[];
  if (key === 'stats') {
    replies = empty ? [
      `${period}统计中，值班次数和培训参与次数都是 0。`,
      `查到了，${period}统计里的值班和培训参与次数目前都是 0。`,
      `${period}统计中的值班次数和培训参与次数都为 0，可以查看明细核对。`,
    ] : [
      `${period}的值班和培训统计如下。`,
      `查到了，这是${period}的值班和培训统计。`,
      `${period}的统计在这里，具体到每个人的时长可以继续看明细。`,
    ];
  } else if (keyword && !range) {
    replies = empty ? [
      `这个关键词没有匹配到${readable}。`,
      `按这个关键词查了一下，没有匹配的${readable}。`,
      `没有找到匹配的${readable}，可以换个关键词试试。`,
    ] : [
      `匹配这个关键词的${readable}数量如下。`,
      `查到了，这个关键词有匹配的${readable}。`,
      `找到了匹配的${readable}，可以继续核对${key === 'members' ? '具体资料' : '详情'}。`,
    ];
  } else if (empty) {
    const next = range === '这个日期范围内' ? '调整日期再看看' : range ? '换个时间范围看看' : '调整条件再看看';
    replies = [
      `${range || '当前查询中'}没有${keyword ? '匹配关键词的' : ''}${record}。`,
      `查过了，${period || '这些条件下'}暂时没有${keyword ? '匹配关键词的' : ''}${readable}。`,
      `${period || '这些条件下'}没查到${keyword ? '匹配关键词的' : ''}${readable}，可以${next}。`,
    ];
  } else if (key === 'attendance' && subject === '待审核值班记录' && range === '今天' && !keyword) {
    replies = ['今天待审核的值班记录汇总如下。', '查到了，今天还有值班记录等着审核。', '今天还有值班记录待审核，可以继续核对明细。'];
  } else if (key === 'repairs' && subject === '进行中维修' && !range && !keyword) {
    replies = ['当前进行中的维修汇总如下。', '查到了，目前还有维修正在进行。', '当前还有维修在处理中，具体进展可以继续查看。'];
  } else {
    replies = [
      `${selected}汇总如下。`,
      `查到了，${period || '当前查询中'}有${keyword ? '匹配关键词的' : ''}${record}。`,
      `${period || '当前查询中'}有${keyword ? '匹配关键词的' : ''}${readable}，可以继续查看详情。`,
    ];
  }
  return pick(`query:${key}:${subject}:${empty}`, replies);
}

export function count(value: unknown) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) throw new Error('Invalid summary');
  return value;
}
