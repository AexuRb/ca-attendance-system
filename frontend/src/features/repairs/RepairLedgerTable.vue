<template>
  <div class="repair-ledger-region">
    <div v-if="items.length" class="repair-ledger-table table-shell">
      <table>
        <thead>
          <tr>
            <th>维修编号</th>
            <th>设备与故障</th>
            <th>联系人</th>
            <th>负责人</th>
            <th>{{ timeLabel }}</th>
            <th>状态</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
            class="repair-ledger-row"
            :class="{ 'is-long-running': isLongRunningRepair(item) }"
            tabindex="0"
            @click="$emit('view', item)"
            @keydown.enter.self="$emit('view', item)"
            @keydown.space.self.prevent="$emit('view', item)"
          >
            <td data-label="维修编号">
              <strong class="case-no">{{ item.caseNo }}</strong>
              <small>{{ repairAgreementLabel(item.agreementType) }}</small>
            </td>
            <td data-label="设备与故障">
              <strong>{{ repairDeviceName(item) }}</strong>
              <small>{{ item.faultDescription }}</small>
            </td>
            <td data-label="联系人">
              <strong>{{ item.ownerName }}</strong>
              <span class="repair-ledger-phone">
                <small>{{ displayPhone(item) }}</small>
                <button
                  class="repair-phone-toggle"
                  type="button"
                  :aria-label="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                  :title="phoneVisible(item.id) ? '隐藏完整电话' : '显示完整电话'"
                  :aria-pressed="phoneVisible(item.id)"
                  @click.stop="$emit('toggle-phone', item.id)"
                >
                  <EyeOff v-if="phoneVisible(item.id)" aria-hidden="true" />
                  <Eye v-else aria-hidden="true" />
                </button>
              </span>
            </td>
            <td data-label="负责人">{{ item.handlerName || "待分配" }}</td>
            <td :data-label="timeLabel">
              <time :datetime="displayTime(item)">{{ repairDateTime(displayTime(item)) }}</time>
              <small>{{ repairAgeLabel(item) }}</small>
            </td>
            <td data-label="状态">
              <span class="repair-ledger-status" :data-status="item.status">
                {{ statusLabel(item.status) }}
              </span>
            </td>
            <td class="repair-ledger-actions align-right" data-label="操作">
              <button
                class="icon-button ghost repair-ledger-open"
                type="button"
                title="查看详情"
                :aria-label="`查看 ${item.caseNo} 的详情`"
                @click.stop="$emit('view', item)"
              >
                <PanelRightOpen aria-hidden="true" />
              </button>
              <button
                class="icon-button ghost repair-ledger-secondary-action"
                type="button"
                title="查看协议"
                :aria-label="`查看 ${item.caseNo} 的协议`"
                @click.stop="$emit('preview', item)"
              >
                <FileText aria-hidden="true" />
              </button>
              <button
                v-if="status === 'REPAIRING' && canManage"
                class="icon-button ghost repair-ledger-secondary-action"
                type="button"
                title="编辑"
                :aria-label="`编辑 ${item.caseNo}`"
                @click.stop="$emit('edit', item)"
              >
                <Pencil aria-hidden="true" />
              </button>
              <button
                v-if="status === 'REPAIRING' && canDelete"
                class="icon-button danger-ghost repair-ledger-secondary-action"
                type="button"
                title="移入回收站"
                :aria-label="`将 ${item.caseNo} 移入回收站`"
                @click.stop="$emit('delete', item)"
              >
                <Trash2 aria-hidden="true" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
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
import { computed } from "vue";
import {
  Archive,
  Eye,
  EyeOff,
  FileText,
  LoaderCircle,
  PanelRightOpen,
  Pencil,
  Trash2,
  Wrench,
} from "@lucide/vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgeLabel,
  repairAgreementLabel,
  repairDateTime,
  repairDeviceName,
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
}>();

defineEmits<{
  view: [item: RepairCase];
  preview: [item: RepairCase];
  edit: [item: RepairCase];
  delete: [item: RepairCase];
  "toggle-phone": [id: number];
  retry: [];
}>();

const timeLabel = computed(() => {
  if (props.status === "COMPLETED") return "完成时间";
  if (props.status === "CANCELED") return "取消时间";
  return "受理时间";
});
const emptyTitle = computed(() => {
  if (props.status === "COMPLETED") return "暂无已完成记录";
  if (props.status === "CANCELED") return "暂无已取消记录";
  return "当前没有进行中的维修";
});
const emptyIcon = computed(() =>
  props.status === "REPAIRING" ? Wrench : Archive,
);

const phoneVisible = (id: number) => props.revealedPhones.has(id);
const displayPhone = (item: RepairCase) =>
  phoneVisible(item.id) ? item.ownerPhone : maskRepairPhone(item.ownerPhone);
const displayTime = (item: RepairCase) =>
  item.status === "REPAIRING"
    ? item.receivedAt
    : item.completedAt || item.updatedAt;
const statusLabel = (status: RepairStatus) =>
  status === "REPAIRING" ? "进行中" : status === "COMPLETED" ? "已完成" : "已取消";
</script>
