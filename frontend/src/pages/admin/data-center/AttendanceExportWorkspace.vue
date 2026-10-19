<template>
  <section class="attendance-export-workspace">
    <template v-if="!period">
      <header class="data-operation-header"><div class="import-kind-identity"><span class="import-kind-icon"><ClipboardList aria-hidden="true" /></span><h2>值班记录</h2></div><button class="button secondary" data-button-kind="entry" type="button" @click="$emit('back')"><ArrowLeft aria-hidden="true" />更换数据类型</button></header>
      <DataTypeCatalog mode="export" title="选择导出方式" :items="choices" @choose="choose" />
    </template>
    <template v-else>
      <header class="data-operation-header"><div class="import-kind-identity"><span class="import-kind-icon"><CalendarDays aria-hidden="true" /></span><h2>值班记录 · {{label}}</h2></div><button class="button secondary" data-button-kind="entry" type="button" :disabled="busy" @click="period=null"><ArrowLeft aria-hidden="true" />更换导出方式</button></header>
      <form class="attendance-ledger-settings" @submit.prevent="makePreview">
        <label v-if="period==='weekly'" class="field"><span>选择周内任一天</span><input v-model="weekDay" type="date" :disabled="exporting" required /></label>
        <label v-else-if="period==='monthly'" class="field"><span>选择月份</span><input v-model="month" type="month" :disabled="exporting" required /></label>
        <template v-else><label class="field"><span>开始日期</span><input v-model="range.from" type="date" :disabled="exporting" required /></label><label class="field"><span>结束日期</span><input v-model="range.to" type="date" :disabled="exporting" required /></label></template>
        <div class="attendance-ledger-actions"><button class="button secondary" data-button-kind="entry" type="submit" :disabled="busy||Boolean(dateError)"><ScanSearch aria-hidden="true" />{{loading?'正在生成…':'生成预览'}}</button><button class="button primary" type="button" :disabled="busy||!preview" @click="download"><Download aria-hidden="true" />{{exporting?'正在导出…':'导出 Excel'}}</button></div>
        <p class="attendance-ledger-scope">{{range.from||'开始日期'}} 至 {{range.to||'结束日期'}} · 单位：小时</p>
        <p class="attendance-ledger-help">范围内有值班记录的成员；仅汇总当前系统值班日的有效时长，不含培训及非值班日记录。</p>
        <p v-if="dateError" class="form-error" role="alert">{{dateError}}</p><p v-if="error" class="form-error" role="alert">{{error}}</p>
      </form>
      <section class="attendance-ledger-preview" aria-labelledby="attendance-ledger-preview-heading" :aria-busy="loading">
        <header class="data-table-toolbar"><h3 id="attendance-ledger-preview-heading" ref="previewHeading" tabindex="-1">结果预览 <small v-if="preview">共 {{preview.totalRows}} 人{{preview.totalRows>preview.rows.length?` · 预览前 ${preview.rows.length} 人`:''}}</small></h3><p v-if="!preview" role="status">{{loading?'正在生成…':'待生成预览'}}</p></header>
        <template v-if="preview">
          <p class="attendance-ledger-help attendance-ledger-range">值班日期：{{preview.weekdays.map(n=>'周'+'一二三四五六日'[n-1]).join('、')||'系统未启用值班日'}}；无有效记录留空，有效记录为零显示 0。Excel 导出全部成员，周小计和总计使用公式。</p>
          <div class="attendance-ledger-scroll" role="region" aria-label="值班记录模板预览，可横向滚动" tabindex="0">
            <table class="attendance-ledger-table"><colgroup><col class="ledger-id-col" /><col class="ledger-name-col" /><col v-for="(column,c) in preview.columns" :key="c" :class="column.kind==='date'?'ledger-day-col':'ledger-sum-col'" /></colgroup>
              <thead>
                <tr><th class="ledger-id" :rowspan="grouped?2:1">学号</th><th class="ledger-name" :rowspan="grouped?2:1">姓名</th><template v-if="grouped"><th v-for="(group,g) in preview.groups" :key="g" :colspan="group.end-group.start+1" class="ledger-week-heading">{{group.label}}</th><th :rowspan="2" class="ledger-total">总计</th></template><template v-else><th v-for="(column,c) in preview.columns" :key="c" :class="{'ledger-total':column.kind==='total'}">{{column.label}}</th></template></tr>
                <tr v-if="grouped" class="ledger-day-heading"><th v-for="(column,c) in preview.columns.slice(0,-1)" :key="c" :class="{'ledger-subtotal':column.kind==='subtotal'}">{{column.label}}</th></tr>
              </thead>
              <tbody><tr v-if="!preview.rows.length"><td :colspan="preview.columns.length+2" class="ledger-empty">当前范围没有可展示的值班记录。</td></tr><tr v-for="row in preview.rows" :key="row.studentNo"><td class="ledger-id">{{row.studentNo}}</td><td class="ledger-name">{{row.name}}</td><td v-for="(column,c) in preview.columns" :key="c" :class="{'ledger-subtotal':column.kind==='subtotal','ledger-total':column.kind==='total'}">{{format(row.values[c])}}</td></tr></tbody>
            </table>
          </div>
        </template>
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,reactive,ref,watch} from 'vue';
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {ArrowLeft,ClipboardList,CalendarDays,ScanSearch,Download} from '@lucide/vue';
import {api,ApiError,downloadBlob} from '../../../shared/api';
import {notify} from '../../../shared/composables/useToast';
import {localDate,weekRange,monthRange} from '../../../features/maintenance/attendanceLedgerTypes';
import type {AttendancePeriod,LedgerPreview} from '../../../features/maintenance/attendanceLedgerTypes';
import DataTypeCatalog from './DataTypeCatalog.vue';
const emit=defineEmits<{back:[];custom:[]}>();
const choices=[{id:'weekly',label:'周',description:'按值班日期展示一周有效时长和总计'},{id:'monthly',label:'月',description:'按周分组，展示整月值班日期、周小计和总计'},{id:'semester',label:'学期',description:'选择起止日期，逐周排列值班日期、周小计和总计'},{id:'custom',label:'自定义',description:'自行选择原有导出字段和筛选条件'}];
const period=ref<AttendancePeriod|null>(null),today=localDate(new Date()),weekDay=ref(today),month=ref(today.slice(0,7));
const range=reactive({from:'',to:''}),preview=ref<LedgerPreview|null>(null),loading=ref(false),exporting=ref(false),error=ref('');
const busy=computed(()=>loading.value||exporting.value),label=computed(()=>choices.find(c=>c.id===period.value)?.label||'');
const grouped=computed(()=>preview.value?.period!=='weekly');
const dateError=computed(()=>!range.from||!range.to?'请选择完整日期范围':range.from>range.to?'开始日期不能晚于结束日期':'');
const previewHeading=ref<HTMLElement|null>(null);
let controller:AbortController|null=null,version=0,active=true;
function signature(){return JSON.stringify({period:period.value,...range});}
watch(signature,()=>{controller?.abort();version++;loading.value=false;preview.value=null;error.value='';},{flush:'sync'});
watch(weekDay,()=>{if(period.value==='weekly')Object.assign(range,weekRange(weekDay.value));});
watch(month,()=>{if(period.value==='monthly')Object.assign(range,monthRange(month.value));});
function choose(id:string){if(id==='custom'){emit('custom');return;}period.value=id as AttendancePeriod;Object.assign(range,id==='weekly'?weekRange(weekDay.value):id==='monthly'?monthRange(month.value):{from:'',to:''});}
async function makePreview(){
 if(busy.value||dateError.value)return;const request={period:period.value,...range},fingerprint=signature(),current=++version;
 controller?.abort();controller=new AbortController();loading.value=true;error.value='';
 try{const result=await api<LedgerPreview>('/api/exports/attendance/templates/preview',{method:'POST',body:JSON.stringify(request),signal:controller.signal});if(active&&version===current&&signature()===fingerprint){preview.value=result;await nextTick();previewHeading.value?.focus({preventScroll:true});}}
 catch(cause){if(active&&version===current)error.value=cause instanceof Error?cause.message:'预览失败，请重试';}
 finally{if(active&&version===current)loading.value=false;}
}
async function download(){
 if(busy.value||!preview.value)return;const snapshot=preview.value;exporting.value=true;error.value='';
 try{const blob=await api<Blob>('/api/exports/attendance/templates/excel',{method:'POST',body:JSON.stringify({period:snapshot.period,from:snapshot.from,to:snapshot.to,revision:snapshot.revision}),headers:{Accept:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}});downloadBlob(blob,`值班记录_${label.value}_${snapshot.from}_${snapshot.to}.xlsx`);notify('值班记录已导出','success');}
 catch(cause){error.value=cause instanceof Error?cause.message:'导出失败，请重试';if(cause instanceof ApiError&&cause.status===409)preview.value=null;}
 finally{exporting.value=false;}
}
const numberFormat=new Intl.NumberFormat('zh-CN',{maximumFractionDigits:2});
function format(value:number|null|undefined){return value==null?'':numberFormat.format(value);}
onBeforeUnmount(()=>{active=false;controller?.abort();});onBeforeRouteLeave(()=>!exporting.value);onBeforeRouteUpdate(()=>!exporting.value);
</script>
<style scoped>
.attendance-export-workspace{min-width:0}.attendance-ledger-settings{display:flex;flex-wrap:wrap;align-items:end;gap:14px 18px;padding:20px;background:rgba(255,255,255,.5);border:1px solid var(--mw-line,var(--line));border-radius:var(--mw-radius,16px);margin-bottom:20px}.attendance-ledger-settings .field{display:grid;gap:8px;min-width:0;width:210px;margin:0}.attendance-ledger-settings input{width:100%;box-sizing:border-box;min-height:42px;padding:8px 12px;border:1px solid var(--mw-line,var(--line));border-radius:8px;background:white;color:var(--mw-ink);font:inherit}.attendance-ledger-actions{display:flex;gap:12px;margin-left:auto}.attendance-ledger-settings p{flex-basis:100%;margin:0}.attendance-ledger-scope,.attendance-ledger-help{font-size:12px;line-height:1.8;color:var(--mw-muted,var(--muted))}.attendance-ledger-preview{min-width:0;overflow:hidden;border:1px solid var(--mw-line,var(--line));border-radius:var(--mw-radius,16px)}.attendance-ledger-range{padding:10px 18px;margin:0}.attendance-ledger-scroll{overflow:auto;max-height:62vh;min-height:200px}.attendance-ledger-table{border-collapse:separate;border-spacing:0;table-layout:fixed;width:max-content;font-size:14px;line-height:22px}.ledger-id-col{width:150px}.ledger-name-col{width:130px}.ledger-day-col{width:135px}.ledger-sum-col{width:105px}.attendance-ledger-table :is(th,td){box-sizing:border-box;padding:12px;border-bottom:1px solid var(--mw-line,var(--line));border-right:1px solid var(--mw-line,var(--line));background:#fff;color:var(--mw-ink);white-space:nowrap}.attendance-ledger-table td{text-align:right;min-width:105px}.attendance-ledger-table th{position:sticky;top:0;z-index:3;background:#eef2f6;height:44px;text-align:center;font-weight:600}.attendance-ledger-table .ledger-day-heading th{top:44px}.attendance-ledger-table .ledger-id{position:sticky;left:0;min-width:150px;text-align:left;z-index:1}.attendance-ledger-table .ledger-name{position:sticky;left:150px;min-width:130px;text-align:left;z-index:1;box-shadow:2px 0 4px #0000000a}.attendance-ledger-table th:is(.ledger-id,.ledger-name){z-index:4}.attendance-ledger-table .ledger-subtotal{background:#edf2f7;font-weight:600}.attendance-ledger-table .ledger-total{background:#dde7f1;font-weight:600}.ledger-empty{text-align:center!important}.attendance-ledger-preview .data-table-toolbar p{font-size:13px;color:var(--mw-muted,var(--muted))}.attendance-ledger-table tbody tr:nth-child(even) td:not(.ledger-subtotal):not(.ledger-total){background:#f8fafc}@media(max-width:600px){.attendance-ledger-settings{padding:14px}.attendance-ledger-settings .field{width:100%}.attendance-ledger-actions{width:100%;margin:0;flex-wrap:wrap}.attendance-ledger-actions .button{flex:1}}
</style>
