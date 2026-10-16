<template>
  <AuthLayout class="credits-editor-page" :entry-mode="state.access.mode" large-brand>
    <template #header-actions><button class="button secondary" data-button-kind="entry" type="button" :disabled="busy" @click="router.push({ name: 'settings' })">返回系统设置</button></template>
    <section class="credits-editor" aria-labelledby="credits-editor-title">
      <header class="credits-editor-heading"><div><h1 id="credits-editor-title">编辑鸣谢</h1><p>{{ acknowledgements.intro }}</p></div></header>
      <div v-if="loading" class="credits-editor-state" role="status">正在加载贡献信息</div>
      <div v-else-if="loadError" class="credits-editor-state" role="alert">{{ loadError }}<button class="button secondary" type="button" @click="load">重新加载</button></div>
      <form v-else @submit.prevent="save" novalidate>
        <div class="credits-editor-list">
          <section class="credits-editor-founder" aria-label="固定贡献信息"><header><span>首位贡献者</span><span><LockKeyhole aria-hidden="true" />固定保留</span></header><dl><div><dt>职务-姓名</dt><dd>{{ fixedContributor.name }}</dd></div><div><dt>贡献</dt><dd>{{ fixedContributor.contribution }}</dd></div></dl></section>
          <section v-for="(entry, index) in entries" :key="entry.key" class="credits-editor-entry" :aria-label="`贡献信息 ${index + 2}`">
            <header><span>贡献信息 {{ index + 2 }}</span><div class="credits-row-actions">
              <button class="icon-button" data-button-kind="icon" type="button" :disabled="busy || index === 0" :aria-label="`上移第${index + 2}条`" @click="move(index, -1)"><ArrowUp /></button>
              <button class="icon-button" data-button-kind="icon" type="button" :disabled="busy || index === entries.length - 1" :aria-label="`下移第${index + 2}条`" @click="move(index, 1)"><ArrowDown /></button>
              <button class="icon-button danger" data-button-kind="icon" type="button" :disabled="busy" :aria-label="`删除第${index + 2}条`" @click="deleting = entry.key"><Trash2 /></button>
            </div></header>
            <label class="credits-editor-field" :for="`credit-name-${entry.key}`">职务-姓名<input :id="`credit-name-${entry.key}`" v-model="entry.name" :disabled="busy" maxlength="100" autocomplete="off" placeholder="例如：2026 会长-姓名" required /></label>
            <label class="credits-editor-field" :for="`credit-contribution-${entry.key}`">贡献<textarea :id="`credit-contribution-${entry.key}`" v-model="entry.contribution" :disabled="busy" maxlength="1000" rows="2" placeholder="记录参与的工作" required /></label>
          </section>
          <button class="button secondary credits-add" data-button-kind="soft" type="button" :disabled="busy || entries.length >= 199" @click="add"><Plus />新增贡献信息</button>
        </div>
        <footer class="credits-editor-footer"><p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-else-if="notice" class="credits-saved" role="status">{{ notice }}</p><div><button class="button secondary" data-button-kind="entry" type="button" :disabled="busy" @click="preview = true">预览</button><button class="button primary" data-button-kind="action" type="submit" :disabled="busy || !dirty || !valid"><Save />{{ busy ? '正在保存' : '保存修改' }}</button></div></footer>
      </form>
    </section>
    <CreditsDialog :open="preview" :contributors="contributors" @close="preview = false" />
    <ConfirmDialog :open="deleting !== null" title="删除贡献信息" message="这条信息将从当前名单移除，保存修改后生效。" confirm-label="删除" danger @cancel="deleting = null" @confirm="remove" />
    <ConfirmDialog :open="unsaved.confirmOpen.value" title="放弃未保存修改" message="鸣谢还有未保存的修改，离开后将无法恢复。" confirm-label="放弃修改" danger @cancel="unsaved.cancel" @confirm="unsaved.discard" />
  </AuthLayout>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import { ArrowDown, ArrowUp, LockKeyhole, Plus, Save, Trash2 } from "@lucide/vue";
import AuthLayout from "../../layouts/AuthLayout.vue";
import { useSession } from "../../app/session";
import { get, put } from "../../shared/api";
import { acknowledgements, fixedContributor, type CreditEntry, type CreditsSetting } from "../../shared/config/acknowledgements";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import CreditsDialog from "../../shared/ui/CreditsDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
const router = useRouter();
const { state } = useSession();
const entries = ref<(CreditEntry & { key: number })[]>([]);
const revision = ref(""), baseline = ref(""), error = ref(""), loadError = ref(""), notice = ref("");
const busy = ref(false), loading = ref(true), preview = ref(false), deleting = ref<number | null>(null);
let sequence = 0;
const controller = new AbortController();
const contributors = computed<CreditEntry[]>(() => [fixedContributor, ...entries.value.map(({ name, contribution }) => ({ name, contribution }))]);
const snapshot = () => JSON.stringify(contributors.value);
const dirty = computed(() => Boolean(baseline.value) && snapshot() !== baseline.value);
const valid = computed(() => entries.value.every(e => e.name.trim() && e.contribution.trim()));
const unsaved = useUnsavedChanges(() => dirty.value || busy.value);
async function load() {
  loading.value = true; loadError.value = "";
  try { const data = await get<CreditsSetting>("/api/settings/credits", { signal: controller.signal }); if (controller.signal.aborted) return; apply(data); }
  catch (cause) { if (!controller.signal.aborted) loadError.value = cause instanceof Error ? cause.message : "鸣谢加载失败"; }
  finally { loading.value = false; }
}
function apply(data: CreditsSetting) { entries.value = data.contributors.slice(1).map(e => ({ ...e, key: ++sequence })); revision.value = data.revision; baseline.value = snapshot(); }
async function add() { if (busy.value || entries.value.length >= 199) return; const key = ++sequence; entries.value.push({ key, name: "", contribution: "" }); error.value = ""; notice.value = ""; await nextTick(); document.getElementById(`credit-name-${key}`)?.focus(); }
function move(index: number, offset: number) { if (busy.value || index + offset < 0 || index + offset >= entries.value.length) return; const entry = entries.value.splice(index, 1)[0]!; entries.value.splice(index + offset, 0, entry); notice.value = ""; }
function remove() { entries.value = entries.value.filter(e => e.key !== deleting.value); deleting.value = null; notice.value = ""; }
async function save() {
  if (busy.value || !dirty.value || !valid.value) return;
  busy.value = true; error.value = ""; notice.value = "";
  try { const data = await put<CreditsSetting>("/api/settings/credits", { intro: acknowledgements.intro, contributors: contributors.value, revision: revision.value }); apply(data); notice.value = "鸣谢已保存"; }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "鸣谢保存失败，当前输入已保留"; }
  finally { busy.value = false; }
}
onMounted(load);
onBeforeUnmount(() => controller.abort());
onBeforeRouteLeave(() => { if (busy.value) return false; return new Promise<boolean>(resolve => unsaved.request(() => resolve(true), () => resolve(false))); });
</script>
<style src="../../features/settings/credits-editor.css"></style>
