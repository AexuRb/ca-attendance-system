<template>
  <div class="repair-active-region">
    <div v-if="items.length" class="repair-active-grid">
      <article
        v-for="item in items"
        :key="item.id"
        class="repair-active-card"
        :class="{ 'is-long-running': isLongRunningRepair(item) }"
      >
        <header>
          <span class="case-no">{{ item.caseNo }}</span>
          <span
            class="repair-age"
            :data-attention="isLongRunningRepair(item)"
          >
            <Clock3 aria-hidden="true" />{{ repairAgeLabel(item) }}
          </span>
        </header>

        <button class="repair-card-main" type="button" @click="$emit('view', item)">
          <span class="repair-card-device">{{ repairDeviceName(item) }}</span>
          <span class="repair-card-fault">{{ item.faultDescription }}</span>
        </button>

        <dl class="repair-active-facts">
          <div>
            <dt>负责人</dt>
            <dd>{{ item.handlerName || "待分配" }}</dd>
          </div>
          <div>
            <dt>受理时间</dt>
            <dd>{{ repairDateTime(item.receivedAt) }}</dd>
          </div>
          <div class="repair-active-contact">
            <dt>联系人</dt>
            <dd>
              <span>{{ item.ownerName }}</span>
              <span>{{ displayPhone(item) }}</span>
              <button
                class="repair-phone-toggle"
                type="button"
                :aria-label="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                :title="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                :aria-pressed="phoneVisible(item.id)"
                @click="$emit('toggle-phone', item.id)"
              >
                <EyeOff v-if="phoneVisible(item.id)" aria-hidden="true" />
                <Eye v-else aria-hidden="true" />
              </button>
            </dd>
          </div>
        </dl>

        <footer>
          <button class="button text small" type="button" @click="$emit('preview', item)">
            <FileText aria-hidden="true" />查看协议
          </button>
          <div>
            <button
              v-if="canManage"
              class="icon-button"
              type="button"
              title="编辑"
              :aria-label="`编辑 ${item.caseNo}`"
              @click="$emit('edit', item)"
            >
              <Pencil aria-hidden="true" />
            </button>
            <button
              v-if="canDelete"
              class="icon-button danger-ghost"
              type="button"
              title="移入回收站"
              :aria-label="`将 ${item.caseNo} 移入回收站`"
              @click="$emit('delete', item)"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </div>
        </footer>
      </article>
    </div>

    <EmptyState
      v-else-if="!loading && !error"
      title="当前没有进行中的维修"
      description="新受理的维修事务会进入这里。"
      :icon="Wrench"
    />
    <div v-if="loading && !items.length" class="repair-region-feedback" aria-live="polite">
      <LoaderCircle class="spin" aria-hidden="true" />
      <span>正在加载工作队列</span>
    </div>
    <div v-if="error" class="repair-region-feedback danger" role="alert">
      <span>{{ error }}</span>
      <button class="button text" type="button" @click="$emit('retry')">重试</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  Clock3,
  Eye,
  EyeOff,
  FileText,
  LoaderCircle,
  Pencil,
  Trash2,
  Wrench,
} from "@lucide/vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgeLabel,
  repairDateTime,
  repairDeviceName,
} from "./repairDisplay";
import type { RepairCase } from "./repairTypes";

const props = defineProps<{
  items: RepairCase[];
  loading: boolean;
  error: string;
  revealedPhones: Set<number>;
  canManage: boolean;
  canDelete?: boolean;
}>();

defineEmits<{
  view: [item: RepairCase];
  preview: [item: RepairCase];
  edit: [item: RepairCase];
  delete: [item: RepairCase];
  "toggle-phone": [id: number];
  retry: [];
}>();

const phoneVisible = (id: number) => props.revealedPhones.has(id);
const displayPhone = (item: RepairCase) =>
  phoneVisible(item.id) ? item.ownerPhone : maskRepairPhone(item.ownerPhone);
</script>
