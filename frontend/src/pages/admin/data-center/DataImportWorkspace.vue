<template>
  <section class="import-catalog" aria-label="数据导入工作区" :aria-busy="Boolean(busy)">
    <DataTypeCatalog v-if="!active" mode="import" :items="catalogItems" :busy="Boolean(busy)" @choose="chooseKind">
      <template #actions="{ item }"><button class="button secondary" data-button-kind="entry" type="button" :disabled="Boolean(busy)" :aria-label="`下载${item.label}模板`" @click="requestTemplate(item.id as ImportKind)"><Download aria-hidden="true" />模板</button></template>
    </DataTypeCatalog>
    <div v-if="active" class="import-catalog-list data-operation-workspace">
      <section v-for="kind in activeKinds" :key="kind.id" class="import-task is-open data-active-task" :aria-labelledby="`import-kind-${kind.id}`">
        <header class="data-operation-header">
          <div class="import-kind-identity"><span class="import-kind-icon"><component :is="kindIcons[kind.id]" aria-hidden="true" /></span><div><h2 :id="`import-kind-${kind.id}`">{{ kind.name }}</h2><p>{{ kind.detail }}</p></div></div>
          <button class="button secondary" data-button-kind="entry" type="button" :disabled="Boolean(busy)" @click="switchTask(kind.id)"><ArrowLeft aria-hidden="true" />更换数据类型</button>
        </header>
        <div v-if="active === kind.id" :id="`import-task-${kind.id}`" class="import-task-details">
          <template v-if="!complete">
            <section class="data-operation-config">
            <section v-if="kind.id === 'training'" class="import-training-target" aria-label="选择培训">
              <form class="import-session-search" @submit.prevent="searchSessions(1)"><label class="field"><span>查找培训</span><input v-model="sessionKeyword" type="search" placeholder="培训名称、地点或主讲人" :disabled="Boolean(busy)" /></label><button class="button" data-button-kind="action" :disabled="sessionLoading || Boolean(busy)" type="submit">查找</button></form>
              <div class="import-session-select"><label class="field"><span>导入到培训</span><select aria-label="导入到培训" :value="session?.id || ''" :disabled="Boolean(busy) || sessionLoading" @change="selectSession(($event.target as HTMLSelectElement).value)"><option value="">请选择培训</option><option v-if="session && !sessions.some(s => s.id === session?.id)" :value="session.id">{{ session.trainingDate }} · {{ session.title }}</option><option v-for="item in sessions" :key="item.id" :value="item.id">{{ item.trainingDate }} · {{ item.title }}</option></select></label><div class="import-session-pagination"><button class="icon-button" data-button-kind="icon" aria-label="上一页培训" :disabled="sessionPage <= 1 || sessionLoading || Boolean(busy)" @click="searchSessions(sessionPage - 1)"><ChevronLeft /></button><span>第 {{ sessionPage }} 页</span><button class="icon-button" data-button-kind="icon" aria-label="下一页培训" :disabled="!sessionHasMore || sessionLoading || Boolean(busy)" @click="searchSessions(sessionPage + 1)"><ChevronRight /></button></div></div>
              <p v-if="sessionError" class="form-error" role="alert">{{ sessionError }} <button class="button text" @click="searchSessions(sessionPage)">重试</button></p>
              <p v-else-if="sessionLoading" class="import-note" role="status">正在加载培训…</p><p v-else-if="!sessions.length" class="import-note">没有找到培训，可更换关键词后重试。</p>
              <p v-if="session" class="import-note">更换培训会清除已选文件与校验结果。</p>
            </section>
            <div class="import-file-toolbar"><span>{{ kind.id === 'schedules' ? '.xlsx' : '.xlsx / .xls' }} · 不超过 5 MB · {{ kind.limit }}</span><button class="button secondary" data-button-kind="entry" type="button" :disabled="Boolean(busy)" :aria-label="`下载${kind.name}模板`" @click="requestTemplate(kind.id)"><Download aria-hidden="true" />下载模板</button></div>
            <div class="import-file-line">
              <div v-if="file" class="import-file-card"><FileSpreadsheet aria-hidden="true" /><div><strong :title="file.name">{{ file.name }}</strong><small>{{ formatSize(file.size) }} · {{ review ? '已校验' : '等待校验' }}</small></div><button class="icon-button" data-button-kind="icon" type="button" aria-label="移除已选文件" :disabled="Boolean(busy)" @click="clearFile"><X /></button></div>
              <label v-else class="import-dropzone" :class="{ 'is-disabled': Boolean(busy) || !hasTarget }" @dragover.prevent @drop.prevent="dropFile">
                <Upload aria-hidden="true" /><span><strong>选择 Excel 文件</strong><small>{{ !hasTarget ? '请先选择培训' : '点击选择，或将文件拖入此处' }}</small></span><input :key="`${kind.id}-${session?.id || 0}`" type="file" :accept="kind.id === 'schedules' ? '.xlsx' : '.xlsx,.xls'" :disabled="Boolean(busy) || !hasTarget" aria-label="选择导入文件" @change="pickFile" />
              </label>
              <div class="import-file-actions"><label v-if="file" class="button secondary import-replace" data-button-kind="entry" :aria-disabled="Boolean(busy)">更换文件<input type="file" :accept="kind.id === 'schedules' ? '.xlsx' : '.xlsx,.xls'" :disabled="Boolean(busy)" aria-label="更换导入文件" @change="pickFile" /></label><button class="button" data-button-kind="action" type="button" :disabled="!canValidate" @click="validate"><LoaderCircle v-if="busy === 'validate'" class="spin" aria-hidden="true" /><FileSearch v-else aria-hidden="true" />{{ busy === 'validate' ? '正在校验' : review ? '重新校验' : '校验文件' }}</button></div>
            </div>
            <p v-if="error" class="form-error import-error" role="alert">{{ error }}</p>
            <p class="data-config-note">{{ kind.rule }}</p>
            </section>
            <section class="import-review data-preview-surface" aria-label="校验结果" aria-live="polite">
              <header v-if="review" class="import-review-heading data-table-toolbar"><div><h3>结果预览 <small>共 {{ review.total }} 条{{ review.rows.length > 50 ? ' · 预览前 50 条有效记录' : '' }}</small></h3><p>{{ review.summary }}{{ review.issueCount ? `，${review.issueCount} 行或项未通过` : '' }}。</p></div><span :class="review.valid ? 'import-success-text' : 'import-error-text'"><ShieldCheck v-if="review.valid" aria-hidden="true" /><TriangleAlert v-else aria-hidden="true" />{{ review.valid ? '校验通过' : review.issueCount ? '需要修正' : '无有效记录' }}</span></header>
              <header v-else class="data-table-toolbar is-pending"><h3>结果预览</h3><p class="data-preview-status" role="status">{{ busy === 'validate' ? '正在校验…' : '待校验文件' }}</p></header>
              <template v-if="review">
              <ul v-if="review.warnings.length" class="import-warning-list"><li v-for="(warning,index) in review.warnings" :key="index">{{ warning }}</li></ul>
              <div v-if="review.issueCount" class="import-review-tools"><label class="import-only-errors"><input v-model="onlyErrors" type="checkbox" />仅看问题</label></div>
              <div v-if="review.errors.length" class="import-issues" role="alert"><p v-for="(issue,index) in review.errors" :key="index">{{ issue }}</p><p v-if="review.issueCount > review.errors.length">当前展示前 {{ review.errors.length }} 条错误，请修正后重新校验。</p></div>
              <div v-if="!onlyErrors && review.rows.length" class="import-preview-scroll data-preview-table" tabindex="0" role="region" aria-label="导入内容预览，可横向滚动"><table><thead><tr><th v-for="column in review.columns" :key="column" scope="col">{{ column }}</th></tr></thead><tbody><tr v-for="(row,rowIndex) in review.rows.slice(0,50)" :key="rowIndex"><td v-for="(cell,index) in row" :key="index">{{ cell }}</td></tr></tbody></table></div>
              </template>
              <footer class="import-task-footer data-operation-footer"><button class="button primary" data-button-kind="action" type="button" :disabled="!canImport" @click="confirmOpen = true"><Upload aria-hidden="true" />{{ busy === 'import' ? '正在导入' : '确认导入' }}</button></footer>
            </section>
          </template>
          <section v-else class="import-complete" role="status"><span class="import-complete-icon"><Check aria-hidden="true" /></span><div><h4>导入完成</h4><p>{{ complete }}</p><p class="import-note">{{ targetLabel }}</p><ul v-if="completionWarnings.length"><li v-for="(warning,index) in completionWarnings" :key="index">{{ warning }}</li></ul></div><button class="button secondary" data-button-kind="entry" @click="clearFile">继续导入</button></section>
        </div>
      </section>
    </div>
    <p v-if="error && !active" class="form-error" role="alert">{{ error }}</p>
    <ConfirmDialog :open="confirmOpen" title="确认整批导入" :message="`${targetLabel}：${review?.summary || ''}。${config?.effect || ''}。`" confirm-label="确认导入" pending-label="正在导入" :pending="busy === 'import'" @cancel="confirmOpen = false" @confirm="confirmImport" />
    <ConfirmDialog :open="unsaved.confirmOpen.value" title="放弃当前导入任务？" message="已选文件和校验结果将被清除，原始文件不会改变。" confirm-label="放弃并继续" @cancel="unsaved.cancel" @confirm="unsaved.discard" />
  </section>
