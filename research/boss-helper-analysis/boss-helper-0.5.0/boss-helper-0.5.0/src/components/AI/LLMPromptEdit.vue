<script lang="ts" setup>
import { h, reactive, ref } from 'vue'

import JobCard from '@/components/JobCard.vue'
import { formInfoData, defaultFormData, useConf } from '@/composables/conf'
import { useModel } from '@/composables/useModel'

//TODO import { parseFiltering } from '@/entrypoints/boss/requests'
import { JobData, useHelper } from '@/composables/useHelper'
import type { FormInfoAi, Prompt } from '@/types/formData'
import { logger } from '@/utils/logger'

import Alert from '../Alert.vue'

let parseFiltering = () => {}

const props = defineProps<{
  data: 'aiGreeting' | 'aiFiltering' | 'aiReply'
}>()
const toast = useToast()
const helper = useHelper()
const conf = useConf()
const model = useModel()
const show = defineModel<boolean>({ required: true })
const currentModel = ref(conf.formData[props.data].model)

const score = ref(props.data === 'aiFiltering' ? (conf.formData[props.data].score ?? 10) : 10)

const role = ['system', 'user', 'assistant']

const message = ref<Prompt>(jsonClone(conf.formData[props.data].prompt))

function inputExample() {
  message.value = jsonClone(defaultFormData[props.data].prompt)
}

function removeMessage(item: Prompt[number]) {
  message.value = message.value.filter((v) => v !== item)
}

function addMessage() {
  message.value.push({ role: 'user', content: '' })
}

const testDialog = ref(false)

interface TestData {
  key: string
  job: JobData
  checked: boolean | string | number
  loading: boolean
}
interface TestContent {
  time: string
  prompt?: string
  reasoning_content?: string | null
  content?: string
}

const testData = reactive<Array<TestData>>([])
const expandTestRowKeys = ref<string[]>([])
const testDataContent = reactive<Record<string, TestContent[]>>({})

function handleExpandChange(row: TestData) {
  logger.info('handleExpandChange', row)
  if (expandTestRowKeys.value.includes(row.key)) {
    expandTestRowKeys.value = expandTestRowKeys.value.filter((v) => v !== row.key)
  } else {
    expandTestRowKeys.value.push(row.key)
  }
}

function test() {
  testDialog.value = true
}

const testJobLoading = ref(false)
const testJobStop = ref(true)

async function addTestJob(n: number) {
  testJobLoading.value = true
  try {
    let count = 0
    for (let item of helper.jobList.value) {
      if (testData.some((v) => v.job.key === item.key)) {
        continue
      }
      const data = helper.jobMaps.get(item.key) // 加载更多数据
      if (data) {
        item = data.jobData
      }
      testData.push({ key: item.key, job: item, checked: false, loading: false })
      testDataContent[item.key] = []
      count++
      if (count >= n) {
        break
      }
    }
  } finally {
    testJobLoading.value = false
  }
}

async function testJob() {
  if (!testJobStop.value) {
    testJobStop.value = true
    return
  }
  testJobLoading.value = true
  testJobStop.value = false
  const md = model.modelData.value.find((v) => currentModel.value === v.key)
  if (!currentModel.value || !md) {
    toast.add({
      title: '请在上级弹窗右上角选择模型',
      color: 'warning',
    })
    return
  }
  try {
    // const gpt = model.getModel(md!, message.value)
    const handle = async (item: TestData) => {
      if (testJobStop.value) {
        return
      }
      try {
        // item.loading = true
        // let { content, prompt, reasoning_content } = await gpt.doStream(
        //   {
        //     data: {
        //       data: item.job,
        //       card: item.job.card!,
        //     },
        //     test: true,
        //     json: props.data === 'aiFiltering',
        //   },
        //   props.data,
        // )
        // if (props.data === 'aiFiltering' && content) {
        //   const { message } = parseFiltering(content)
        //   content = message ?? content
        // }
        // testDataContent[item.key].push({
        //   time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
        //   prompt,
        //   reasoning_content,
        //   content,
        // })
      } catch (err: any) {
        logger.error(err)
        toast.add({
          title: err.message,
          color: 'error',
        })
      } finally {
        item.loading = false
      }
    }

    for (let i = 0; i < testData.length; i += 4) {
      const batch = testData.slice(i, i + 4)
      await Promise.all(batch.map(handle))
    }
  } catch (err: any) {
    logger.error(err)
    toast.add({
      title: err.message,
      color: 'error',
    })
  } finally {
    testJobLoading.value = false
    testJobStop.value = true
  }
}

