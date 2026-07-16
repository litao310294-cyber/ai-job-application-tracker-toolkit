<script lang="ts" setup>
import type { AccordionItem } from '@nuxt/ui'
import { fi } from '@nuxt/ui/runtime/locale/index.js'
import { computed } from 'vue'

import { ref } from '#imports'
import Alert from '@/components/Alert.vue'
import formItem from '@/components/Form/FormItem.vue'
import formSelect from '@/components/Form/FormSelect.vue'
import SalaryRangeComponent from '@/components/Form/SalaryRange.vue'
import { formInfoData, useConf } from '@/composables/conf'
import { getCacheManager } from '@/composables/useApplying'
import { useHelper } from '@/composables/useHelper'
import { counter } from '@/message/index.js'
import { CustomGreetingItem, CustomGreetingItemImage } from '@/types/formData.js'
import { amapGeocode } from '@/utils/amap'
import { logger } from '@/utils/logger'

import Appearance from './Appearance.vue'

const helper = useHelper()
const conf = useConf()
const toast = useToast()
const items = computed<AccordionItem[]>(() => {
  const configs = [
    {
      label: '筛选配置',
      slot: 'filter',
    },
    conf.configLevel.intermediate && {
      label: '招呼语配置',
      slot: 'greetings',
    },
    {
      label: '外观配置',
      slot: 'appearance',
    },
    conf.configLevel.advanced && {
      label: '地址配置',
      slot: 'address',
    },
    conf.configLevel.intermediate && {
      label: '延迟配置',
      slot: 'delay',
    },
  ] satisfies (AccordionItem | boolean | null | undefined | '')[]

  return configs.filter((item) => !!item) as AccordionItem[]
})

const salaryRangeAdvanced = ref(false)
const salaryRangeRef = ref()
const amapGeocodeLoading = ref(false)
async function amapGeocodeHandler() {
  amapGeocodeLoading.value = true
  try {
    const res = await amapGeocode(conf.formData.amap.origins)
    if (res) {
      conf.formData.amap.origins = res.location
    } else {
      toast.add({
        title: '获取地址失败',
        color: 'error',
      })
    }
  } catch (error) {
    toast.add({
      title: '获取地址失败',
      color: 'error',
    })
    logger.error(error)
  } finally {
    amapGeocodeLoading.value = false
  }
}

function syncSalaryRange() {
  conf.formData.salaryRange.advancedValue.M[0] = Math.round(
    conf.formData.salaryRange.value[0] * 1000,
  )
  conf.formData.salaryRange.advancedValue.M[1] = Math.round(
    conf.formData.salaryRange.value[1] * 1000,
  )

  conf.formData.salaryRange.advancedValue.D[0] = Math.round(
    conf.formData.salaryRange.advancedValue.M[0] / 21.75,
  )
  conf.formData.salaryRange.advancedValue.D[1] = Math.round(
    conf.formData.salaryRange.advancedValue.M[1] / 21.75,
  )

  conf.formData.salaryRange.advancedValue.H[0] = Math.round(
    conf.formData.salaryRange.advancedValue.D[0] / 8,
  )
  conf.formData.salaryRange.advancedValue.H[1] = Math.round(
    conf.formData.salaryRange.advancedValue.D[1] / 8,
  )
}
const advancedGreetingModelRef = useTemplateRef('advancedGreetingModel')
function gotoZhipinNotifySetting() {
  window.open('https://www.zhipin.com/web/geek/notify-set?type=greetSet', '_blank')
}
function gotoAmapDevSetting() {
  window.open('https://lbs.amap.com/dev/', '_blank')
}

const advancedGreetingValue = ref<CustomGreetingItem[]>([])

function removeMessage(item: CustomGreetingItem) {
  advancedGreetingValue.value = advancedGreetingValue.value.filter((v) => v !== item)
}

function addMessage(type: CustomGreetingItem['type'] = 'text', index?: number) {
  let item: CustomGreetingItem | undefined
  if (type === 'text') {
    item = { type, content: '' }
  } else if (type === 'image') {
    item = {
      type,
      // image: {
      //   [helper.key]: { url: '' },
      // },
      image: '',
    }
  }

  if (!item) {
    return
  }

  if (
    typeof index === 'number' &&
    Number.isInteger(index) &&
    index >= 0 &&
    index <= conf.formData.customGreeting.value.length
  ) {
    advancedGreetingValue.value.splice(index, 0, item)
  } else {
    advancedGreetingValue.value.push(item)
  }
}

