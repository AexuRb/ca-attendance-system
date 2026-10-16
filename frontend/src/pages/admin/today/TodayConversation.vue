<template>
  <div ref="log" class="today-dialogue-log" :class="{ 'is-welcome-only': !turns.length }" role="log" aria-label="今日操作对话" aria-live="polite" aria-relevant="additions">
    <article class="today-system-message today-entry-message" :class="{ 'is-welcome': !turns.length }">
      <p>{{ entry }}</p>
    </article>
    <div v-for="(turn, index) in turns" :key="turn.id" class="today-conversation-turn">
      <p class="today-user-message">{{ turn.input }}</p>
      <article class="today-system-message" :class="{ 'is-error': turn.failed }">
        <p>{{ turn.text }}</p>
        <template v-if="turn.summary">
          <small v-if="turn.summary.scope" class="today-query-scope">{{ turn.summary.scope }}</small>
          <p class="today-query-summary"><template v-for="(part, partIndex) in turn.summary.parts" :key="partIndex"><strong v-if="part.emphasis">{{ part.text }}</strong><template v-else>{{ part.text }}</template></template></p>
        </template>
        <small v-if="!turn.shortcut && turn.command && turn.command.canonical !== turn.input">{{ turn.command.canonical }}</small>
        <div v-if="!turn.pending" class="today-message-actions">
          <button data-button-kind="entry" v-if="turn.failed && turn.command" type="button" :disabled="replying" @click="$emit('retry', turn.id)">重新查询<RotateCcw aria-hidden="true" /></button>
          <button data-button-kind="entry" v-if="turn.command" type="button" class="today-reply-action" :class="{ 'is-primary': index === turns.length - 1 }" :disabled="opening" @click="$emit('open', turn.command)">{{ turn.button }}<ArrowRight aria-hidden="true" /></button>
        </div>
        <p v-if="turn.navigationError" class="today-navigation-error" role="alert">页面没能打开，请再试一次。</p>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, RotateCcw } from '@lucide/vue';
import { nextTick, ref, watch } from 'vue';
import type { ResolvedCommand } from '../../../features/command-center/commandTypes';
import type { QuerySummary } from './conversationQuery';
export interface ConversationTurn { id: number; input: string; shortcut?: boolean; text: string; button: string; command?: ResolvedCommand; summary?: QuerySummary; pending?: boolean; failed?: boolean; navigationError?: boolean }
const props = defineProps<{ entry: string; turns: ConversationTurn[]; opening?: boolean; replying?: boolean }>();
defineEmits<{ open: [ResolvedCommand]; retry: [number] }>();
const log = ref<HTMLElement>();
watch(() => [props.entry, props.turns.map(t => `${t.id}:${t.pending}`).join('|')], async () => {
  await nextTick();
  if (log.value) log.value.scrollTop = log.value.scrollHeight;
}, { immediate: true });
</script>
