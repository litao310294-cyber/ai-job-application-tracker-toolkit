<script setup lang="ts">
import { computed, ref } from 'vue'

import { JobStatus } from '@/composables/useApplying/type'
import {
  resolveJobWithDetail,
  sendCurrentJobToBackend,
  type DebugCaptureHelper,
} from '@/composables/useHelper/debugCapture'
import { JobData, useHelper } from '@/composables/useHelper'

const helper = useHelper()

const props = defineProps<{
  job: JobData
  hover?: boolean
}>()

const jobResult = computed(() => {
  return helper.jobResultMaps.get(props.job.key)
})

const currentJobData = computed(() => {
  return helper.jobMaps.get(props.job.key)?.jobData ?? props.job
})

const stateMaps: Record<JobStatus, string> = {
  pending: '#CECECE',
  wait: '#CECECE',
  error: '#e74c3c',
  warn: '#f39c12',
  success: '#2ecc71',
  running: '#98F5F9',
  request: '#3498db',
  ai: '#9b59b6',
}

const jobStatus = computed(() => {
  const status = jobResult.value?.status ?? 'pending'
  const data = stateMaps[status]
  return {
    status,
    color: data,
    show: jobResult.value?.status !== 'pending' ? 'flex' : 'none',
  }
})

const showDescription = ref(false)
const debugCaptureState = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const debugCaptureMessage = ref('')

async function showDescriptionHandler() {
  showDescription.value = true
  // if (props.job.card == null) {
  //   await props.job.getCard()
  // }
}

async function sendCurrentJob() {
  if (debugCaptureState.value === 'loading') return
  debugCaptureState.value = 'loading'
  debugCaptureMessage.value = ''
  try {
    const job = await resolveJobWithDetail(
      currentJobData.value,
      helper as unknown as DebugCaptureHelper,
    )
    const response = await sendCurrentJobToBackend(job)
    debugCaptureState.value = 'success'
    debugCaptureMessage.value = response.created
      ? `saved job_record #${response.jobRecordId}`
      : 'saved'
  } catch (error) {
    debugCaptureState.value = 'error'
    debugCaptureMessage.value = error instanceof Error ? error.message : 'send failed'
  }
}

function getActiveTimeType(job: JobData): 'success' | 'warning' | 'error' {
  const activeTime = job.activeTime
  if (!activeTime) return 'error'

  const now = Date.now()
  const diffDays = (now - activeTime) / (1000 * 60 * 60 * 24)

  if (diffDays <= 2) return 'success'
  if (diffDays <= 7) return 'warning'
  return 'error'
}
</script>

<template>
  <div
    class="job-card"
    :class="{ 'job-card-hover': hover }"
    :style="{
      '--state-color': jobStatus.color,
      '--state-show': jobStatus.show,
    }"
    v-if="job"
  >
    <div class="card-tag">{{ job.brand.industry }},{{ job.degreeName }},{{ job.brand.scale }}</div>
    <!-- `https://www.zhipin.com/job_detail/${job.encryptJobId}.html`" -->
    <a :href="job.link" target="_blank" class="card-title">
      {{ job.jobName }}
    </a>
    <h3 class="card-salary">
      {{ job.salary }}
    </h3>
    <div
      v-show="showDescription"
      class="card-content"
      :title="job.jobDescription"
      @click="showDescription = false"
    >
      {{ job.jobDescription }}
    </div>
    <div v-show="!showDescription" class="card-content" @click="showDescriptionHandler">
      <div>
        <div class="flex flex-wrap gap-1">
          <UBadge v-for="tag in job.skills" :key="tag" size="sm" variant="subtle" color="warning">
            {{ tag }}
          </UBadge>
          <UBadge
            v-for="tag in job.jobLabels"
            :key="tag"
            size="sm"
            variant="subtle"
            color="success"
          >
            {{ tag }}
          </UBadge>
        </div>
      </div>
      <div class="card-footer" v-if="job.welfareList && job.welfareList.length > 0">
        {{ job.welfareList.join(',') }}
      </div>
    </div>

    <div v-if="job.activeTime || job.activeTimeStr" class="active-time-tag">
      <UBadge :color="getActiveTimeType(job)" variant="subtle">
        活跃时间：{{
          job.activeTime ? new Date(job.activeTime).toLocaleString() : job.activeTimeStr
        }}
      </UBadge>
    </div>

    <div class="author-row">
      <img alt="" class="avatar" height="80" :src="job.brand.logo" width="80" />
      <div>
        <span class="company-name">{{ job.brand.name }}</span>
        <!-- <h4>{{ job.cityName }}/{{ job.areaDistrict }}/{{ job.businessDistrict }}</h4> -->
        <h4>{{ job.address }}</h4>
      </div>
    </div>
    <div
      class="card-status flex-row gap-2 justify-center items-center"
      v-if="jobResult"
      :title="jobResult?.reason || jobResult?.msg"
    >
      <UIcon v-if="jobStatus.status === 'running'" name="i-line-md-loading-twotone-loop" />
      <UIcon v-else-if="jobStatus.status === 'request'" name="i-svg-spinners-wifi-fade" />
      <UIcon v-else-if="jobStatus.status === 'ai'" name="i-line-md-hazard-lights-loop" />
      {{ jobResult?.msg || jobResult?.reason || '无内容' }}
    </div>
    <div class="card-actions flex-row gap-2 justify-center items-center">
      <UButton
        size="xs"
        color="primary"
        variant="outline"
        :loading="debugCaptureState === 'loading'"
        title="Send current job to backend"
        @click.stop="sendCurrentJob"
      >
        Send current job to backend
      </UButton>
    </div>
    <div
      v-if="debugCaptureState !== 'idle'"
      class="text-xs text-center"
      :class="debugCaptureState === 'error' ? 'text-red-500' : 'text-green-500'"
    >
      {{ debugCaptureMessage || debugCaptureState }}
    </div>
  </div>
</template>