function advancedGreetingEnter(open: boolean) {
  if (!open) {
    return
  }
  if (!Array.isArray(conf.formData.customGreeting.value)) {
    advancedGreetingValue.value = [
      {
        type: 'text',
        content: conf.formData.customGreeting.value,
      },
    ]
  } else {
    advancedGreetingValue.value = JSON.parse(
      JSON.stringify(conf.formData.customGreeting.value),
    ) as CustomGreetingItem[]
  }
  advancedGreetingValue.value.forEach(async (item) => {
    if (item.type === 'image' && item.image && !item.model) {
      const response = await counter.getImage(item.image)
      if (!response.success) {
        throw new Error('图片未上传或已过期')
      }
      const u8Array = new Uint8Array(response.buffer)
      item.model = new File([u8Array.buffer], response.name, { type: response.type })
    }
  })
}

const advancedGreetinSaveLoading = ref(false)

async function advancedGreetinSave(close: () => void) {
  advancedGreetinSaveLoading.value = true
  try {
    for (const index in advancedGreetingValue.value) {
      const item = advancedGreetingValue.value[index]
      if (item.type === 'image') {
        if (item.model instanceof File) {
          const file = item.model as File
          const uploadedImage = await counter.setImage({
            name: file.name,
            type: file.type,
            buffer: Array.from(new Uint8Array(await file.arrayBuffer())),
          })
          item.image = uploadedImage.key
        }
        advancedGreetingValue.value[index] = { ...item, model: undefined }
      }
    }
    conf.formData.customGreeting.value = JSON.parse(
      JSON.stringify(advancedGreetingValue.value),
    ) as CustomGreetingItem[]
    close()
  } finally {
    advancedGreetinSaveLoading.value = false
  }
}

// function openImg(val: CustomGreetingItemImage['image'][string]) {
//   window.open(val.url ?? val.base64, '_blank')
// }
</script>

