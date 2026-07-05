<template>
  <div class="p-md md:p-lg space-y-lg pb-xl">
    <header class="flex flex-col gap-xs">
      <h1 class="font-headline-lg text-headline-lg text-on-background">LLM 管理</h1>
      <p class="font-body-sm text-body-sm text-on-surface-variant">
        先调试未入库 Provider，确认可用后加入配置库；再为 Provider 维护可在对话页选择的模型。
      </p>
    </header>

    <div v-if="message.text" class="p-sm rounded-lg border font-body-sm text-body-sm" :class="message.kind === 'error' ? 'bg-error-container border-error text-error' : 'bg-primary-fixed border-primary-fixed-dim text-primary'">
      {{ message.text }}
    </div>

    <section class="grid grid-cols-1 xl:grid-cols-2 gap-lg">
      <div class="space-y-md">
        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <div class="flex items-start justify-between gap-sm mb-md">
            <div class="flex flex-col gap-xs">
              <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold">{{ editingProviderId ? '编辑 Provider' : 'Provider 草稿' }}</h2>
              <p class="font-body-sm text-body-sm text-on-surface-variant">
                {{ editingProviderId ? 'Provider 编码不可修改；API Key 留空表示沿用原密钥。' : '草稿可先在线调试，不会自动写入数据库。' }}
              </p>
            </div>
            <button v-if="editingProviderId" type="button" @click="resetProviderForm" class="px-3 py-1.5 rounded-md bg-surface-container text-on-surface-variant font-label-md text-label-md hover:bg-surface-container-high">
              取消编辑
            </button>
          </div>

          <form class="space-y-md" @submit.prevent="handleSubmitProvider">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">Provider 编码</span>
                <input v-model="providerForm.providerCode" :disabled="Boolean(editingProviderId)" type="text" class="input" placeholder="custom-openai" />
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">Provider 名称</span>
                <input v-model="providerForm.providerName" type="text" class="input" placeholder="自定义 OpenAI" />
              </label>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">协议类型</span>
                <select v-model="providerForm.protocolType" class="input">
                  <option value="OPENAI_COMPATIBLE">OPENAI_COMPATIBLE</option>
                  <option value="GENERIC_HTTP">GENERIC_HTTP</option>
                </select>
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">鉴权类型</span>
                <select v-model="providerForm.authType" class="input">
                  <option value="BEARER">BEARER</option>
                  <option value="HEADER">HEADER</option>
                </select>
              </label>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">接口地址</span>
                <input v-model="providerForm.endpointUrl" type="url" class="input" placeholder="https://api.example.com/v1/chat/completions" />
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">默认模型</span>
                <input v-model="providerForm.defaultModel" type="text" class="input" placeholder="qwen-plus" />
              </label>
            </div>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">API Key</span>
              <input v-model="providerForm.apiKey" type="password" class="input" :placeholder="editingProviderId ? '留空表示不修改' : 'sk-...'" />
            </label>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">连接超时(ms)</span>
                <input v-model.number="providerForm.connectTimeoutMs" type="number" min="100" max="120000" class="input" />
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">读取超时(ms)</span>
                <input v-model.number="providerForm.readTimeoutMs" type="number" min="100" max="300000" class="input" />
              </label>
            </div>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">默认请求头 JSON</span>
              <textarea v-model="providerForm.defaultHeadersJson" rows="4" class="input mono" placeholder='{"X-Custom":"value"}'></textarea>
            </label>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">请求模板 JSON</span>
              <textarea v-model="providerForm.requestTemplateJson" rows="8" class="input mono" placeholder='{"model":"{{model}}","messages":[{"role":"user","content":"{{message}}"}]}'></textarea>
            </label>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">响应映射 JSON</span>
              <textarea v-model="providerForm.responseMappingJson" rows="6" class="input mono" placeholder='{"mode":"JSON","contentPath":"choices.0.message.content"}'></textarea>
            </label>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">流配置 JSON</span>
              <textarea v-model="providerForm.streamConfigJson" rows="4" class="input mono" placeholder='{"mode":"SSE"}'></textarea>
            </label>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">备注</span>
              <textarea v-model="providerForm.remark" rows="3" class="input" placeholder="用于区分测试环境、生产环境等"></textarea>
            </label>

            <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-sm">
              <span class="font-label-md text-label-md" :class="lastDraftDebugSuccess ? 'text-primary' : 'text-outline'">
                {{ editingProviderId ? '保存后会刷新运行时缓存' : (lastDraftDebugSuccess ? '临时调试已成功，可加入配置库' : '建议先完成右侧临时调试') }}
              </span>
              <button :disabled="submittingProvider" class="px-md py-sm bg-primary text-on-primary rounded-lg font-label-md text-label-md hover:opacity-90 disabled:opacity-50">
                {{ submittingProvider ? '保存中...' : (editingProviderId ? '保存 Provider' : '加入配置库') }}
              </button>
            </div>
          </form>
        </div>
      </div>

      <div class="space-y-md">
        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <div class="flex items-center justify-between gap-sm mb-md">
            <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold">在线调试</h2>
            <span class="font-label-md text-label-md text-outline">支持非流式与 SSE 调试</span>
          </div>
          <form class="space-y-md" @submit.prevent="handleDebug">
            <div class="inline-flex items-center bg-surface-container rounded-md p-0.5 border border-outline-variant">
              <button type="button" @click="debugMode = 'draft'" class="px-3 py-1.5 rounded font-label-md text-label-md transition-colors" :class="debugMode === 'draft' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-outline hover:text-on-surface-variant'">
                临时配置
              </button>
              <button type="button" @click="debugMode = 'saved'" class="px-3 py-1.5 rounded font-label-md text-label-md transition-colors" :class="debugMode === 'saved' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-outline hover:text-on-surface-variant'">
                已入库 Provider
              </button>
            </div>

            <label v-if="debugMode === 'saved'" class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">Provider 编码</span>
              <select v-model="debugForm.providerCode" class="input">
                <option value="" disabled>请选择 Provider</option>
                <option v-for="item in enabledProviders" :key="item.providerCode" :value="item.providerCode">
                  {{ item.providerName }} ({{ item.defaultModel }})
                </option>
              </select>
            </label>

            <div v-else class="p-sm rounded-lg bg-surface-container border border-outline-variant font-body-sm text-body-sm text-on-surface-variant">
              将使用左侧配置草稿发起真实请求，不会写入数据库，也不会进入模型库。
            </div>

            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">消息内容</span>
              <textarea v-model="debugForm.message" rows="5" class="input" placeholder="请输入测试消息"></textarea>
            </label>
            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">系统提示词</span>
              <textarea v-model="debugForm.systemPrompt" rows="3" class="input" placeholder="可选"></textarea>
            </label>
            <label class="flex items-center gap-xs font-label-md text-label-md text-on-surface-variant">
              <input v-model="debugForm.stream" type="checkbox" />
              流式返回
            </label>
            <div class="flex justify-end">
              <button :disabled="submittingDebug" class="px-md py-sm bg-primary text-on-primary rounded-lg font-label-md text-label-md hover:opacity-90 disabled:opacity-50">
                {{ submittingDebug ? '调试中...' : '开始调试' }}
              </button>
            </div>
          </form>
        </div>

        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <div class="flex items-center justify-between mb-md">
            <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold">Provider 配置库</h2>
            <button @click="loadAll" class="font-label-md text-label-md text-primary hover:underline">刷新</button>
          </div>
          <div v-if="providers.length === 0" class="text-center py-lg text-outline-variant">暂无 Provider</div>
          <div v-else class="space-y-sm">
            <div v-for="item in providers" :key="item.id" class="border border-outline-variant rounded-lg p-md bg-background">
              <div class="flex items-start justify-between gap-sm">
                <div class="min-w-0">
                  <div class="font-semibold text-on-background truncate">{{ item.providerName }}</div>
                  <div class="font-label-md text-label-md text-outline">{{ item.providerCode }} · {{ item.defaultModel }} · {{ item.status }}</div>
                </div>
                <span class="px-2 py-1 rounded-full text-xs flex-shrink-0" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
              </div>
              <div class="mt-sm text-sm text-on-surface-variant break-all">{{ item.endpointUrl }}</div>
              <div class="mt-md flex flex-wrap gap-xs justify-end">
                <button type="button" @click="copyProviderToDraft(item)" class="action-btn">复制到草稿</button>
                <button type="button" @click="startEditProvider(item)" class="action-btn primary-action">编辑</button>
                <button type="button" @click="startCreateModel(item)" class="action-btn primary-action">新增模型</button>
                <button v-if="item.status === 'ENABLED'" type="button" @click="handleDisableProvider(item)" class="action-btn">停用</button>
                <button v-else type="button" @click="handleEnableProvider(item)" class="action-btn primary-action">启用</button>
                <button type="button" @click="handleDeleteProvider(item)" :disabled="deletingProviderId === item.id" class="action-btn danger-action">
                  {{ deletingProviderId === item.id ? '删除中...' : '删除' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <div class="flex items-start justify-between gap-sm mb-md">
            <div>
              <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold">模型库</h2>
              <p class="font-body-sm text-body-sm text-on-surface-variant">这些模型会出现在对话页的模型选择器中。</p>
            </div>
            <button v-if="editingModelId" type="button" @click="resetModelForm" class="px-3 py-1.5 rounded-md bg-surface-container text-on-surface-variant font-label-md text-label-md hover:bg-surface-container-high">取消编辑</button>
          </div>

          <form class="space-y-md mb-lg" @submit.prevent="handleSubmitModel">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">所属 Provider</span>
                <select v-model="modelForm.providerId" class="input">
                  <option value="" disabled>请选择 Provider</option>
                  <option v-for="item in providers" :key="item.id" :value="item.id">{{ item.providerName }} · {{ statusText(item.status) }}</option>
                </select>
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">模型编码</span>
                <input v-model="modelForm.modelCode" :disabled="Boolean(editingModelId)" type="text" class="input" placeholder="qwen-plus-prod" />
              </label>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">展示名称</span>
                <input v-model="modelForm.displayName" type="text" class="input" placeholder="通义千问 Plus" />
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">远端模型名</span>
                <input v-model="modelForm.remoteModelName" type="text" class="input" placeholder="qwen-plus" />
              </label>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">默认参数 JSON</span>
                <textarea v-model="modelForm.defaultParamsJson" rows="4" class="input mono" placeholder='{"temperature":0.7}'></textarea>
              </label>
              <label class="space-y-xs">
                <span class="font-label-md text-label-md text-on-surface-variant">能力标签 JSON</span>
                <textarea v-model="modelForm.capabilitiesJson" rows="4" class="input mono" placeholder='{"chat":true,"stream":false}'></textarea>
              </label>
            </div>
            <label class="space-y-xs block">
              <span class="font-label-md text-label-md text-on-surface-variant">排序</span>
              <input v-model.number="modelForm.sortOrder" type="number" min="0" max="999999" class="input" />
            </label>
            <div class="flex justify-end">
              <button :disabled="submittingModel" class="px-md py-sm bg-primary text-on-primary rounded-lg font-label-md text-label-md hover:opacity-90 disabled:opacity-50">
                {{ submittingModel ? '保存中...' : (editingModelId ? '保存模型' : '新增模型') }}
              </button>
            </div>
          </form>

          <div v-if="models.length === 0" class="text-center py-lg text-outline-variant">暂无模型</div>
          <div v-else class="space-y-sm">
            <div v-for="item in models" :key="item.id" class="border border-outline-variant rounded-lg p-md bg-background">
              <div class="flex items-start justify-between gap-sm">
                <div class="min-w-0">
                  <div class="font-semibold text-on-background truncate">{{ item.displayName }}</div>
                  <div class="font-label-md text-label-md text-outline">{{ item.modelCode }} · {{ item.remoteModelName }} · {{ item.providerName }}</div>
                </div>
                <span class="px-2 py-1 rounded-full text-xs flex-shrink-0" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
              </div>
              <div v-if="capabilityBadges(item).length" class="mt-sm flex flex-wrap gap-xs">
                <span v-for="cap in capabilityBadges(item)" :key="cap" class="cap-badge">{{ cap }}</span>
              </div>
              <div class="mt-md flex flex-wrap gap-xs justify-end">
                <button type="button" @click="startEditModel(item)" class="action-btn primary-action">编辑</button>
                <button v-if="item.status === 'ENABLED'" type="button" @click="handleDisableModel(item)" class="action-btn">停用</button>
                <button v-else type="button" @click="handleEnableModel(item)" class="action-btn primary-action">启用</button>
                <button type="button" @click="handleDeleteModel(item)" :disabled="deletingModelId === item.id" class="action-btn danger-action">
                  {{ deletingModelId === item.id ? '删除中...' : '删除' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold mb-md">最近调试结果</h2>
          <pre class="debug-pre">{{ debugResult }}</pre>
        </div>

        <div class="bg-surface-container-lowest border border-outline-variant rounded-xl p-lg shadow-sm">
          <div class="flex items-center justify-between mb-md">
            <h2 class="font-headline-sm text-headline-sm text-on-background font-semibold">模型运维指标</h2>
            <button @click="loadOpsMetrics" class="font-label-md text-label-md text-primary hover:underline">刷新</button>
          </div>
          <div v-if="opsMetrics.length === 0" class="text-center py-lg text-outline-variant">
            暂无调用指标。完成一次在线调试或自定义模型对话后会生成数据。
          </div>
          <div v-else class="space-y-sm">
            <div v-for="item in opsMetrics" :key="`${item.providerCode}:${item.modelCode}`" class="border border-outline-variant rounded-lg p-md bg-background space-y-sm">
              <div class="flex items-start justify-between gap-sm">
                <div class="min-w-0">
                  <div class="font-semibold text-on-background truncate">{{ item.modelCode }}</div>
                  <div class="font-label-md text-label-md text-outline">{{ item.providerCode }}</div>
                </div>
                <span class="px-2 py-1 rounded-full text-xs bg-primary-fixed text-primary">{{ item.successRate }}%</span>
              </div>
              <div class="grid grid-cols-2 gap-xs font-label-md text-label-md text-on-surface-variant">
                <span>成功 {{ item.successCount }}</span>
                <span>失败 {{ item.failureCount }}</span>
                <span>平均 {{ item.averageLatencyMs }}ms</span>
                <span>Token {{ item.estimatedTokens }}</span>
                <span>费用 {{ item.estimatedCost }}</span>
                <span>熔断 {{ item.circuitBreakerOpenCount }}</span>
              </div>
              <div v-if="item.lastError" class="text-error font-body-sm break-words">
                最近错误：{{ item.lastError }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { llm } from '../api'

const providers = ref([])
const models = ref([])
const submittingProvider = ref(false)
const submittingModel = ref(false)
const submittingDebug = ref(false)
const deletingProviderId = ref('')
const deletingModelId = ref('')
const editingProviderId = ref('')
const editingModelId = ref('')
const debugResult = ref('等待调试结果')
const debugMode = ref('draft')
const lastDraftDebugSuccess = ref(false)
const opsMetrics = ref([])
const message = reactive({ kind: 'success', text: '' })

const defaultTemplate = '{"model":"{{model}}","messages":[{"role":"system","content":"{{systemPrompt}}"},{"role":"user","content":"{{message}}"}],"stream":"{{stream}}"}'
const defaultMapping = '{"mode":"JSON","contentPath":"choices.0.message.content"}'
const defaultCapabilities = '{"chat":true,"stream":false,"jsonMode":false,"tools":false,"rag":true,"longContext":false}'
const emptyProviderForm = () => ({
  providerCode: '',
  providerName: '',
  protocolType: 'OPENAI_COMPATIBLE',
  authType: 'BEARER',
  endpointUrl: '',
  apiKey: '',
  defaultModel: 'qwen-plus',
  defaultHeadersJson: '',
  requestTemplateJson: defaultTemplate,
  responseMappingJson: defaultMapping,
  streamConfigJson: '',
  connectTimeoutMs: 5000,
  readTimeoutMs: 30000,
  remark: ''
})
const emptyModelForm = () => ({
  providerId: '',
  modelCode: '',
  displayName: '',
  remoteModelName: '',
  defaultParamsJson: '',
  capabilitiesJson: '',
  sortOrder: 0
})

const providerForm = reactive(emptyProviderForm())
const modelForm = reactive(emptyModelForm())

const debugForm = reactive({
  providerCode: '',
  message: '你好，请介绍一下你自己',
  systemPrompt: '',
  stream: false
})

watch(providerForm, () => {
  lastDraftDebugSuccess.value = false
})

const enabledProviders = computed(() => providers.value.filter(item => item.status === 'ENABLED'))

const showMessage = (kind, text) => {
  message.kind = kind
  message.text = text
}

const statusText = (status) => status === 'ENABLED' ? '已启用' : '已停用'

const statusClass = (status) => status === 'ENABLED'
  ? 'bg-primary-fixed text-primary'
  : 'bg-surface-container text-outline'

const parseJsonField = (value, label, { requiredObject = true } = {}) => {
  if (!value || !value.trim()) return null
  try {
    const parsed = JSON.parse(value)
    if (requiredObject && (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object')) {
      throw new Error(`${label} 必须是 JSON 对象`)
    }
    return parsed
  } catch (err) {
    throw new Error(err.message || `${label} 格式无效`)
  }
}

const validateProviderJson = () => {
  parseJsonField(providerForm.defaultHeadersJson, '默认请求头 JSON')
  parseJsonField(providerForm.requestTemplateJson, '请求模板 JSON', { requiredObject: false })
  const mapping = parseJsonField(providerForm.responseMappingJson, '响应映射 JSON')
  if (mapping?.mode && !['JSON', 'SSE'].includes(mapping.mode)) {
    throw new Error('响应映射 mode 仅支持 JSON 或 SSE')
  }
  parseJsonField(providerForm.streamConfigJson, '流配置 JSON')
}

const validateModelJson = () => {
  parseJsonField(modelForm.defaultParamsJson, '默认参数 JSON')
  const capabilities = parseJsonField(modelForm.capabilitiesJson, '能力标签 JSON')
  if (!capabilities) return
  ;['chat', 'stream', 'jsonMode', 'tools', 'rag', 'longContext'].forEach(key => {
    if (capabilities[key] !== undefined && typeof capabilities[key] !== 'boolean') {
      throw new Error(`${key} 能力标签必须是 true/false`)
    }
  })
}

const capabilityBadges = (model) => {
  let capabilities = null
  try {
    capabilities = parseJsonField(model.capabilitiesJson || '', '能力标签 JSON')
  } catch {
    return ['能力配置异常']
  }
  if (!capabilities) return []
  const labels = {
    chat: '对话',
    stream: '流式',
    jsonMode: 'JSON',
    tools: '工具',
    rag: 'RAG',
    longContext: '长上下文'
  }
  return Object.entries(labels)
    .filter(([key]) => capabilities[key] === true)
    .map(([, label]) => label)
}

const assignProviderForm = (payload) => Object.assign(providerForm, payload)
const assignModelForm = (payload) => Object.assign(modelForm, payload)

const resetProviderForm = () => {
  editingProviderId.value = ''
  assignProviderForm(emptyProviderForm())
  lastDraftDebugSuccess.value = false
}

const resetModelForm = () => {
  editingModelId.value = ''
  assignModelForm(emptyModelForm())
}

const loadProviders = async () => {
  const res = await llm.listProviders({ includeDisabled: true })
  if (res.code === 200) {
    providers.value = res.data || []
    if (!debugForm.providerCode && enabledProviders.value.length > 0) {
      debugForm.providerCode = enabledProviders.value[0].providerCode
    }
    if (!modelForm.providerId && providers.value.length > 0) {
      modelForm.providerId = providers.value[0].id
    }
  } else {
    showMessage('error', res.message || '加载 Provider 失败')
  }
}

const loadModels = async () => {
  const res = await llm.listModels({ includeDisabled: true })
  if (res.code === 200) {
    models.value = res.data || []
  } else {
    showMessage('error', res.message || '加载模型失败')
  }
}

const loadAll = async () => {
  try {
    await loadProviders()
    await loadModels()
    await loadOpsMetrics()
  } catch (err) {
    showMessage('error', err.message || '加载配置失败')
  }
}

const loadOpsMetrics = async () => {
  const res = await llm.opsMetrics()
  if (res.code === 200) {
    opsMetrics.value = res.data || []
  }
}

const providerPayload = ({ includeProviderCode = true, includeBlankApiKey = true } = {}) => {
  const payload = {
    providerName: providerForm.providerName,
    protocolType: providerForm.protocolType,
    authType: providerForm.authType,
    endpointUrl: providerForm.endpointUrl,
    defaultModel: providerForm.defaultModel,
    defaultHeadersJson: providerForm.defaultHeadersJson,
    requestTemplateJson: providerForm.requestTemplateJson,
    responseMappingJson: providerForm.responseMappingJson,
    streamConfigJson: providerForm.streamConfigJson,
    connectTimeoutMs: providerForm.connectTimeoutMs,
    readTimeoutMs: providerForm.readTimeoutMs,
    remark: providerForm.remark
  }
  if (includeProviderCode) {
    payload.providerCode = providerForm.providerCode
  }
  if (includeBlankApiKey || providerForm.apiKey.trim()) {
    payload.apiKey = providerForm.apiKey
  }
  return payload
}

const modelPayload = ({ includeModelCode = true } = {}) => {
  const payload = {
    providerId: modelForm.providerId,
    displayName: modelForm.displayName,
    remoteModelName: modelForm.remoteModelName,
    defaultParamsJson: modelForm.defaultParamsJson,
    capabilitiesJson: modelForm.capabilitiesJson,
    sortOrder: modelForm.sortOrder
  }
  if (includeModelCode) {
    payload.modelCode = modelForm.modelCode
  }
  return payload
}

const handleSubmitProvider = async () => {
  try {
    validateProviderJson()
  } catch (err) {
    showMessage('error', err.message)
    return
  }
  submittingProvider.value = true
  const shouldPrefillModel = !editingProviderId.value && lastDraftDebugSuccess.value
  try {
    const res = editingProviderId.value
      ? await llm.updateProvider(editingProviderId.value, providerPayload({ includeProviderCode: false, includeBlankApiKey: false }))
      : await llm.createProvider(providerPayload())

    if (res.code === 200) {
      const successText = editingProviderId.value ? `已更新：${res.data.providerName}` : `已加入配置库：${res.data.providerName}`
      showMessage('success', successText)
      debugForm.providerCode = res.data.providerCode
      debugMode.value = 'saved'
      await loadAll()
      if (shouldPrefillModel) {
        startCreateModel(res.data)
        showMessage('success', `已加入配置库：${res.data.providerName}，模型表单已预填`)
      }
      resetProviderForm()
    } else {
      showMessage('error', res.message || '保存失败')
    }
  } catch (err) {
    showMessage('error', err.message || '保存失败')
  } finally {
    submittingProvider.value = false
  }
}

const handleSubmitModel = async () => {
  try {
    validateModelJson()
  } catch (err) {
    showMessage('error', err.message)
    return
  }
  submittingModel.value = true
  try {
    const res = editingModelId.value
      ? await llm.updateModel(editingModelId.value, modelPayload({ includeModelCode: false }))
      : await llm.createModel(modelPayload())

    if (res.code === 200) {
      showMessage('success', editingModelId.value ? `已更新模型：${res.data.displayName}` : `已新增模型：${res.data.displayName}`)
      resetModelForm()
      await loadAll()
    } else {
      showMessage('error', res.message || '保存模型失败')
    }
  } catch (err) {
    showMessage('error', err.message || '保存模型失败')
  } finally {
    submittingModel.value = false
  }
}

const copyProviderToDraft = (provider) => {
  editingProviderId.value = ''
  assignProviderForm({
    providerCode: `${provider.providerCode}-copy`,
    providerName: `${provider.providerName} 副本`,
    protocolType: provider.protocolType,
    authType: provider.authType,
    endpointUrl: provider.endpointUrl,
    apiKey: '',
    defaultModel: provider.defaultModel,
    defaultHeadersJson: provider.defaultHeadersJson || '',
    requestTemplateJson: provider.requestTemplateJson || defaultTemplate,
    responseMappingJson: provider.responseMappingJson || defaultMapping,
    streamConfigJson: provider.streamConfigJson || '',
    connectTimeoutMs: provider.connectTimeoutMs || 5000,
    readTimeoutMs: provider.readTimeoutMs || 30000,
    remark: provider.remark || ''
  })
  debugMode.value = 'draft'
  showMessage('success', '已复制到草稿，请补充 API Key 后调试或入库')
}

const startEditProvider = (provider) => {
  editingProviderId.value = provider.id
  assignProviderForm({
    providerCode: provider.providerCode,
    providerName: provider.providerName,
    protocolType: provider.protocolType,
    authType: provider.authType,
    endpointUrl: provider.endpointUrl,
    apiKey: '',
    defaultModel: provider.defaultModel,
    defaultHeadersJson: provider.defaultHeadersJson || '',
    requestTemplateJson: provider.requestTemplateJson || defaultTemplate,
    responseMappingJson: provider.responseMappingJson || defaultMapping,
    streamConfigJson: provider.streamConfigJson || '',
    connectTimeoutMs: provider.connectTimeoutMs || 5000,
    readTimeoutMs: provider.readTimeoutMs || 30000,
    remark: provider.remark || ''
  })
  showMessage('success', '已进入 Provider 编辑模式，API Key 留空表示不修改')
}

const startCreateModel = (provider) => {
  editingModelId.value = ''
  assignModelForm({
    ...emptyModelForm(),
    providerId: provider.id,
    remoteModelName: provider.defaultModel,
    displayName: provider.defaultModel,
    modelCode: `${provider.providerCode}-${provider.defaultModel}`.replace(/[^a-zA-Z0-9_-]/g, '-').toLowerCase(),
    capabilitiesJson: defaultCapabilities
  })
  showMessage('success', '已准备新增模型')
}

const startEditModel = (model) => {
  editingModelId.value = model.id
  assignModelForm({
    providerId: model.providerId,
    modelCode: model.modelCode,
    displayName: model.displayName,
    remoteModelName: model.remoteModelName,
    defaultParamsJson: model.defaultParamsJson || '',
    capabilitiesJson: model.capabilitiesJson || '',
    sortOrder: model.sortOrder || 0
  })
  showMessage('success', '已进入模型编辑模式，模型编码不可修改')
}

const handleDeleteProvider = async (provider) => {
  if (!confirm(`确认删除 Provider「${provider.providerName}」吗？若仍有关联模型，后端会拒绝删除。`)) return
  deletingProviderId.value = provider.id
  try {
    const res = await llm.deleteProvider(provider.id)
    if (res.code === 200) {
      if (debugForm.providerCode === provider.providerCode) {
        debugForm.providerCode = providers.value.find(item => item.id !== provider.id)?.providerCode || ''
      }
      if (editingProviderId.value === provider.id) resetProviderForm()
      showMessage('success', '已删除 Provider')
      await loadAll()
    } else {
      showMessage('error', res.message || '删除失败')
    }
  } catch (err) {
    showMessage('error', err.message || '删除失败')
  } finally {
    deletingProviderId.value = ''
  }
}

const handleEnableProvider = async (provider) => {
  try {
    const res = await llm.enableProvider(provider.id)
    if (res.code === 200) {
      showMessage('success', `已启用 Provider：${provider.providerName}`)
      await loadAll()
    } else {
      showMessage('error', res.message || '启用失败')
    }
  } catch (err) {
    showMessage('error', err.message || '启用失败')
  }
}

const handleDisableProvider = async (provider) => {
  try {
    const res = await llm.disableProvider(provider.id)
    if (res.code === 200) {
      if (debugForm.providerCode === provider.providerCode) {
        debugForm.providerCode = ''
      }
      showMessage('success', `已停用 Provider：${provider.providerName}`)
      await loadAll()
    } else {
      showMessage('error', res.message || '停用失败')
    }
  } catch (err) {
    showMessage('error', err.message || '停用失败')
  }
}

const handleDeleteModel = async (model) => {
  if (!confirm(`确认删除模型「${model.displayName}」吗？删除后对话页将不能再选择它。`)) return
  deletingModelId.value = model.id
  try {
    const res = await llm.deleteModel(model.id)
    if (res.code === 200) {
      if (editingModelId.value === model.id) resetModelForm()
      showMessage('success', '已删除模型')
      await loadModels()
    } else {
      showMessage('error', res.message || '删除模型失败')
    }
  } catch (err) {
    showMessage('error', err.message || '删除模型失败')
  } finally {
    deletingModelId.value = ''
  }
}

const handleEnableModel = async (model) => {
  try {
    const res = await llm.enableModel(model.id)
    if (res.code === 200) {
      showMessage('success', `已启用模型：${model.displayName}`)
      await loadModels()
    } else {
      showMessage('error', res.message || '启用模型失败')
    }
  } catch (err) {
    showMessage('error', err.message || '启用模型失败')
  }
}

const handleDisableModel = async (model) => {
  try {
    const res = await llm.disableModel(model.id)
    if (res.code === 200) {
      showMessage('success', `已停用模型：${model.displayName}`)
      await loadModels()
    } else {
      showMessage('error', res.message || '停用模型失败')
    }
  } catch (err) {
    showMessage('error', err.message || '停用模型失败')
  }
}

const buildDebugPayload = () => {
  const payload = {
    message: debugForm.message,
    systemPrompt: debugForm.systemPrompt,
    stream: debugForm.stream
  }
  if (debugMode.value === 'saved') {
    return {
      ...payload,
      providerCode: debugForm.providerCode
    }
  }
  return {
    ...payload,
    providerConfig: providerPayload()
  }
}

const handleDebug = async () => {
  try {
    if (debugMode.value === 'draft') {
      validateProviderJson()
    }
  } catch (err) {
    showMessage('error', err.message)
    return
  }
  submittingDebug.value = true
  try {
    if (debugForm.stream) {
      const events = []
      debugResult.value = '流式调试中...'
      await llm.debugProviderStream(buildDebugPayload(), ({ event, data }) => {
        events.push({ event, data: safeParse(data) })
        debugResult.value = JSON.stringify(events, null, 2)
      })
      const summary = events.find(item => item.event === 'summary')?.data
      lastDraftDebugSuccess.value = debugMode.value === 'draft' && summary?.success === true
      showMessage(summary?.success ? 'success' : 'error', summary?.success ? '流式调试完成' : '流式调试返回失败，请查看结果')
      await loadOpsMetrics()
      return
    }
    const res = await llm.debugProvider(buildDebugPayload())
    if (res.code === 200) {
      debugResult.value = JSON.stringify(res.data, null, 2)
      lastDraftDebugSuccess.value = debugMode.value === 'draft' && res.data?.success === true
      showMessage(res.data?.success ? 'success' : 'error', res.data?.success ? '调试完成' : '调试返回失败，请查看结果')
      await loadOpsMetrics()
    } else {
      debugResult.value = JSON.stringify(res, null, 2)
      lastDraftDebugSuccess.value = false
      showMessage('error', res.message || '调试失败')
    }
  } catch (err) {
    debugResult.value = err.message || '调试失败'
    lastDraftDebugSuccess.value = false
    showMessage('error', err.message || '调试失败')
  } finally {
    submittingDebug.value = false
  }
}

const safeParse = (value) => {
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

onMounted(loadAll)
</script>

<style scoped>
.input {
  width: 100%;
  border: 1px solid rgb(var(--tw-colors-outline-variant));
  border-radius: 0.75rem;
  background: rgb(var(--tw-colors-surface));
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
  color: rgb(var(--tw-colors-on-background));
  outline: none;
}

.input:focus {
  border-color: rgb(var(--tw-colors-primary));
  box-shadow: 0 0 0 2px rgb(var(--tw-colors-primary-container));
}

.input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.action-btn {
  padding: 0.375rem 0.625rem;
  border-radius: 0.375rem;
  background: rgb(var(--tw-colors-surface-container));
  color: rgb(var(--tw-colors-on-surface-variant));
  font-size: 0.75rem;
  line-height: 1rem;
}

.primary-action {
  color: rgb(var(--tw-colors-primary));
}

.danger-action {
  color: rgb(var(--tw-colors-error));
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.debug-pre {
  min-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 1rem;
  border-radius: 0.75rem;
  background: rgb(var(--tw-colors-surface-container));
  color: rgb(var(--tw-colors-on-surface));
}
</style>
