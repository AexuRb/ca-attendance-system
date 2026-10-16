import { afterEach, describe, expect, it, vi } from 'vitest';
import { commandSuggestions } from '../../../features/command-center/commandCatalog';
import { resolveCommand } from '../../../features/command-center/commandParser';
import { createReplyPicker, describeOperation, initialReply } from './conversationReplies';

afterEach(() => vi.restoreAllMocks());
describe('conversation copy', () => {
  it('provides three non-repeating replies for every legal catalog operation', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0);
    for (const item of commandSuggestions) {
      const command = resolveCommand(item.command, 'ADMIN', new Date(2026, 9, 10));
      if (command.kind !== 'resolved') continue;
      const pick = createReplyPicker();
      const replies = Array.from({ length: 3 }, () => describeOperation(command, pick));
      expect(new Set(replies.map(r => r.text)).size, item.command).toBe(3);
      expect(new Set(replies.map(r => r.button)).size).toBe(1);
      expect(replies.every(r => r.text && r.button)).toBe(true);
    }
  });
  it.each([[0, 0], [2, 0], [0, 4], [2, 4]])('covers each zero/nonzero entry state (%s,%s)', (pending, repairs) => {
    vi.spyOn(Math, 'random').mockReturnValue(0);
    const pick = createReplyPicker();
    const messages = Array.from({ length: 3 }, () => initialReply({ todayPendingCount: pending, ongoingRepairCount: repairs }, pick));
    expect(new Set(messages).size).toBe(3);
    messages.forEach(text => {
      expect(text).toContain('维修');
      expect(text).toMatch(/审核/);
      if (pending) expect(text).toContain(String(pending));
      if (repairs) expect(text).toContain(String(repairs));
    });
  });
  it('never represents missing counts as zero', () => {
    expect(() => initialReply({}, createReplyPicker())).toThrow();
  });
  it.each([['成员', 'members'], ['培训', 'trainings']])('uses the actual new %s form rather than a backup fallback', (object, route) => {
    const command = resolveCommand(`/ 新建 ${object}`, 'ADMIN');
    if (command.kind !== 'resolved') throw new Error('Invalid test command');
    expect(command.target).toMatchObject({ name: route, query: { intent: 'new' } });
    const reply = describeOperation(command, (_key, choices) => {
      choices.forEach(text => { expect(text).toContain(object); expect(text).not.toContain('备份'); });
      return choices[0]!;
    });
    expect(reply.button).toBe(`填写${object}`);
  });
});
