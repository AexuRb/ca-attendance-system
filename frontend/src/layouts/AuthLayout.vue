<template>
  <main class="auth-layout auth-signal-layout">
    <header class="auth-signal-header">
      <div class="auth-brand-lockup">
        <span class="auth-brand-mark">
          <img src="/brand/ca-logo-white.png" alt="计算机协会会徽" />
        </span>
        <div>
          <strong>计算机协会</strong>
          <span>值班管理系统</span>
        </div>
      </div>
      <div class="auth-signal-context">
        <i aria-hidden="true"></i>
        <span>本机管理后台</span>
      </div>
    </header>

    <section class="auth-form-panel">
      <div class="auth-form-shell"><slot /></div>
    </section>

    <footer class="auth-signal-footer">
      <span>COMPUTER ASSOCIATION</span>
      <span>SQLITE · OFFLINE</span>
    </footer>
  </main>
</template>