async function savePrompt() {
  if (currentModel.value == null) {
    toast.add({
      title: '请在右上角选择模型',
      color: 'warning',
    })
    return
  }
  conf.formData[props.data].model = currentModel.value
  conf.formData[props.data].prompt = message.value

  if (props.data === 'aiFiltering') {
    conf.formData[props.data].score = score.value
  }
  await conf.confSaving()
  show.value = false
}

const promptModelRef = useTemplateRef('promptModel')
const testModelRef = useTemplateRef('testModel')

onMounted(() => {
  logger.info('LLMPromptEdit mounted', { promptModelRef, testModelRef })
})
</script>

<template>
  <UModal
    v-model:open="show"
    :title="formInfoData[data].label"
    :ui="{ content: 'sm:max-w-[70%]', body: 'flex flex-col gap-4' }"
    :dismissible="false"
  >
    <template #body>
      <div v-if="data === 'aiFiltering'">
        <UFormField label="过滤分数">
          <UInputNumber v-model="score" :min="-100" :max="100" size="sm" placeholder="请输入分数" />
        </UFormField>
      </div>
      <div class="w-full flex items-center justify-between" ref="promptModel">
        <div class="flex gap-2">
          <UButton color="neutral"> 多对话模式 </UButton>
          <UButton color="primary" @click="addMessage"> 添加消息 </UButton>
        </div>
        <div class="flex gap-2">
          <UButton color="info" @click="inputExample"> 填入示例值 </UButton>
          <USelectMenu
            v-model="currentModel"
            :items="model.modelData.value"
            labelKey="name"
            valueKey="key"
            placeholder="选择模型"
            :portal="promptModelRef?.parentElement ?? false"
          >
            <!-- <template #item="{ item }">
              <div style="display: flex">
                <span
                  v-if="item.vip != null"
                  style="align-items: center; display: inline-flex; margin-right: 6px"
                  v-html="llmIcon.vip"
                />
                <span>{{ item.name }}</span>
              </div>
            </template>
            <template #create-item-label="{ item }">
              <div style="display: flex">
                <span
                  v-if="item.startsWith('vip-')"
                  style="align-items: center; display: inline-flex; margin-right: 6px"
                  v-html="llmIcon.vip"
                />
                <span>{{ item }}</span>
              </div>
            </template> -->
          </USelectMenu>
        </div>
      </div>
      <div v-pre>
        <Alert v-if="currentModel?.startsWith('vip-')" id="vip-alert" title="注意" type="warning">
          会员模型暂时不支持输出 思考过程, 比如deepseekR1，但是不影响模型能力
        </Alert>
        使用 {{}} 来渲染变量。
        <ULink
          to="https://github.com/Ocyss/boss-helper/blob/master/src/types/bossData.d.ts"
          target="_blank"
        >
          变量表
        </ULink>
        <br />
        推荐阅读
        <ULink to="https://langgptai.feishu.cn/wiki/RXdbwRyASiShtDky381ciwFEnpe" target="_blank">
          《LangGPT》
        </ULink>
        的提示词文档学习 ( 示例提示词写的并不好,欢迎AI大佬来提pr )
      </div>
      <div class="demo-dynamic space-y-3">
        <div v-for="(item, index) in message" :key="index" class="flex items-start gap-2">
          <div class="flex flex-col gap-3 w-27.5">
            <USelectMenu
              v-model="item.role"
              :items="role"
              :portal="promptModelRef?.parentElement ?? false"
              :content="{ side: 'right' }"
            />
            <UButton
              color="error"
              variant="outline"
              @click.prevent="removeMessage(item)"
              class="w-full"
            >
              删除
            </UButton>
          </div>
          <UTextarea v-model="item.content" autoresize :rows="2" :maxrows="6" class="flex-1" />
        </div>
      </div>
    </template>

    <template #footer>
      <UButton
        color="neutral"
        variant="outline"
        @click="
          () => {
            show = false
          }
        "
      >
        关闭
      </UButton>
      <UButton color="neutral" variant="soft" @click="test"> 测试 </UButton>
      <UButton color="primary" @click="savePrompt"> 保存 </UButton>
    </template>
  </UModal>
  <UModal
    v-model:open="testDialog"
    title="Prompt 测试"
    :ui="{ content: 'sm:max-w-3xl' }"
    :dismissible="false"
    scrollable
  >
    <template #body>
      <div class="flex gap-2 mb-4" ref="testModel">
        <UButton :loading="testJobLoading" @click="addTestJob(1)" color="neutral">
          从页面添加1个岗位
        </UButton>
        <UButton :loading="testJobLoading" @click="addTestJob(4)" color="neutral">
          从页面添加4个岗位
        </UButton>
        <UButton :loading="testJobLoading" @click="addTestJob(10)" color="neutral">
          从页面添加10个岗位
        </UButton>
      </div>
      <div class="overflow-auto">
        <table class="w-full border-collapse">
          <thead>
            <tr class="border-b border-gray-200">
              <th style="width: 32px"></th>
              <th style="width: 180px; text-align: left; padding: 8px">岗位名</th>
              <th style="text-align: left; padding: 8px">内容</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in testData" :key="row.key">
              <tr
                class="border-b border-gray-100 cursor-pointer hover:bg-gray-50"
                @click="handleExpandChange(row)"
              >
                <td style="padding: 4px; text-align: center">
                  <UButton
                    variant="ghost"
                    size="xs"
                    :icon="
                      expandTestRowKeys.includes(row.key)
                        ? 'i-lucide-chevron-down'
                        : 'i-lucide-chevron-right'
                    "
                  />
                </td>
                <td style="width: 180px; padding: 8px">
                  <UPopover mode="hover" :portal="testModelRef?.parentElement ?? false">
                    <div class="flex items-center">
                      <UIcon v-if="row.loading" name="i-line-md-loading-twotone-loop" />
                      {{ row.job.jobName }}
                    </div>
                    <template #content>
                      <JobCard :job="row.job" :hover="false" style="width: 300px" />
                    </template>
                  </UPopover>
                </td>
                <td style="padding: 8px">
                  <div
                    :title="row.job.jobDescription"
                    style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
                  >
                    {{ row.job.jobDescription }}
                  </div>
                </td>
              </tr>
              <tr v-if="expandTestRowKeys.includes(row.key)">
                <td colspan="3">
                  <div class="test-content-wrapper">
                    <div class="test-content-list">
                      <div
                        v-for="item in testDataContent[row.key].slice(-3)"
                        :key="item.time"
                        class="test-content-item"
                      >
                        <div class="test-content-time">
                          {{ item.time }}
                        </div>
                        <div v-if="item.prompt" class="test-content-prompt" :title="item.prompt">
                          {{ item.prompt }}
                        </div>
                        <div
                          v-if="item.reasoning_content"
                          class="test-content-reasoning-content"
                          :title="item.reasoning_content"
                        >
                          {{ item.reasoning_content }}
                        </div>
                        <div class="test-content-content" :title="item.content">
                          {{ item.content }}
                        </div>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
    <template #footer>
      <UButton
        color="neutral"
        variant="outline"
        @click="
          () => {
            testDialog = false
          }
        "
      >
        取消
      </UButton>
      <UButton color="primary" @click="testJob">
        {{ testJobStop ? '开始测试' : '停止测试' }}
      </UButton>
    </template>
  </UModal>
</template>
x
