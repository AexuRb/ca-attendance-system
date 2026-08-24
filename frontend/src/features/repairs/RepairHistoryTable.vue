<template>
  <div class="repair-history-region">
    <div v-if="items.length" class="repair-history-table table-shell">
      <table>
        <thead>
          <tr>
            <th>维修编号</th>
            <th>设备</th>
            <th>联系人</th>
            <th>负责人</th>
            <th>{{ endTimeLabel }}</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
            class="repair-history-row"
            tabindex="0"
            @click="$emit('view', item)"
            @keydown.enter.self="$emit('view', item)"
            @keydown.space.self.prevent="$emit('view', item)"
          >
            <td data-label="维修编号">
              <strong class="case-no">{{ item.caseNo }}</strong>
              <small>{{ repairAgreementLabel(item.agreementType) }}</small>
            </td>
            <td data-label="设备">
              <strong>{{ repairDeviceName(item) }}</strong>
              <small>{{ item.faultDescription }}</small>
            </td>
            <td data-label="联系人">
              <strong>{{ item.ownerName }}</strong>
              <span class="repair-history-phone">
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
            <td data-label="负责人">{{ item.handlerName || "未分配" }}</td>
            <td :data-label="endTimeLabel">
              <time :datetime="endTime(item)">{{ repairDateTime(endTime(item)) }}</time>
              <small>{{ repairAgeLabel(item) }}</small>
            </td>
            <td class="repair-history-actions align-right" data-label="操作">
              <button
                class="icon-button ghost"
                type="button"
                title="查看详情"
                :aria-label="`查看 ${item.caseNo} 的详情`"
                @click.stop="$emit('view', item)"
              >
                <PanelRightOpen aria-hidden="true" />
              </button>
              <button
                class="icon-button ghost"
                type="button"
                title="查看协议"
                :aria-label="`查看 ${item.caseNo} 的协议`"
                @click.stop="$emit('preview', item)"
              >
                <FileText aria-hidden="true" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <EmptyState
      v-else-if="!loading && !error"
      :title="status === 'COMPLETED' ? '暂无已完成记录' : '暂无已取消记录'"
      description="调整筛选条件后可以重新查询。"
      :icon="Archive"
    />
    <div v-if="loading && !items.length" class="repair-region-feedback" aria-live="polite">
      <LoaderCircle class="spin" aria-hidden="true" />
      <span>正在加载历史档案</span>
    </div>
    <div v-if="error" class="repair-region-feedback danger" role="alert">
      <span>{{ error }}</span>
      <button class="button text" type="button" @click="$emit('retry')">重试</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  Archive,
  Eye,
  EyeOff,
  FileText,
  LoaderCircle,
  PanelRightOpen,
} from "@lucide/vue";
import { computed } from "vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import {
  maskRepairPhone,
  repairAgeLabel,
  repairAgreementLabel,
  repairDateTime,
  repairDeviceName,
} from "./repairDisplay";
import type { RepairCase, RepairStatus } from "./repairTypes";

const props = defineProps<{
  items: RepairCase[];
  status: Exclude<RepairStatus, "REPAIRING">;
  loading: boolean;
  error: string;
  revealedPhones: Set<number>;
}>();

defineEmits<{
  view: [item: RepairCase];
  preview: [item: RepairCase];
  "toggle-phone": [id: number];
  retry: [];
}>();

const endTimeLabel = computed(() =>
  props.status === "COMPLETED" ? "完成时间" : "取消时间",
);
const phoneVisible = (id: number) => props.revealedPhones.has(id);
const displayPhone = (item: RepairCase) =>
  phoneVisible(item.id) ? item.ownerPhone : maskRepairPhone(item.ownerPhone);
const endTime = (item: RepairCase) => item.completedAt || item.updatedAt;
</script>
