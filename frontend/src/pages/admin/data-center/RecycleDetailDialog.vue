<template>
  <ModalDialog :open="Boolean(item)" title="回收站维修详情" size="lg" backdrop-class="recycle-refined-backdrop recycle-detail-backdrop" @close="$emit('close')">
    <template v-if="item">
      <div class="recycle-detail-heading">
        <span>{{ item.caseNo }} · {{ repairAgreementLabel(item.agreementType) }}</span>
        <h3>{{ repairDeviceName(item) }}</h3>
        <span class="recycle-original-status">原状态 · {{ statusLabel(item.status) }}</span>
      </div>
      <dl class="recycle-detail-facts">
        <div><dt>送修人</dt><dd>{{ item.ownerName || '未填写' }}</dd></div>
        <div><dt>联系电话</dt><dd>{{ phoneVisible ? item.ownerPhone : maskRepairPhone(item.ownerPhone) }}
          <button data-button-kind="icon" v-if="item.ownerPhone?.trim()" class="recycle-phone-toggle" type="button"
            :aria-label="phoneVisible ? '隐藏完整电话' : '显示完整电话'" :aria-pressed="phoneVisible" @click="phoneVisible = !phoneVisible">
            <EyeOff v-if="phoneVisible" aria-hidden="true" /><Eye v-else aria-hidden="true" />
          </button>
        </dd></div>
        <div><dt>负责人</dt><dd>{{ item.handlerName || '待分配' }}</dd></div>
        <div><dt>受理时间</dt><dd>{{ repairDateTime(item.receivedAt) }}</dd></div>
        <div v-if="item.status === 'COMPLETED'"><dt>完成时间</dt><dd>{{ repairDateTime(item.completedAt) }}</dd></div>
        <div><dt>最近更新时间</dt><dd>{{ repairDateTime(item.updatedAt) }}</dd></div>
        <div><dt>删除时间</dt><dd>{{ repairDateTime(item.deletedAt) }}</dd></div>
        <div><dt>删除人</dt><dd>{{ item.deletedByName || '未记录' }}</dd></div>
      </dl>
      <section class="recycle-detail-section"><h4>故障描述</h4><p>{{ item.faultDescription || '未填写' }}</p></section>
      <section class="recycle-detail-section"><h4>维修说明</h4><p>{{ item.serviceDescription || '未填写' }}</p></section>
      <section v-if="item.accessories" class="recycle-detail-section"><h4>随附物品</h4><p>{{ item.accessories }}</p></section>
      <section v-if="item.remark" class="recycle-detail-section"><h4>备注</h4><p>{{ item.remark }}</p></section>
      <section class="recycle-detail-section"><h4>受理确认</h4>
        <ul class="recycle-detail-confirmations">
          <li>数据备份情况：{{ item.dataBackupConfirmed ? '已确认' : '未确认' }}</li>
          <li>维修风险：{{ item.riskAcknowledged ? '已确认' : '未确认' }}</li>
          <li>隐私事项：{{ item.privacyAcknowledged ? '已确认' : '未确认' }}</li>
        </ul>
      </section>
    </template>
    <template #footer><button class="button secondary" type="button" @click="$emit('close')">关闭详情</button></template>
  </ModalDialog>
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { Eye, EyeOff } from "@lucide/vue";
import ModalDialog from "../../../shared/ui/ModalDialog.vue";
import type { RecycledRepairCase } from "../../../features/maintenance/dataCenterTypes";
import { maskRepairPhone, repairAgreementLabel, repairDateTime, repairDeviceName } from "../../../features/repairs/repairDisplay";
import { statusLabel } from "./recycleDisplay";
const props = defineProps<{ item: RecycledRepairCase | null }>();
defineEmits<{ close: [] }>();
const phoneVisible = ref(false);
watch(() => props.item?.id, () => { phoneVisible.value = false; });
</script>
