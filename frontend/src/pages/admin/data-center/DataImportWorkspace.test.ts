// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DataImportWorkspace from "./DataImportWorkspace.vue";
import { reviewMembers, reviewSchedules, reviewTraining } from "../../../features/maintenance/dataImport";
const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), download: vi.fn(), leave: vi.fn(), update: vi.fn(), query: {} as Record<string,string> }));
vi.mock("../../../shared/api", () => ({ get: mocks.get, post: mocks.post, downloadBlob: mocks.download }));
vi.mock("vue-router", () => ({ useRoute: () => ({ query: mocks.query }), onBeforeRouteLeave: mocks.leave, onBeforeRouteUpdate: mocks.update }));
const training = { id: 4, title: "培训甲", trainingDate: "2026-10-15" };
const memberPreview = { valid: true, created: 1, updated: 0, errorCount: 0, errors: [], warnings: [], token: "preview-revision", changes: [{ studentNo: "90001", name: "测试成员", action: "新增", fields: [] }] };
const wrappers: ReturnType<typeof mount>[] = [];
function render() { const w = mount(DataImportWorkspace, { global: { stubs: { ConfirmDialog: { props: ["open", "message", "pending"], template: `<div v-if="open" class="test-confirm"><p>{{ message }}</p><button :disabled="pending" @click="$emit('confirm')">提交确认</button><button @click="$emit('cancel')">取消确认</button></div>` } } } }); wrappers.push(w); return w; }
async function open(w: ReturnType<typeof render>, index = 0) { await w.findAll('.import-task-summary .import-task-actions button').at(index * 2 + 1)!.trigger('click'); await flushPromises(); }
async function file(w: ReturnType<typeof render>, name = "导入.xlsx") { const input = w.get('input[type=file]'); Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(["sheet"], name)] }); await input.trigger('change'); }
async function validate(w: ReturnType<typeof render>) { await w.get('.import-file-actions button').trigger('click'); await flushPromises(); }
beforeEach(() => { Object.keys(mocks.query).forEach(key => delete mocks.query[key]); vi.clearAllMocks(); mocks.get.mockResolvedValue({ items: [training, { ...training, id: 5, title: "培训乙" }], page: 1, hasMore: false }); mocks.post.mockResolvedValue(memberPreview); });
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); });
describe('data import catalog', () => {
  it('downloads the member template from the authenticated backend', async () => {
    const w = render(); mocks.get.mockResolvedValue(new Blob(['template']));
    await w.get('[aria-label="下载成员名册模板"]').trigger('click'); await flushPromises();
    expect(mocks.get).toHaveBeenCalledWith('/api/users/import-template');
    expect(mocks.download).toHaveBeenCalledTimes(1);
  });
  it('starts with three task entries and no file or API request', () => { const w = render(); expect(w.findAll('.import-task')).toHaveLength(3); expect(w.find('input[type=file]').exists()).toBe(false); expect(mocks.get).not.toHaveBeenCalled(); });
  it('requires a valid preview and submits the reviewed file with member revision', async () => {
    const w = render(); await open(w); expect(w.get('.import-task-footer button').attributes('disabled')).toBeDefined(); await file(w); await validate(w);
    expect(w.get('.import-task-footer button').attributes('disabled')).toBeUndefined(); await w.get('.import-task-footer button').trigger('click');
    mocks.post.mockResolvedValue({ created: 1, updated: 0, skipped: 0, errors: [] }); await w.get('.test-confirm button').trigger('click'); await flushPromises();
    const [url, body] = mocks.post.mock.calls.at(-1)!; expect(url).toBe('/api/users/import'); expect(body.get('previewToken')).toBe('preview-revision'); expect(body.get('file').name).toBe('导入.xlsx'); expect(w.text()).toContain('导入完成'); expect(w.find('input[type=file]').exists()).toBe(false);
  });
  it('blocks an invalid batch but preserves warnings, errors and filename', async () => {
    mocks.post.mockResolvedValue({ ...memberPreview, valid: false, errorCount: 1, errors: ['第 3 行：缺少学号'], warnings: ['管理员行将跳过'] });
    const w = render(); await open(w); await file(w); await validate(w); expect(w.text()).toContain('缺少学号'); expect(w.text()).toContain('管理员行将跳过'); expect(w.get('.import-task-footer button').attributes('disabled')).toBeDefined(); expect(w.text()).toContain('导入.xlsx');
  });
  it('retains selected file on failed submit and requires fresh validation', async () => {
    const w = render(); await open(w); await file(w); await validate(w); await w.get('.import-task-footer button').trigger('click'); mocks.post.mockRejectedValueOnce(new Error('数据已变化，请重新校验')); await w.get('.test-confirm button').trigger('click'); await flushPromises(); expect(w.text()).toContain('导入.xlsx'); expect(w.text()).toContain('数据已变化'); expect(w.find('.import-preview-scroll').exists()).toBe(false); expect(w.get('.import-task-footer button').attributes('disabled')).toBeDefined();
  });
  it('replacing file discards the old preview and token', async () => { const w = render(); await open(w); await file(w); await validate(w); await file(w, '新文件.xlsx'); expect(w.find('.import-preview-scroll').exists()).toBe(false); expect(w.get('.import-task-footer button').attributes('disabled')).toBeDefined(); });
  it('rejects xls schedule files before making a request', async () => { const w = render(); await open(w, 1); await file(w, '排班.xls'); expect(w.text()).toContain('仅支持 .xlsx'); expect(mocks.post).not.toHaveBeenCalled(); });
  it('requires a training and clears reviewed file when target changes', async () => {
    const w = render(); await open(w, 2); expect(w.get('input[type=file]').attributes('disabled')).toBeDefined(); await w.get('select').setValue('4'); await file(w); mocks.post.mockResolvedValue({ valid: true, sourceRows: 1, errorCount: 0, errors: [], rows: [{ row: 2, studentNo: '90001', name: '测试成员', durationHours: 2 }] }); await validate(w); expect(mocks.post.mock.calls.at(-1)![0]).toBe('/api/trainings/4/participants/import/preview'); await w.get('select').setValue('5'); expect(w.find('.import-preview-scroll').exists()).toBe(false); expect(w.find('.import-file-card').exists()).toBe(false);
  });
  it('does not download a training template before target selection', async () => { const w = render(); await w.get('[aria-label="下载培训参与名单模板"]').trigger('click'); await flushPromises(); expect(w.text()).toContain('请先选择培训'); expect(mocks.download).not.toHaveBeenCalled(); await w.get('select').setValue('4'); mocks.get.mockResolvedValue(new Blob(['template'])); await w.get('[aria-label="下载培训参与名单模板"]').trigger('click'); await flushPromises(); expect(mocks.get).toHaveBeenLastCalledWith('/api/trainings/4/participants/import-template'); expect(mocks.download).toHaveBeenCalledTimes(1); });
  it('locks duplicate preview requests and file changes while busy', async () => { let finish!: (value: unknown) => void; mocks.post.mockReturnValue(new Promise(resolve => { finish = resolve; })); const w = render(); await open(w); await file(w); await w.get('.import-file-actions button').trigger('click'); await w.get('.import-file-actions button').trigger('click'); expect(mocks.post).toHaveBeenCalledTimes(1); expect(w.get('[aria-label="移除已选文件"]').attributes('disabled')).toBeDefined(); finish(memberPreview); await flushPromises(); });
  it('asks before changing the type and retains the selected file when cancelled', async () => {
    const w = render(); await open(w); await file(w);
    expect(w.findAll('.import-task')).toHaveLength(1);
    await w.get('.data-operation-header button').trigger('click');
    expect(w.find('.test-confirm').exists()).toBe(true);
    expect(w.text()).toContain('导入.xlsx');
    await w.findAll('.test-confirm button')[1].trigger('click');
    expect(w.find('.import-file-card').exists()).toBe(true);
    await w.get('.data-operation-header button').trigger('click');
    await w.get('.test-confirm button').trigger('click'); await flushPromises();
    expect(w.find('.import-file-card').exists()).toBe(false);
    expect(w.findAll('.import-task')).toHaveLength(3);
    await w.get('[aria-label="下载培训参与名单模板"]').trigger('click'); await flushPromises();
    expect(w.find('.import-training-target').exists()).toBe(true);
    expect(w.text()).toContain('请先选择培训');
  });
  it('does not treat an empty preview as importable across all three types', () => {
    expect(reviewMembers({ ...memberPreview, created: 0, changes: [] }).valid).toBe(false);
    expect(reviewSchedules({ valid: true, sourceRows: 0, groupCount: 0, memberCount: 0, groups: [], issues: [] }).valid).toBe(false);
    expect(reviewTraining({ valid: true, sourceRows: 0, errorCount: 0, errors: [], rows: [] }).valid).toBe(false);
  });
});


describe("import command handoff", () => {
  it.each(["members", "schedules", "training"])("opens the %s task directly without selecting a file or submitting", async kind => {
    mocks.query.kind=kind;
    const wrapper=render(); await flushPromises();
    expect(wrapper.findAll(".import-task")).toHaveLength(1);
    expect(wrapper.get(".import-task").attributes("aria-labelledby")).toBe(`import-kind-${kind}`);
    expect(wrapper.find('input[type="file"]').exists()).toBe(true);
    expect(wrapper.find(".import-file-card").exists()).toBe(false);
    if(kind === "training") expect(wrapper.get('input[type="file"]').attributes("disabled")).toBeDefined();
    expect(mocks.post).not.toHaveBeenCalled(); expect(mocks.download).not.toHaveBeenCalled();
  });
});
