import { afterEach, expect, it, vi } from 'vitest';
import { resolveCommand } from '../../../features/command-center/commandParser';
import { querySummary } from './conversationQuery';
import { summaryReply } from './conversationReplies';
const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../shared/api', () => ({ get: mocks.get }));
afterEach(() => mocks.get.mockReset());
it.each([
  ['/ 查看 值班记录 待审核 今天', '/api/attendance/page', 'status=PENDING', '待审核的值班记录'],
  ['/ 查看 成员名册 停用', '/api/users/page', 'status=DISABLED', '停用的成员'],
  ['/ 查看 操作日志 本周', '/api/logs', 'from=2026-10-05', '操作日志'],
  ['/ 查找 维修事务 "演示关键词"', '/api/repairs', 'keyword=', '维修'],
  ['/ 查看 培训记录 本月', '/api/trainings/page', 'from=2026-10-01', '培训'],
  ['/ 查看 维修事务 已取消 本月', '/api/repairs', 'status=CANCELED', '已取消的维修'],
])('uses the exact existing read-only scope for %s', async (input, endpoint, query, copy) => {
  const command = resolveCommand(input, 'ADMIN', new Date(2026, 9, 10));
  if (command.kind !== 'resolved') throw new Error('Invalid test command');
  mocks.get.mockResolvedValue({ total: 7 });
  const result = await querySummary(command, new AbortController().signal);
  expect(mocks.get.mock.calls[0]![0]).toContain(endpoint + '?');
  expect(mocks.get.mock.calls[0]![0]).toContain(query);
  expect(mocks.get.mock.calls[0]![0]).toContain('pageSize=1');
  expect(result?.facts).toContain(copy);
  expect(result?.facts).toContain('7');
});
it('keeps the literal keyword in the scope and original request', async () => {
  const keyword = '<img src=x onerror=alert(1)>';
  const command = resolveCommand(`/ 查找 成员名册 "${keyword}"`, 'ADMIN');
  if (command.kind !== 'resolved') throw new Error('Invalid test command');
  mocks.get.mockResolvedValue({ total: 0 });
  const result = await querySummary(command, new AbortController().signal);
  expect(result?.scope).toBe(`关键词：“${keyword}”`);
  expect(new URL(mocks.get.mock.calls[0]![0], 'http://localhost').searchParams.get('keyword')).toBe(keyword);
  expect(result?.empty).toBe(true);
});
it('distinguishes a scoped zero result from a failed or invalid response', async () => {
  const command = resolveCommand('/ 查看 维修事务 已完成 2026-09-01..2026-09-30', 'ADMIN');
  if (command.kind !== 'resolved') throw new Error('Invalid test command');
  mocks.get.mockResolvedValueOnce({ total: 0 });
  const result = await querySummary(command, new AbortController().signal);
  expect(result).toMatchObject({ scope: '2026-09-01 至 2026-09-30', subject: '已完成维修', range: '这个日期范围内', empty: true });
  summaryReply(result!, (_key, choices) => {
    expect(choices[0]).toBe('这个日期范围内没有已完成的维修记录。');
    choices.forEach(text => expect(text).toContain('已完成的维修'));
    return choices[0]!;
  });
  mocks.get.mockRejectedValueOnce(new Error('Offline'));
  await expect(querySummary(command, new AbortController().signal)).rejects.toThrow('Offline');
  mocks.get.mockResolvedValueOnce({});
  await expect(querySummary(command, new AbortController().signal)).rejects.toThrow('Invalid summary');
});
it('does not broaden an unsupported attendance keyword or absent date range', async () => {
  for (const input of ['/ 查找 值班记录 "演示关键词"', '/ 查看 值班记录 待审核']) {
    const command = resolveCommand(input, 'ADMIN');
    if (command.kind !== 'resolved') throw new Error('Invalid test command');
    expect(await querySummary(command, new AbortController().signal)).toBeNull();
  }
  expect(mocks.get).not.toHaveBeenCalled();
});
