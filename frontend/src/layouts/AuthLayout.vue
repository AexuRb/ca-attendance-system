<template>
  <main class="auth-layout">
    <div class="auth-brand-panel">
      <div class="auth-brand-lockup">
        <img src="/brand/ca-logo-white.png" alt="计算机协会" />
        <div>
          <span>COMPUTER ASSOCIATION</span><strong>本地管理系统</strong>
        </div>
      </div>
      <div class="auth-brand-message">
        <p>LOCAL OPERATIONS</p>
        <h1>把值班、事务和交接，留在一台可靠的电脑里。</h1>
        <div class="auth-signal"><span></span>SQLite 本地数据 · 无需互联网</div>
      </div>
      <p class="auth-brand-foot">中国地质大学（北京）计算机协会</p>
    </div>
    <section class="auth-form-panel"><slot /></section>
  </main>
</template>
