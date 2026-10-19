<template>
  <ModalDialog :open="open && !!user" :title="acknowledgements.title" size="sm" :backdrop-class="['credits-dialog-backdrop', backdropClass].filter(Boolean).join(' ')"
    :title-interactive="canEdit" @title-activate="activateTitle" @close="$emit('close')">
    <p class="credits-thanks">{{ acknowledgements.intro }}</p>
    <p v-if="loading" class="credits-notice" role="status">正在加载贡献信息</p>
    <div v-if="error" class="credits-notice" role="alert">{{ error }} <button class="button secondary small" type="button" @click="load">重试</button></div>
    <ol class="credits-people">
      <li v-for="(person, index) in people" :key="index" :class="{ 'credits-founder': index === 0 }">
        <strong>{{ person.name }}</strong><p>{{ person.contribution }}</p>
      </li>
    </ol>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useSession } from "../../app/session";
import { get } from "../api";
import { acknowledgements, type CreditEntry, type CreditsSetting } from "../config/acknowledgements";
import ModalDialog from "./ModalDialog.vue";
const props = defineProps<{ open: boolean; contributors?: CreditEntry[]; backdropClass?: string }>();
const emit = defineEmits<{ close: [] }>();
const router = useRouter();
const { user } = useSession();
const saved = ref<CreditEntry[]>([...acknowledgements.contributors]);
const people = computed(() => props.contributors ?? saved.value);
const isAdmin = computed(() => user.value?.role === "ADMIN");
const canEdit = computed(() => !props.contributors && isAdmin.value);
const loading = ref(false), error = ref("");
let controller: AbortController | undefined;
let clicks = 0, lastClick = 0;
let entering = false;
async function activateTitle() {
  if (!canEdit.value || entering) return;
  const now = Date.now(); clicks = now - lastClick > 2000 ? 1 : clicks + 1; lastClick = now;
  if (clicks < 5) return;
  clicks = 0; entering = true;
  try { const failure = await router.push({ name: "credits-editor" }); if (!failure) emit("close"); }
  finally { entering = false; }
}
async function load() {
  controller?.abort();
  if (!props.open || !user.value || props.contributors) return;
  const active = controller = new AbortController(); loading.value = true; error.value = "";
  try { const data = await get<CreditsSetting>("/api/settings/credits", { signal: active.signal }); if (!active.signal.aborted) saved.value = data.contributors; }
  catch (cause) { if (!active.signal.aborted) error.value = cause instanceof Error ? cause.message : "鸣谢加载失败"; }
  finally { if (controller === active) loading.value = false; }
}
watch([() => props.open, () => user.value?.id, () => user.value?.role], () => { clicks = 0; lastClick = 0; if (props.open && user.value) { saved.value = [...acknowledgements.contributors]; void load(); } else controller?.abort(); }, { immediate: true });
onBeforeUnmount(() => controller?.abort());
</script>
<style>
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell).modal-sm{--credits-accent:var(--mw-accent,var(--primary));position:relative;display:flex;flex-direction:column;width:min(560px,100%);border:1px solid color-mix(in srgb,var(--credits-accent) 12%,white);border-radius:22px;background:radial-gradient(ellipse 72% 60% at 50% 0%,color-mix(in srgb,var(--credits-accent) 7%,white),transparent 90%),white;isolation:isolate}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell)>:is(.mw-modal-head,.modal-header){position:relative;display:grid;grid-template-columns:minmax(0,1fr);justify-content:stretch;place-items:center;grid-column:auto;grid-row:auto;gap:0;padding:28px 56px 16px;min-height:0;border:0;background:transparent}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal-head,.modal-header)>div{position:relative;z-index:1;text-align:center}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell)>:is(.mw-modal-head,.modal-header) h2{margin:0;padding-left:.12em;font:500 26px/1.4 var(--mw-font,var(--font-sans));letter-spacing:.12em;text-align:center;color:var(--mw-ink,var(--ink-900));user-select:none}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell).modal-sm::after{content:"";position:absolute;right:-22px;bottom:-34px;width:210px;height:210px;background:url('/brand/ca-logo-transparent.svg') center/contain no-repeat;opacity:.06;pointer-events:none;z-index:-1}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal-head,.modal-header)>.icon-button{position:absolute;top:18px;right:18px;z-index:2}
html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell)>:is(.mw-modal-body,.modal-body){display:block;grid-column:auto;grid-row:auto;padding:2px 28px 22px;min-height:0;overflow:auto;font-family:var(--mw-font,var(--font-sans))}
html:root[data-appearance] .credits-dialog-backdrop .credits-thanks{max-width:430px;margin:0 auto 24px;color:var(--mw-ink,var(--ink-800));font:400 20px/1.8 var(--mw-font,var(--font-sans));text-align:center;text-wrap:pretty}
html:root[data-appearance] .credits-dialog-backdrop .credits-people{list-style:none;padding:0;margin:0;display:grid;gap:0}
html:root[data-appearance] .credits-dialog-backdrop .credits-people li{display:grid;grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);gap:18px;align-items:baseline;padding:16px 0;border-top:1px solid color-mix(in srgb,var(--credits-accent) 12%,white);overflow-wrap:anywhere}
html:root[data-appearance] .credits-dialog-backdrop .credits-people strong{display:block;font:400 14px/1.8 var(--mw-font,var(--font-sans));color:var(--mw-ink,var(--ink-800));text-align:left}
html:root[data-appearance] .credits-dialog-backdrop .credits-people p{margin:0;color:var(--mw-ink,var(--ink-800));font:400 14px/1.8 var(--mw-font,var(--font-sans));white-space:pre-wrap;text-align:right}
html:root[data-appearance] .credits-dialog-backdrop .credits-notice{font-size:13px;margin-bottom:18px;color:var(--mw-muted,var(--ink-600))}
@media(max-width:550px){html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell)>:is(.mw-modal-body,.modal-body){padding:2px 22px 20px}html:root[data-appearance] .credits-dialog-backdrop :is(.mw-modal,.modal-shell)>:is(.mw-modal-head,.modal-header){padding:26px 54px 16px}html:root[data-appearance] .credits-dialog-backdrop .credits-thanks{font-size:18px;margin-bottom:20px}html:root[data-appearance] .credits-dialog-backdrop .credits-people li{grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);gap:14px}html:root[data-appearance] .credits-dialog-backdrop .credits-people strong{font-size:13px}html:root[data-appearance] .credits-dialog-backdrop .credits-people p{font-size:13px}}
</style>