<template>
  <div class="flex flex-col gap-2">
    <UTheme
      :ui="{
        formField: {
          root: 'flex max-sm:flex-col justify-between gap-4 items-center',
          container: 'flex-1',
        },
        input: {
          root: 'w-full',
        },
        inputMenu: {
          root: 'w-full',
        },
        inputTags: {
          root: 'w-full',
        },
      }"
    >
      <Alert
        id="config-alert-1"
        show-icon
        title="进行配置前都请先阅读完整的帮助文档，再进行配置，如有bug请反馈"
        type="success"
        description="滚动到底部，差不多150个岗位左右，也会自动停止, 刷新或者变更期望重新获取新的岗位即可。"
      />
      <Alert
        v-if="helper.key === 'boss'"
        id="config-alert-2"
        show-icon
        color="success"
        description="使用自定义招呼语前 推荐禁用boss直聘自带招呼语"
        :actions="[
          {
            label: '前往',
            color: 'neutral',
            variant: 'subtle',
            onClick: gotoZhipinNotifySetting,
          },
        ]"
      >
      </Alert>
      <Alert
        id="config-alert-3"
        type="success"
        description="所有配置选项皆有帮助提示，不懂用法请进入帮助模式进行查看，若是对帮助说明有疑问请反馈最好能给出改进意见。"
      />
      <UForm :disabled="helper.workflowRunning.value || conf.isLoading.value">
        <UAccordion
          type="single"
          collapsible
          :items="items"
          :ui="{ content: 'data-[state=open]:pt-1 data-[state=open]:pb-3 px-2 gap-3' }"
          :unmount-on-hide="false"
          default-value="filter"
        >
          <template #filter>
            <Alert
              id="filter-config-alert-enable"
              title="复选框打钩才会启用，别忘记打钩启用哦。保存也别忘了"
              description="排除和包含可点击切换，混合模式适用性过低难以配置不会考虑开发"
              type="success"
              show-icon
            />
            <div class="grid grid-cols-2 gap-2 mt-2" style="width: 100%" data-help="筛选配置">
              <form-item
                v-bind="formInfoData.company"
                v-model:enable="conf.formData.company.enable"
                v-model:include="conf.formData.company.include"
                :disabled="helper.workflowRunning.value"
              >
                <formSelect
                  v-model:value="conf.formData.company.value"
                  v-model:options="conf.formData.company.options"
                />
              </form-item>
              <form-item
                v-bind="formInfoData.jobTitle"
                v-model:enable="conf.formData.jobTitle.enable"
                v-model:include="conf.formData.jobTitle.include"
                :disabled="helper.workflowRunning.value"
              >
                <form-select
                  v-model:value="conf.formData.jobTitle.value"
                  v-model:options="conf.formData.jobTitle.options"
                />
              </form-item>
              <form-item
                v-bind="formInfoData.jobContent"
                v-model:enable="conf.formData.jobContent.enable"
                v-model:include="conf.formData.jobContent.include"
                :disabled="helper.workflowRunning.value"
              >
                <form-select
                  v-model:value="conf.formData.jobContent.value"
                  v-model:options="conf.formData.jobContent.options"
                />
              </form-item>
              <form-item
                v-if="conf.configLevel.intermediate"
                v-bind="formInfoData.hrPosition"
                v-model:enable="conf.formData.hrPosition.enable"
                v-model:include="conf.formData.hrPosition.include"
                :disabled="helper.workflowRunning.value"
              >
                <form-select
                  v-model:value="conf.formData.hrPosition.value"
                  v-model:options="conf.formData.hrPosition.options"
                />
              </form-item>
              <form-item
                v-if="conf.configLevel.intermediate"
                v-bind="formInfoData.jobAddress"
                v-model:enable="conf.formData.jobAddress.enable"
                v-model:include="conf.formData.jobAddress.include"
                :disabled="helper.workflowRunning.value"
              >
                <form-select
                  v-model:value="conf.formData.jobAddress.value"
                  v-model:options="conf.formData.jobAddress.options"
                />
              </form-item>
              <div></div>
              <form-item
                v-if="conf.configLevel.intermediate"
                v-bind="formInfoData.salaryRange"
                v-model:enable="conf.formData.salaryRange.enable"
                class="col-span-2 xl:col-span-1"
                ref="salaryRangeRef"
              >
                <SalaryRangeComponent
                  :value="conf.formData.salaryRange.value"
                  unit="K"
                  :show="false"
                >
                  <UButton
                    v-if="conf.configLevel.advanced"
                    @click="
                      () => {
                        salaryRangeAdvanced = !salaryRangeAdvanced
                      }
                    "
                  >
                    高级
                  </UButton>
                </SalaryRangeComponent>
                <UPopover
                  :reference="salaryRangeRef"
                  :open="salaryRangeAdvanced"
                  placement="top"
                  trigger="click"
                >
                  <template #content>
                    <div class="p-3 flex flex-col gap-3 max-w-85">
                      <UAlert
                        title="宽松匹配: 薪资范围有任何重叠即匹配, 如10-20K: 15-20K, 15-21k, 20-26k 都满足, 21-22k 不满足"
                        color="info"
                        :close="false"
                      />
                      <UAlert
                        title="严格匹配: 目标薪资需完全在职位范围内, 如10-20K: 10-15K 和15-20K 满足, 15-21k 不满足"
                        color="info"
                        :close="false"
                      />
                      <SalaryRangeComponent
                        :value="conf.formData.salaryRange.value"
                        unit="K"
                        :show="true"
                        :ui="{ base: 'max-w-20' }"
                      />
                      <UAlert
                        title="计算值进行同步，算法固定. 日薪: /21.75, 时薪: /21.75/8"
                        color="info"
                        :close="false"
                      />
                      <UButton @click="syncSalaryRange"> 同步 </UButton>
                      <SalaryRangeComponent
                        :value="conf.formData.salaryRange.advancedValue.H"
                        unit="元/时"
                        :show="true"
                        :step="5"
                        :ui="{ base: 'max-w-20' }"
                      />
                      <SalaryRangeComponent
                        :value="conf.formData.salaryRange.advancedValue.D"
                        unit="元/天"
                        :show="true"
                        :step="10"
                        :ui="{ base: 'max-w-20' }"
                      />
                      <SalaryRangeComponent
                        :value="conf.formData.salaryRange.advancedValue.M"
                        unit="元/月"
                        :show="true"
                        :step="200"
                        :ui="{ base: 'max-w-20' }"
                      /></div
                  ></template>
                </UPopover>
              </form-item>
              <form-item
                v-if="conf.configLevel.intermediate"
                v-bind="formInfoData.companySizeRange"
                v-model:enable="conf.formData.companySizeRange.enable"
                class="col-span-2 xl:col-span-1"
              >
                <SalaryRangeComponent
                  :controls="false"
                  :value="conf.formData.companySizeRange.value"
                  unit="人"
                  :show="true"
                />
              </form-item>
              <div class="col-span-full flex flex-wrap gap-2 mt-3">
                <UCheckbox
                  v-if="conf.configLevel.intermediate"
                  v-bind="formInfoData.activityFilter"
                  v-model="conf.formData.activityFilter.value"
                  :title="formInfoData.activityFilter['data-help']"
                />
                <UCheckbox
                  v-bind="formInfoData.goldHunterFilter"
                  v-model="conf.formData.goldHunterFilter.value"
                  :title="formInfoData.goldHunterFilter['data-help']"
                />
                <UCheckbox
                  v-bind="formInfoData.friendStatus"
                  v-model="conf.formData.friendStatus.value"
                  :title="formInfoData.friendStatus['data-help']"
                />
                <UCheckbox
                  v-if="conf.configLevel.intermediate"
                  v-bind="formInfoData.sameCompanyFilter"
                  v-model="conf.formData.sameCompanyFilter.value"
                  :title="formInfoData.sameCompanyFilter['data-help']"
                />
                <UCheckbox
                  v-if="conf.configLevel.intermediate"
                  v-bind="formInfoData.sameHrFilter"
                  v-model="conf.formData.sameHrFilter.value"
                  :title="formInfoData.sameHrFilter['data-help']"
                />
              </div>
            </div>
          </template>
          <template #greetings>
            <div data-help="自定义招呼语配置" class="flex flex-col gap-2">
              <div class="flex flex-row gap-3">
                <UCheckbox
                  v-bind="formInfoData.customGreeting"
                  v-model="conf.formData.customGreeting.enable"
                />
                <UCheckbox
                  v-if="conf.configLevel.expert"
                  v-bind="formInfoData.greetingVariable"
                  v-model="conf.formData.greetingVariable.value"
                />
              </div>
              <UTextarea
                v-if="Array.isArray(conf.formData.customGreeting.value)"
                :disabled="true"
                placeholder="请使用高级进行配置"
                :rows="3"
                autoresize
                :maxrows="7"
                class="w-full"
              />
              <UTextarea
                v-else
                v-model="conf.formData.customGreeting.value"
                :disabled="conf.formData.aiGreeting.enable"
                :title="conf.formData.aiGreeting.enable && 'AI招呼语已启用, 自定义招呼语不可用'"
                :rows="3"
                autoresize
                :maxrows="7"
                class="w-full"
              />
              <UModal
                title="高级招呼语配置"
                description="为了支持图片,附件等格式, 发送的时候将按顺序进行发送. 已知bug: 多条消息可能只有第一条能发送成功, 具体原因正在排查/修复中"
                :ui="{ footer: 'justify-end', content: 'sm:max-w-[70%]' }"
                @update:open="advancedGreetingEnter"
                :dismissible="false"
                :close="false"
                scrollable
              >
                <UButton label="高级招呼语配置(支持图片)" color="neutral" variant="subtle" />

                <template #body>
                  <div class="flex flex-row gap-3 mb-3">
                    <UButton color="primary" @click="addMessage('text')"> 添加文字消息 </UButton>
                    <UButton color="primary" @click="addMessage('image')"> 添加图片消息 </UButton>
                  </div>
                  <div class="demo-dynamic space-y-3" ref="advancedGreetingModel">
                    <div
                      v-for="(item, index) in advancedGreetingValue"
                      :key="index"
                      class="flex items-start gap-2"
                    >
                      <div class="flex flex-col gap-1.5 w-20">
                        <UDropdownMenu
                          :items="[
                            { label: '向上插入 文字', onClick: () => addMessage('text', index) },
                            {
                              label: '向下插入 文字',
                              onClick: () => addMessage('text', index + 1),
                            },
                            {
                              label: '向上插入 图片',
                              onClick: () => addMessage('image', index),
                            },
                            {
                              label: '向下插入 图片',
                              onClick: () => addMessage('image', index + 1),
                            },
                          ]"
                          :portal="advancedGreetingModelRef?.parentElement ?? false"
                          :content="{
                            align: 'start',
                            side: 'bottom',
                            sideOffset: 8,
                          }"
                        >
                          <UButton label="插入" color="neutral" variant="outline" />
                        </UDropdownMenu>
                        <UButton
                          color="error"
                          variant="outline"
                          @click.prevent="removeMessage(item)"
                          class="w-full"
                        >
                          删除
                        </UButton>
                      </div>
                      <UTextarea
                        v-if="item.type === 'text'"
                        v-model="item.content"
                        autoresize
                        :rows="2"
                        :maxrows="6"
                        class="flex-1"
                      />
                      <div v-else-if="item.type === 'image'">
                        <!-- <img
                          :src="
                            item.image?.[helper.key]?.url ?? item.image?.[helper.key]?.base64 ?? ''
                          "
                          class="size-10"
                          @click="openImg(item.image[helper.key])"
                        /> -->
                        <UFileUpload
                          class="flex-1"
                          size="xl"
                          variant="area"
                          :label="`上传图片(${helper.label})`"
                          accept="image/*"
                          v-model="item.model"
                        />
                      </div>
                    </div>
                  </div>
                </template>

                <template #footer="{ close }">
                  <UButton label="取消" color="neutral" variant="outline" @click="close" />
                  <UButton
                    label="确定"
                    color="neutral"
                    @click="advancedGreetinSave(close)"
                    :loading="advancedGreetinSaveLoading"
                  />
                </template>
              </UModal>
            </div>
          </template>
          <template #appearance> <Appearance /></template>
          <template #address>
            <div class="flex flex-col gap-3" data-help="地址配置">
              <Alert
                id="config-amap-2"
                show-icon
                type="info"
                title="使用高德地图前, 需自行创建key (每日免费配额足够使用)"
                description="推荐结合工作地址包含使用, 创建路径: 创建应用 -> 添加key -> Web服务"
                :actions="[
                  {
                    label: '前往创建',
                    color: 'primary',
                    variant: 'subtle',
                    onClick: gotoAmapDevSetting,
                  },
                ]"
              >
              </Alert>
              <Alert
                id="config-amap-ai"
                :closable="false"
                type="info"
                title="AI Prompt 参考如下语法(仅筛选可用):"
              >
                <template #description>
                  <div class="grid grid-cols-2 gap-2">
                    <span v-pre>直线距离: {{ amap.straightDistance }}km</span>
                    <span v-pre>驾车距离: {{ amap.drivingDistance }}km</span>
                    <span v-pre>驾车时间: {{ amap.drivingDuration }}分钟</span>
                    <span v-pre>步行距离: {{ amap.walkingDistance }}km</span>
                    <span v-pre>步行时间: {{ amap.walkingDuration }}分钟</span>
                  </div>
                </template>
              </Alert>
              <div class="flex gap-3 items-center">
                <UCheckbox v-bind="formInfoData.amap.enable" v-model="conf.formData.amap.enable" />
                <UFormField v-bind="formInfoData.amap.key">
                  <UInput v-model="conf.formData.amap.key" />
                </UFormField>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <UFormField v-bind="formInfoData.amap.origins">
                  <UFieldGroup>
                    <UInput v-model="conf.formData.amap.origins" :disabled="amapGeocodeLoading" />
                    <UButton
                      color="primary"
                      :loading="amapGeocodeLoading"
                      @click="amapGeocodeHandler()"
                      icon="solar:magnifer-bug-outline"
                      title="根据完整地址获取经纬度"
                    >
                    </UButton>
                  </UFieldGroup>
                </UFormField>
                <UFormField v-bind="formInfoData.amap.straightDistance">
                  <UFieldGroup>
                    <UInputNumber
                      v-model="conf.formData.amap.straightDistance"
                      :precision="1"
                      :max="1000"
                      :min="0"
                      :step="1"
                    />
                    <UBadge label="km" />
                  </UFieldGroup>
                </UFormField>

                <UFormField v-bind="formInfoData.amap.drivingDistance">
                  <UFieldGroup>
                    <UInputNumber
                      v-model="conf.formData.amap.drivingDistance"
                      :precision="1"
                      :max="1000"
                      :min="0"
                      :step="1"
                    />
                    <UBadge label="km" />
                  </UFieldGroup>
                </UFormField>
                <UFormField v-bind="formInfoData.amap.drivingDuration">
                  <UFieldGroup>
                    <UInputNumber
                      v-model="conf.formData.amap.drivingDuration"
                      :precision="2"
                      :max="1440"
                      :min="0"
                      :step="30"
                    />

                    <UBadge label="分钟" />
                  </UFieldGroup>
                </UFormField>

                <UFormField v-bind="formInfoData.amap.walkingDistance">
                  <UFieldGroup>
                    <UInputNumber
                      v-model="conf.formData.amap.walkingDistance"
                      :precision="1"
                      :max="1000"
                      :min="0"
                      :step="1"
                    />
                    <UBadge label="km" />
                  </UFieldGroup>
                </UFormField>
                <UFormField v-bind="formInfoData.amap.walkingDuration">
                  <UFieldGroup>
                    <UInputNumber
                      v-model="conf.formData.amap.walkingDuration"
                      :precision="2"
                      :max="1440"
                      :min="0"
                      :step="30"
                    />
                    <UBadge label="分钟" />
                  </UFieldGroup>
                </UFormField>
              </div>
            </div>
          </template>
          <template #delay>
            <div class="grid grid-cols-2 gap-3" data-help="延迟配置">
              <UFormField
                v-for="(item, key) in formInfoData.delay"
                :key
                :label="item.label"
                :data-help="item['data-help']"
              >
                <UInputNumber
                  v-model="conf.formData.delay[key]"
                  :min="1"
                  :max="99999"
                  :disabled="item.disable"
                />
              </UFormField>
            </div>
          </template>
        </UAccordion>
        <hr class="border-t border-gray-200 dark:border-gray-800" />
        <div class="mt-3 flex flex-row flex-wrap gap-5 items-center">
          <UFormField label="配置级别" :data-help="formInfoData.configLevel['data-help']">
            <USelectMenu
              v-model="conf.formData.configLevel"
              :items="formInfoData.configLevel.options"
              value-key="value"
              label-key="label"
              :search-input="false"
            />
          </UFormField>
          <UCheckbox
            v-bind="formInfoData.notification"
            v-model="conf.formData.notification.value"
          />
          <UCheckbox
            v-if="conf.configLevel.expert || conf.formData.useCache.value"
            v-bind="formInfoData.useCache"
            v-model="conf.formData.useCache.value"
          />
          <UButton
            v-if="conf.formData.useCache.value"
            color="warning"
            @click="() => getCacheManager().clearCache()"
          >
            清空缓存
          </UButton>
          <UFormField
            v-if="conf.configLevel.intermediate"
            :label="formInfoData.deliveryLimit.label"
          >
            <UInputNumber
              v-bind="formInfoData.deliveryLimit"
              v-model="conf.formData.deliveryLimit.value"
              :min="1"
              :max="155"
              :step="10"
            />
          </UFormField>
        </div>
      </UForm>
      <div class="flex flex-row *:flex *:flex-row justify-between *:gap-3 mt-3">
        <div>
          <UButton color="success" data-help="保存配置，会自动刷新页面。" @click="conf.confSaving">
            保存配置
          </UButton>
          <UButton color="warning" data-help="重新加载本地配置" @click="conf.confReload">
            重载配置
          </UButton>
          <UButton
            color="primary"
            data-help="不同版本的参数可能会调整, 更新之后一键应用, 不会覆盖主要筛选条件"
            @click="conf.confRecommend"
          >
            使用推荐配置
          </UButton>
        </div>
        <div>
          <UFormField
            label="预设: "
            data-help="虽然不维护多账号了, 但是预设还是要有的, 这样使用隐身/第三方扩展依旧能多账号使用. 多账号是一件多助人为乐的事呀"
          >
            <UInputMenu
              v-model="conf.formDataPreset.value"
              :items="conf.formDataPresets.value"
              value-key="value"
              create-item
              @create="conf.createPreset"
            />
          </UFormField>
          <UButton
            v-if="conf.configLevel.intermediate"
            color="primary"
            data-help="互联网就是要分享"
            @click="conf.confExport"
          >
            导出配置
          </UButton>
          <UButton
            v-if="conf.configLevel.intermediate"
            color="primary"
            data-help="互联网就是要分享"
            @click="conf.confImport"
          >
            导入配置
          </UButton>
          <UButton
            v-if="conf.configLevel.advanced"
            color="error"
            data-help="清空配置,不会帮你保存,可以重载恢复"
            @click="conf.confDelete"
          >
            清空配置
          </UButton>
        </div>
      </div>
    </UTheme>
  </div>
</template>