</template>

<script setup lang="ts">
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from "vue-router";
import { computed, onMounted } from "vue";
import { ArrowLeft, UsersRound, CalendarDays, GraduationCap, Download, Upload, FileSpreadsheet, FileSearch, X, Check, ShieldCheck, TriangleAlert, LoaderCircle, ChevronLeft, ChevronRight } from "@lucide/vue";
import DataTypeCatalog from "./DataTypeCatalog.vue";
import ConfirmDialog from "../../../shared/ui/ConfirmDialog.vue";
import { useUnsavedChanges } from "../../../shared/composables/useUnsavedChanges";
import { importKinds, type ImportKind } from "../../../features/maintenance/dataImport";
import { useDataImportWorkspace } from "../../../features/maintenance/useDataImportWorkspace";
const kindIcons = { members: UsersRound, schedules: CalendarDays, training: GraduationCap };
const { active, config, file, review, error, busy, complete, completionWarnings, confirmOpen, onlyErrors, session, sessions, sessionKeyword, sessionPage, sessionHasMore, sessionLoading, sessionError, hasTarget, targetLabel, canValidate, canImport, clearFile, openTask, chooseFile, selectSession, validate, confirmImport, template, searchSessions } = useDataImportWorkspace();
const catalogItems = importKinds.map(kind => ({ id: kind.id, label: kind.name, description: kind.detail }));
const activeKinds = computed(() => importKinds.filter(kind => kind.id === active.value));
function chooseKind(id: string) { if (id === 'members' || id === 'schedules' || id === 'training') switchTask(id); }
const route = useRoute();
const unsaved = useUnsavedChanges(() => Boolean(file.value));
function requestTemplate(kind: ImportKind) {
  if (busy.value) return;
  if (kind === "training" && active.value !== "training") unsaved.request(() => { void template(kind); });
  else void template(kind);
}
function switchTask(kind: ImportKind) { if (!busy.value) unsaved.request(() => openTask(kind)); }
function pickFile(event: Event) { const input = event.target as HTMLInputElement; chooseFile(input.files?.[0] || null); input.value = ""; }
function dropFile(event: DragEvent) { if (hasTarget.value) chooseFile(event.dataTransfer?.files[0] || null); }
function formatSize(size: number) { return size < 1024 ? `${size} B` : `${(size / 1024).toFixed(1)} KB`; }
function leave() { if (busy.value) return false; return new Promise<boolean>(resolve => unsaved.request(() => resolve(true), () => resolve(false))); }
onBeforeRouteLeave(leave);
onBeforeRouteUpdate((to, from) => to.query.tab === from.query.tab ? true : leave());
onMounted(() => { const kind = route.query.kind; if (kind === 'members' || kind === 'schedules' || kind === 'training') openTask(kind); });
</script>
