<template>
  <div :ref="bindScroll" class="repair-ledger-region" @scroll="$emit('scroll')">
    <div v-if="items.length" class="repair-ledger-table" :class="{ 'repair-active-cards': status === 'REPAIRING' }" aria-label="维修事务清单">
      <div role="list">
        <article v-for="item in items" :key="item.id" class="repair-ledger-row"
          role="listitem" :class="{ 'is-long-running': isLongRunningRepair(item) }"
          @click="$emit('view', item)">
          <button class="repair-cell-device" type="button" :aria-label="`查看 ${item.caseNo} 的详情`" @click.stop="$emit('view', item)">
            <strong :title="[item.deviceBrand, item.deviceModel, item.deviceType].filter(Boolean).join(' ')">{{ [item.deviceBrand, item.deviceModel].filter(Boolean).join(' ') || item.deviceType || '未命名设备' }}</strong>
            <span class="repair-fault-summary" :title="item.faultDescription">{{ item.faultDescription }}</span>
            <small class="repair-card-identity" :title="`${item.deviceType} · ${item.caseNo} · ${repairAgreementLabel(item.agreementType)}`">{{ item.caseNo }} · {{ repairAgreementLabel(item.agreementType) }}</small>
          </button>
          <div class="repair-cell-contact">
            <strong>{{ item.ownerName }}</strong>
            <span class="repair-ledger-phone">
              <small>{{ displayPhone(item) }}</small>
              <button data-button-kind="icon" v-if="item.ownerPhone?.trim()" class="repair-phone-toggle" type="button"
                :aria-label="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                :title="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                :aria-pressed="phoneVisible(item.id)" @click.stop="$emit('toggle-phone', item.id)">
                <EyeOff v-if="phoneVisible(item.id)" aria-hidden="true" /><Eye v-else aria-hidden="true" />
              </button>
            </span>
            <small class="repair-cell-handler">负责人 {{ item.handlerName || '待分配' }}</small>
          </div>
          <div class="repair-cell-time">
            <small class="repair-cell-label">{{ timeLabel }}</small>
            <time :datetime="displayTime(item)">{{ repairDateTime(displayTime(item)) }}</time>
            <small>{{ repairAgeLabel(item) }}</small>
          </div>
          <div class="repair-ledger-actions" @click.stop @keydown.stop>
            <button data-button-kind="action" v-if="status === 'REPAIRING' && canManage" class="button primary repair-card-process"
              type="button" title="更新处理" :aria-label="`更新 ${item.caseNo} 的处理情况`" @click="$emit('process', item)">处理</button>
            <button v-else data-button-kind="soft" class="button secondary repair-card-view" type="button"
              :aria-label="`查看 ${item.caseNo} 的详情`" @click="$emit('view', item)">查看</button>
            <ActionMenu :label="`${item.caseNo} 更多操作`">
              <button role="menuitem" type="button" @click="$emit('preview', item)"><FileText aria-hidden="true" />查看协议</button>
              <button v-if="canManage" role="menuitem" type="button" @click="$emit('edit', item)"><Pencil aria-hidden="true" />编辑事务</button>
              <button v-if="status === 'REPAIRING' && canDelete" role="menuitem" type="button" class="danger"
                title="移入回收站" @click="$emit('delete', item)"><Trash2 aria-hidden="true" />移入回收站</button>
            </ActionMenu>
            <span class="sr-only">{{ statusLabel(item.status) }}</span>
          </div>
        </article>
      </div>
    </div>

    <EmptyState
      v-else-if="!loading && !error"
      :title="emptyTitle"
      description="调整筛选条件后可以重新查询。"
      :icon="emptyIcon"
    />
    <div v-if="loading && !items.length" class="repair-region-feedback" aria-live="polite">
      <LoaderCircle class="spin" aria-hidden="true" />
      <span>正在加载维修台账</span>
    </div>
    <div v-if="error" class="repair-region-feedback danger" role="alert">
      <span>{{ error }}</span>
      <button class="button text" type="button" @click="$emit('retry')">重试</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type ComponentPublicInstance } from "vue";
import {
  Archive,
  Eye,
  EyeOff,
  FileText,
  LoaderCircle,
  Pencil,
  Trash2,
  Wrench,
} from "@lucide/vue";
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgeLabel,
  repairAgreementLabel,
  repairDateTime,
} from "./repairDisplay";
import type { RepairCase, RepairStatus } from "./repairTypes";

const props = defineProps<{
  items: RepairCase[];
  status: RepairStatus;
  loading: boolean;
  error: string;
  revealedPhones: Set<number>;
  canManage: boolean;
  canDelete: boolean;
  bindScroll?: (element: Element | ComponentPublicInstance | null) => void;
}>();

defineEmits<{
  view: [item: RepairCase];
  preview: [item: RepairCase];
  process: [item: RepairCase];
  edit: [item: RepairCase];
  delete: [item: RepairCase];
  "toggle-phone": [id: number];
  retry: [];
  scroll: [];
}>();

const timeLabel = computed(() => {
  if (props.status === "COMPLETED") return "完成时间";
  if (props.status === "CANCELED") return "最近更新时间";
  return "受理时间";
});
const emptyTitle = computed(() => {
  if (props.status === "COMPLETED") return "当前条件下没有已完成记录";
  if (props.status === "CANCELED") return "当前条件下没有已取消记录";
  return "当前条件下没有进行中的维修";
});
const emptyIcon = computed(() =>
  props.status === "REPAIRING" ? Wrench : Archive,
);

const phoneVisible = (id: number) => props.revealedPhones.has(id);
const displayPhone = (item: RepairCase) =>
  phoneVisible(item.id) && item.ownerPhone?.trim() ? item.ownerPhone : maskRepairPhone(item.ownerPhone);
const displayTime = (item: RepairCase) =>
  item.status === "REPAIRING"
    ? item.receivedAt
    : item.status === "CANCELED" ? item.updatedAt : item.completedAt || item.updatedAt;
const statusLabel = (status: RepairStatus) =>
  status === "REPAIRING" ? "进行中" : status === "COMPLETED" ? "已完成" : "已取消";
</script>
