<template>
  <div class="page-stack profile-page">
    <PageHeader
      eyebrow="PEOPLE / PROFILE"
      title="个人资料"
      description="维护联系方式，并查看自己的值班与培训记录。"
    />
    <div class="profile-summary">
      <span class="avatar profile-avatar">{{ user?.name?.slice(0, 1) }}</span>
      <div>
        <h2>{{ user?.name }}</h2>
        <p>{{ user?.studentNo }} · {{ roleLabel(user?.role) }}</p>
      </div>
      <div class="profile-stat">
        <strong>{{ totalHours }}</strong
        ><span>本年小时</span>
      </div>
      <div class="profile-stat">
        <strong>{{ records.length }}</strong
        ><span>值班记录</span>
      </div>
    </div>
    <div class="profile-grid">
      <section class="panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">CONTACT</p>
            <h2>联系信息</h2>
          </div>
        </div>
        <form class="form-grid two" @submit.prevent="save">
          <label class="field"
            ><span>手机</span
            ><input
              v-model.trim="profile.phone"
              name="tel"
              autocomplete="tel" /></label
          ><label class="field"
            ><span>QQ</span><input v-model.trim="profile.qq" /></label
          ><label class="field"
            ><span>学院</span><input v-model.trim="profile.major" /></label
          ><label class="field"
            ><span>年级</span><input v-model.trim="profile.grade"
          /></label>
          <div class="span-2 form-actions">
            <button class="button primary" type="submit" :disabled="busy">
              <Save />保存资料
            </button>
          </div>
        </form>
      </section>
      <section class="panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">MY RECORDS</p>
            <h2>最近值班</h2>
          </div>
        </div>
        <EmptyState v-if="!records.length" title="暂无值班记录" />
        <div v-else class="activity-list">
          <article v-for="item in records.slice(0, 8)" :key="item.id">
            <CalendarCheck />
            <div>
              <strong>{{ item.dutyDate }}</strong>
              <p>
                {{ clock(item.checkInTime) }}–{{ clock(item.checkOutTime) }} ·
                {{ item.durationMinutes || 0 }} 分钟
              </p>
            </div>
            <StatusBadge
              :label="item.effectiveStatus === 'VALID' ? '有效' : '未完成'"
              :tone="item.effectiveStatus === 'VALID' ? 'success' : 'warning'"
            />
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { CalendarCheck, Save } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import { get, put } from "../../shared/api";
import { useSession } from "../../app/session";
import { useAsyncTask } from "../../shared/composables/useAsyncTask";
const { user, refreshUser } = useSession();
const { busy, run } = useAsyncTask();
const records = ref<any[]>([]);
const training = ref<any>({ trainingHours: 0 });
const profile = reactive({ phone: "", qq: "", major: "", grade: "" });
const totalHours = computed(() =>
  Number(
    records.value.reduce((s, i) => s + Number(i.validHours || 0), 0) +
      Number(training.value.trainingHours || 0),
  ).toFixed(1),
);
onMounted(load);
async function load() {
  const me = await get<any>("/api/auth/me");
  Object.assign(profile, {
    phone: me.phone || "",
    qq: me.qq || "",
    major: me.major || "",
    grade: me.grade || "",
  });
  const now = new Date();
  const from = `${now.getFullYear()}-01-01`;
  const to = date(now);
  [records.value, training.value] = await Promise.all([
    get(`/api/attendance/me?from=${from}&to=${to}`),
    get(`/api/trainings/me/hours?from=${from}&to=${to}`),
  ]);
}
async function save() {
  const ok = await run(() => put("/api/me/profile", profile), "个人资料已保存");
  if (ok) await refreshUser();
}
const clock = (v?: string) => v?.slice(11, 16) || "—";
const roleLabel = (v?: string) =>
  (
    ({
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    }) as any
  )[v || ""] || "";
function date(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>
