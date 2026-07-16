<script setup lang="ts">
import type { AlertProps } from '@nuxt/ui/components/Alert.vue'
import { computed, onMounted, ref } from 'vue'

import { counter } from '@/message'

export interface ExtendedAlertProps extends AlertProps {
  id?: string
  showIcon?: boolean
}

const props = withDefaults(defineProps<ExtendedAlertProps>(), {
  color: 'info',
  variant: 'subtle',
  orientation: 'horizontal',
})

const storageKey = computed(() => `local:alert:${props.id}`)
const isVisible = ref(true)

onMounted(async () => {
  const shouldHide = await counter.storageGet(storageKey.value, false)
  isVisible.value = !shouldHide
})

const handleClose = async () => {
  await counter.storageSet(storageKey.value, true)
  isVisible.value = false
}

const icon = computed(() => {
  if (props.icon) return props.icon
  if (props.showIcon === false) return null
  switch (props.color) {
    case 'success':
      return 'solar:check-circle-outline'
    case 'error':
      return 'solar:close-circle-outline'
    case 'warning':
      return 'solar:shield-warning-outline'
    default:
      return 'solar:info-circle-outline'
  }
})
</script>

<template>
  <UAlert
    v-if="isVisible"
    v-bind="props"
    @close="handleClose"
    :close="props.close ? props.close : props.id ? true : undefined"
    :icon="icon"
  >
    <slot />
    <template #title>
      <slot name="title"></slot>
    </template>
    <template #description>
      <slot name="description"></slot>
    </template>
  </UAlert>
</template>
