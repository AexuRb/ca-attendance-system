import { get } from '../../../shared/api';
import type { ResolvedCommand } from '../../../features/command-center/commandTypes';
import { count } from './conversationReplies';

export interface QuerySummary {
  scope: string;
  facts: string;
  parts: Array<{ text: string; emphasis?: boolean }>;
  empty: boolean;
  key: string;
  subject: string;
  range: string;
  keyword: boolean;
}
function summary(context: Pick<QuerySummary, 'scope' | 'subject' | 'range' | 'keyword'>, parts: QuerySummary['parts'], empty: boolean, key: string): QuerySummary {
  return { ...context, parts, facts: parts.map(part => part.text).join(''), empty, key };
}

export async function querySummary(command: ResolvedCommand, signal: AbortSignal) {
  if (!/^\/ (查看|查找) /.test(command.canonical) || typeof command.target !== 'object') return null;
  const target = command.target as { name: string; query?: Record<string, string> };
  const q = target.query || {}, params = new URLSearchParams();
  for (const key of ['from', 'to', 'status', 'keyword']) if (q[key]) params.set(key, q[key]!);
  const scope = [q.from && q.to ? `${q.from} 至 ${q.to}` : '', q.keyword ? `关键词：“${q.keyword}”` : ''].filter(Boolean).join(' · ');
  const relativeRange = command.canonical.split(' ').slice(3).find(word => ['今天', '昨天', '本周', '上周', '本月', '上月', '本年'].includes(word));
  const range = q.from && q.to ? relativeRange || '这个日期范围内' : '';
  const status = ({ PENDING: '待审核', INCOMPLETE: '未签退', REPAIRING: '进行中', COMPLETED: '已完成', CANCELED: '已取消', ACTIVE: '启用', DISABLED: '停用' } as Record<string, string>)[q.status || ''] || '';
  if (target.name === 'stats' && q.from && q.to) {
    const rows = await get<Array<{ attendanceCount: number; trainingCount: number; totalHours: number }>>(`/api/stats/summary?${params}`, { signal });
    if (!Array.isArray(rows)) throw new Error('Invalid summary');
    const records = rows.reduce((sum, row) => sum + count(row.attendanceCount), 0);
    const trainings = rows.reduce((sum, row) => sum + count(row.trainingCount), 0);
    const hours = rows.reduce((sum, row) => sum + count(row.totalHours), 0);
    return summary({ scope, subject: '统计', range, keyword: Boolean(q.keyword) }, [
      { text: '值班 ' }, { text: `${records} 次`, emphasis: true },
      { text: '，培训参与 ' }, { text: `${trainings} 次`, emphasis: true },
      { text: '，合计有效时长 ' }, { text: `${Number(hours.toFixed(2))} 小时`, emphasis: true }, { text: '。' },
    ], !records && !trainings, 'stats');
  }
  const endpoints: Record<string, string> = { repairs: '/api/repairs', members: '/api/users/page', trainings: '/api/trainings/page', logs: '/api/logs' };
  let endpoint = endpoints[target.name];
  if (target.name === 'attendance' && q.from && q.to && !q.keyword) endpoint = '/api/attendance/page';
  if (!endpoint) return null; // Unsupported summary scopes are left to the existing detail page.
  params.set('page', '1'); params.set('pageSize', '1');
  const result = await get<{ total: number }>(`${endpoint}?${params}`, { signal });
  const total = count(result?.total);
  const noun = ({ attendance: '值班记录', repairs: '维修', members: '成员', trainings: '培训', logs: '操作日志' } as Record<string, string>)[target.name];
  const unit = target.name === 'members' ? '人' : target.name === 'trainings' ? '场' : target.name === 'repairs' ? '项' : '条';
  return summary({ scope, subject: status + noun, range, keyword: Boolean(q.keyword) }, [
    { text: `${status ? status + '的' : ''}${noun}共 ` }, { text: `${total} ${unit}`, emphasis: true }, { text: '。' },
  ], total === 0, target.name);
}
