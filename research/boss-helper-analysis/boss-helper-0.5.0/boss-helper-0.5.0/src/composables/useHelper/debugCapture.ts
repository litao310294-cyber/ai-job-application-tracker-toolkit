import type { JobData } from './type'
import { counter } from '@/message'
import type { Ref } from 'vue'

import type { BossZpDetailData, BossZpJobItemData } from '@/entrypoints/boss/types'

export interface JobCaptureResponse {
  success: boolean
  jobRecordId: number
  created: boolean
  completenessScore?: number
  extractionMode?: string
}

type DebugCaptureWorkflowData = {
  jobData: JobData
  rawData: {
    jobitem: BossZpJobItemData
  }
}

export type DebugCaptureHelper = {
  jobMaps: Map<string, DebugCaptureWorkflowData>
  _jobDetail: Ref<BossZpDetailData | undefined>
  _clickJobCardAction: (job: BossZpJobItemData) => void
}

function isMatchingJobDetail(detail: BossZpDetailData, job: BossZpJobItemData): boolean {
  return detail.lid === job.lid || detail.jobInfo?.encryptId === job.encryptJobId
}

function waitForJobDetail(
  helper: DebugCaptureHelper,
  job: BossZpJobItemData,
): Promise<BossZpDetailData> {
  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      window.clearInterval(interval)
      reject(new Error('job detail loading timed out'))
    }, 60_000)
    const interval = window.setInterval(() => {
      const detail = helper._jobDetail.value
      if (!detail || !isMatchingJobDetail(detail, job)) return
      window.clearTimeout(timeout)
      window.clearInterval(interval)
      resolve(detail)
    }, 100)
  })
}

export async function resolveJobWithDetail(
  job: JobData,
  helper: DebugCaptureHelper,
): Promise<JobData> {
  if (job.jobDescription?.trim()) return job

  const workflowData = helper.jobMaps.get(job.key)
  const jobItem = workflowData?.rawData?.jobitem
  if (!jobItem) {
    throw new Error('current job has no source JobData for detail loading')
  }

  helper._clickJobCardAction(jobItem)
  const detail = await waitForJobDetail(helper, jobItem)
  const jobDescription = detail.jobInfo?.postDescription?.trim() ?? ''
  if (!jobDescription) {
    throw new Error('job detail loaded but jobDescription is empty')
  }

  const enrichedJob: JobData = {
    ...job,
    jobDescription,
    city: detail.jobInfo.locationName || job.city,
    address: detail.jobInfo.address || job.address,
    addressCoords: [detail.jobInfo.longitude, detail.jobInfo.latitude],
    activeTime: detail.brandComInfo.activeTime || job.activeTime,
    activeTimeStr: detail.bossInfo.activeTimeDesc || job.activeTimeStr,
    boss: {
      ...job.boss,
      isOnline: detail.bossInfo.bossOnline,
      isCertificated: detail.bossInfo.certificated,
    },
    brand: {
      ...job.brand,
      labels: detail.brandComInfo.labels,
      introduce: detail.brandComInfo.introduce,
      stageName: detail.brandComInfo.stageName,
    },
  }

  workflowData.jobData = enrichedJob
  return enrichedJob
}

export function buildStructuredJobInfo(job: JobData) {
  return {
    jobTitle: job.jobName ?? '',
    companyName: job.brand?.name ?? '',
    salary: job.salary ?? '',
    city: job.city ?? job.address ?? '',
    education: job.degreeName ?? '',
    experience: job.experienceName ?? '',
    skills: Array.isArray(job.skills) ? job.skills : [],
    jobTags: Array.isArray(job.jobLabels) ? job.jobLabels : [],
    rawJD: job.jobDescription ?? '',
    extractionMode: 'VUE',
  }
}

export async function sendCurrentJobToBackend(job: JobData): Promise<JobCaptureResponse> {
  const response = await counter.request({
    url: 'http://localhost:8080/api/job/capture',
    data: {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(buildStructuredJobInfo(job)),
    },
    timeout: 15,
    responseType: 'json',
  }) as DebugCaptureResponse

  if (!response || response.success !== true) {
    throw new Error('job capture failed')
  }
  return response
}
