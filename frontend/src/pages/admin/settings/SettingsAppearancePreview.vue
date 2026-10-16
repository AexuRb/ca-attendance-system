<template>
  <span class="settings-appearance-preview appearance-style-sample" :data-preview-theme="appearance" :data-palette-preview="palette" aria-hidden="true">
    <span class="sample-workspace">
      <span class="sample-heading">今日概览</span>
      <span class="sample-copy">欢迎回来。</span>
      <span class="sample-bubble">暂无待审核记录。</span>
      <span class="sample-toolbar">
        <span class="sample-composer">输入操作…</span>
        <span class="mw-button primary sample-action">确认</span>
      </span>
    </span>
  </span>
</template>
<script setup lang="ts">
defineProps<{ appearance: string; palette?: string }>();
</script>
<style>
/* Display-only samples: identical content and geometry, isolated palette paint.
   The ID boundary keeps old page-level miniature rules from leaking in. */
#settings-appearance .appearance-style-sample {
  --mw-font:"Noto Sans SC","Segoe UI","Microsoft YaHei UI",sans-serif;
  --mw-speed:150ms;
  --sample-radius:8px;
  display:block; min-width:0; width:100%; height:176px; padding:8px;
  border:1px solid var(--mw-line); border-radius:var(--sample-radius);
  background:radial-gradient(ellipse at 5% 0%,var(--theme-glow),transparent 75%),radial-gradient(ellipse at 100% 100%,var(--theme-corner),transparent 65%),var(--theme-canvas);
  color:var(--mw-ink); font-family:var(--mw-font); text-align:left;
}
#settings-appearance .appearance-style-sample[data-preview-theme=editorial] {
  --mw-font:"Noto Serif SC","Source Han Serif SC","SimSun",serif;
  --mw-speed:210ms; --sample-radius:14px;
}
#settings-appearance .appearance-style-sample[data-preview-theme=spatial] {
  --mw-speed:180ms; --sample-radius:20px;
}
#settings-appearance .sample-workspace {
  display:grid; grid-template-rows:24px 18px minmax(0,1fr) 42px; gap:6px;
  height:100%; min-width:0; padding:8px; border-radius:calc(var(--sample-radius) - 4px);
  background:#ffffffed;
}
#settings-appearance .sample-heading { display:block; margin:0; font:500 17px/24px var(--mw-font); color:var(--mw-ink); }
#settings-appearance .sample-copy { display:block; margin:0; font:400 12px/18px var(--mw-font); color:var(--mw-muted); }
#settings-appearance .sample-bubble { display:flex; align-items:center; padding:3px 7px; border-radius:6px; background:var(--theme-bubble); color:var(--mw-ink); font:400 11px/18px var(--mw-font); }
#settings-appearance .sample-toolbar { display:grid; grid-template-columns:minmax(0,1fr) 64px; gap:6px; min-width:0; }
#settings-appearance .sample-composer { display:flex; align-items:center; min-width:0; padding:0 8px; border:1px solid var(--mw-line); border-radius:8px; background:var(--theme-composer); color:var(--mw-muted); white-space:nowrap; font:400 11px/18px var(--mw-font); }
#settings-appearance .sample-action { display:inline-flex; align-items:center; justify-content:center; align-self:stretch; margin:0; min-width:0; width:100%; height:42px; padding:0 8px; font-family:var(--mw-font); cursor:inherit; }
#settings-appearance .appearance-choice:not(:disabled):is(:hover,:focus-visible) .sample-action { --ui-button-fill:var(--theme-hover); }
</style>
