<template>
  <div class="form-sections repair-editor-body">
    <section>
      <h3>协议与联系人</h3>
      <div class="form-grid two">
        <label class="field">
          <span>协议类型</span>
          <select v-model="form.agreementType" name="repair-agreement-type">
            <option value="REPAIR">维修协议</option>
            <option value="DISCLAIMER">免责协议</option>
          </select>
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model="form.status" name="repair-status">
            <option value="REPAIRING">进行中</option>
            <option value="COMPLETED">已完成</option>
            <option value="CANCELED">已取消</option>
          </select>
        </label>
        <label class="field">
          <span>联系人</span>
          <input
            v-model.trim="form.ownerName"
            name="repair-owner-name"
            autocomplete="name"
            data-dialog-initial-focus
            :aria-invalid="Boolean(errors.ownerName)"
          />
          <small v-if="errors.ownerName" class="field-error" role="alert">{{ errors.ownerName }}</small>
        </label>
        <label class="field">
          <span>联系电话</span>
          <input
            v-model.trim="form.ownerPhone"
            name="repair-owner-phone"
            type="tel"
            inputmode="tel"
            autocomplete="tel"
            :aria-invalid="Boolean(errors.ownerPhone)"
          />
          <small v-if="errors.ownerPhone" class="field-error" role="alert">{{ errors.ownerPhone }}</small>
        </label>
      </div>
    </section>
    <section>
      <h3>设备与故障</h3>
      <div class="form-grid two">
        <label class="field">
          <span>设备类型</span>
          <input
            v-model.trim="form.deviceType"
            name="repair-device-type"
            autocomplete="off"
            placeholder="笔记本电脑、台式机等"
            :aria-invalid="Boolean(errors.deviceType)"
          />
          <small v-if="errors.deviceType" class="field-error" role="alert">{{ errors.deviceType }}</small>
        </label>
        <label class="field">
          <span>品牌</span>
          <input v-model.trim="form.deviceBrand" name="repair-device-brand" autocomplete="off" />
        </label>
        <label class="field">
          <span>具体型号</span>
          <input v-model.trim="form.deviceModel" name="repair-device-model" autocomplete="off" />
        </label>
        <label class="field">
          <span>附件</span>
          <input v-model.trim="form.accessories" name="repair-accessories" autocomplete="off" placeholder="电源、鼠标等" />
        </label>
        <label class="field span-2">
          <span>故障描述</span>
          <textarea
            v-model.trim="form.faultDescription"
            name="repair-fault-description"
            rows="2"
            :aria-invalid="Boolean(errors.faultDescription)"
          />
          <small v-if="errors.faultDescription" class="field-error" role="alert">{{ errors.faultDescription }}</small>
        </label>
        <label class="field span-2">
          <span>维修说明</span>
          <textarea v-model.trim="form.serviceDescription" name="repair-service-description" rows="2" />
        </label>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { RepairErrors } from "./repairForms";
import type { RepairCaseForm } from "./repairTypes";

defineProps<{ form: RepairCaseForm; errors: RepairErrors }>();
</script>
