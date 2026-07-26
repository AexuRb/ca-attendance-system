<template>
  <ModalDialog :open="open" title="批量导入排班" size="lg" @close="close">
    <div class="schedule-import-workspace">
      <label class="field">
        <span>Excel 文件</span>
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          @change="selectFile"
        />
      </label>
      <button
        class="button secondary"
        :disabled="busy || !file"
        @click="previewFile"
      >
        <FileSearch />校验预览
      </button>
    </div>

    <div
      v-if="preview"
      class="schedule-import-result"
      :class="{ invalid: !preview.valid }"
    >
      <div class="schedule-import-summary">
        <div>
          <span>排班分组</span><strong>{{ preview.groupCount }}</strong>
        </div>
        <div>
          <span>值班人员</span><strong>{{ preview.memberCount }}</strong>
        </div>
        <div>
          <span>校验结果</span
          ><strong>{{
            preview.valid ? "通过" : `${preview.issues.length} 处错误`
          }}</strong>
        </div>
      </div>
      <div
        v-if="preview.issues.length"
        class="schedule-import-issues"
        role="alert"
      >
        <p
          v-for="(issue, index) in preview.issues"
          :key="`${issue.row}-${index}`"
        >
          <TriangleAlert />{{ issue.row ? `第 ${issue.row} 行：` : ""
          }}{{ issue.message }}
        </p>
      </div>
      <div v-if="preview.groups.length" class="schedule-import-groups">
        <article
          v-for="group in preview.groups"
          :key="`${group.weekday}-${group.startTime}`"
        >
          <header>
            <strong
              >{{ group.weekdayName }} · {{ group.startTime }}-{{
                group.endTime
              }}</strong
            >
            <span>{{ group.members.length }} 人</span>
          </header>
          <p>
            {{ group.members.map((member: any) => member.name).join("、") }}
          </p>
        </article>
      </div>
    </div>

    <template #footer>
      <button class="button secondary" @click="close">取消</button>
      <button
        class="button primary"
        :disabled="busy || !file || !preview?.valid"
        @click="confirmImport"
      >
        <Upload />确认导入
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { FileSearch, TriangleAlert, Upload } from "@lucide/vue";
import ModalDialog from "../../../shared/ui/ModalDialog.vue";
import { post } from "../../../shared/api";
import { notify } from "../../../shared/composables/useToast";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; imported: [] }>();
const fileInput = ref<HTMLInputElement>();
const file = ref<File | null>(null);
const preview = ref<any>(null);
const busy = ref(false);

watch(
  () => props.open,
  (value) => {
    if (!value) reset();
  },
);

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null;
  preview.value = null;
}

async function previewFile() {
  if (!file.value) return;
  busy.value = true;
  try {
    const body = new FormData();
    body.append("file", file.value);
    preview.value = await post("/api/schedules/import/preview", body);
    notify(
      preview.value.valid ? "文件校验通过" : "请先修正导入文件中的错误",
      preview.value.valid ? "success" : "warning",
    );
  } catch (cause) {
    notify(
      cause instanceof Error ? cause.message : "排班文件校验失败",
      "error",
    );
  } finally {
    busy.value = false;
  }
}

async function confirmImport() {
  if (!file.value || !preview.value?.valid) return;
  busy.value = true;
  try {
    const body = new FormData();
    body.append("file", file.value);
    const result = await post<any>("/api/schedules/import", body);
    notify(
      `已导入 ${result.replacedGroups} 个时段、${result.assignedMembers} 人`,
      "success",
    );
    emit("imported");
    emit("close");
  } catch (cause) {
    notify(cause instanceof Error ? cause.message : "排班导入失败", "error");
  } finally {
    busy.value = false;
  }
}

function close() {
  emit("close");
}

function reset() {
  file.value = null;
  preview.value = null;
  if (fileInput.value) fileInput.value.value = "";
}
</script>
