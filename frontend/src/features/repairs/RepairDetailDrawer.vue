<template>
  <Teleport to="body">
    <Transition name="repair-drawer">
      <div
        v-if="open && item"
        class="repair-drawer-backdrop"
        @mousedown.self="$emit('close')"
      >
        <aside
          ref="dialog"
          class="repair-detail-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="repair-detail-title"
          tabindex="-1"
        >
          <header class="repair-detail-header">
            <div>
              <p class="eyebrow">REPAIR DETAIL</p>
              <h2 id="repair-detail-title">{{ item.caseNo }}</h2>
            </div>
            <button
              class="icon-button ghost"
              type="button"
              aria-label="关闭维修详情"
              title="关闭"
              @click="$emit('close')"
            >
              <X aria-hidden="true" />
            </button>
          </header>

          <div class="repair-detail-body" data-dialog-content>
            <div class="repair-detail-status">
              <StatusBadge :label="statusLabel" :tone="statusTone" />
              <span>{{ repairAgreementLabel(item.agreementType) }}</span>
              <span :data-attention="isLongRunningRepair(item)">
                {{ repairAgeLabel(item) }}
              </span>
            </div>

            <section class="repair-detail-lead">
              <span>送修设备</span>
              <h3>{{ repairDeviceName(item) }}</h3>
              <p>{{ item.faultDescription }}</p>
            </section>

            <section v-if="item.serviceDescription" class="repair-detail-section">
              <h3>维修说明</h3>
              <p>{{ item.serviceDescription }}</p>
            </section>

            <dl class="repair-detail-facts">
              <div>
                <dt><UserRound aria-hidden="true" />联系人</dt>
                <dd>
                  <strong>{{ item.ownerName }}</strong>
                  <span>
                    {{ phoneVisible ? item.ownerPhone : maskRepairPhone(item.ownerPhone) }}
                    <button
                      class="repair-phone-toggle"
                      type="button"
                      :aria-label="phoneVisible ? '隐藏完整电话' : '显示完整电话'"
                      :title="phoneVisible ? '隐藏完整电话' : '显示完整电话'"
                      :aria-pressed="phoneVisible"
                      @click="$emit('toggle-phone', item.id)"
                    >
                      <EyeOff v-if="phoneVisible" aria-hidden="true" />
                      <Eye v-else aria-hidden="true" />
                    </button>
                  </span>
                </dd>
              </div>
              <div>
                <dt><Wrench aria-hidden="true" />负责人</dt>
                <dd>{{ item.handlerName || "待分配" }}</dd>
              </div>
              <div>
                <dt><CalendarClock aria-hidden="true" />受理时间</dt>
                <dd>{{ repairDateTime(item.receivedAt) }}</dd>
              </div>
              <div>
                <dt><CircleCheck aria-hidden="true" />{{ endTimeLabel }}</dt>
                <dd>{{ repairDateTime(item.completedAt || item.updatedAt) }}</dd>
              </div>
            </dl>

            <section v-if="item.accessories || item.remark" class="repair-detail-section repair-detail-notes">
              <div v-if="item.accessories">
                <h3>随附物品</h3>
                <p>{{ item.accessories }}</p>
              </div>
              <div v-if="item.remark">
                <h3>备注</h3>
                <p>{{ item.remark }}</p>
              </div>
            </section>

            <section class="repair-detail-section">
              <h3>受理确认</h3>
              <ul class="repair-confirmation-list">
                <li :data-confirmed="item.dataBackupConfirmed">
                  <CircleCheck aria-hidden="true" />数据备份情况已记录
                </li>
                <li :data-confirmed="item.riskAcknowledged">
                  <CircleCheck aria-hidden="true" />维修风险情况已记录
                </li>
                <li :data-confirmed="item.privacyAcknowledged">
                  <CircleCheck aria-hidden="true" />隐私事项情况已记录
                </li>
              </ul>
            </section>
          </div>

          <footer class="repair-detail-actions">
            <button
              class="button secondary"
              type="button"
              data-action="preview"
              @click="$emit('preview', item)"
            >
              <FileText aria-hidden="true" />查看协议
            </button>
            <button
              v-if="canManage"
              class="button primary"
              type="button"
              @click="$emit('edit', item)"
            >
              <Pencil aria-hidden="true" />编辑事务
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
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import {
  CalendarClock,
  CircleCheck,
  Eye,
  EyeOff,
  FileText,
  Pencil,
  Trash2,
  UserRound,
  Wrench,
  X,
} from "@lucide/vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import { useDialogFocus } from "../../shared/ui/useDialogFocus";
import {
  isLongRunningRepair,
  maskRepairPhone,
  repairAgeLabel,
  repairAgreementLabel,
  repairDateTime,
  repairDeviceName,
} from "./repairDisplay";
import type { RepairCase } from "./repairTypes";

const props = defineProps<{
  open: boolean;
  item: RepairCase | null;
  phoneVisible: boolean;
  canManage: boolean;
  canDelete: boolean;
}>();

const emit = defineEmits<{
  close: [];
  preview: [item: RepairCase];
  edit: [item: RepairCase];
  delete: [item: RepairCase];
  "toggle-phone": [id: number];
}>();

const dialog = ref<HTMLElement | null>(null);
let restorePageScroll: (() => void) | null = null;
useDialogFocus({
  root: dialog,
  open: () => props.open,
  close: () => emit("close"),
});

const statusLabel = computed(() => {
  if (props.item?.status === "COMPLETED") return "已完成";
  if (props.item?.status === "CANCELED") return "已取消";
  return "进行中";
});
const statusTone = computed(() => {
  if (props.item?.status === "COMPLETED") return "success" as const;
  if (props.item?.status === "CANCELED") return "neutral" as const;
  return "info" as const;
});
const endTimeLabel = computed(() =>
  props.item?.status === "COMPLETED"
    ? "完成时间"
    : props.item?.status === "CANCELED"
      ? "最后更新"
      : "最近更新",
);

watch(
  () => props.open,
  (open) => {
    if (open && !restorePageScroll) {
      const htmlOverflow = document.documentElement.style.overflow;
      const bodyOverflow = document.body.style.overflow;
      document.documentElement.style.overflow = "hidden";
      document.body.style.overflow = "hidden";
      restorePageScroll = () => {
        document.documentElement.style.overflow = htmlOverflow;
        document.body.style.overflow = bodyOverflow;
        restorePageScroll = null;
      };
    } else if (!open) {
      restorePageScroll?.();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => restorePageScroll?.());
</script>
