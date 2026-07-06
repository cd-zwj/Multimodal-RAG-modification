<template>
  <div class="h-full min-h-0 flex overflow-hidden bg-background">
    <!-- Secondary Panel: Conversations History Sidebar -->
    <aside class="w-64 bg-surface-container-lowest border-r border-outline-variant h-full flex flex-col p-md flex-shrink-0 hidden lg:flex min-h-0">
      <div class="font-label-md text-label-md text-outline uppercase tracking-wider mb-sm px-2">最近会话</div>
      <div class="flex-1 overflow-y-auto scrollbar-hide flex flex-col gap-xs pr-1 min-h-0">
        <div v-if="sessionsList.length === 0" class="text-center py-lg text-outline-variant text-body-sm">
          暂无历史会话
        </div>
        <div
          v-for="session in sessionsList"
          :key="session"
          @click="selectSession(session)"
          class="flex items-center justify-between py-2 px-3 rounded-md cursor-pointer transition-colors group text-left"
          :class="[currentSessionId === session ? 'bg-surface-container text-primary font-semibold' : 'hover:bg-surface-container-low text-on-surface-variant']"
        >
          <span class="font-body-sm text-body-sm truncate flex-1 pr-2">{{ formatSessionTitle(session) }}</span>
          <button
            @click.stop="confirmDeleteSession(session)"
            class="text-outline-variant hover:text-error opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer flex items-center justify-center p-xs rounded hover:bg-error-container"
            title="删除会话"
          >
            <span class="material-symbols-outlined text-[16px]">delete</span>
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Chat Feed Area -->
    <div class="flex-1 flex flex-col min-w-0 min-h-0 bg-background h-full">
      <!-- Chat Message Window -->
      <div
        ref="messageContainer"
        class="flex-1 min-h-0 overflow-y-auto p-md md:p-lg flex flex-col gap-lg scrollbar-hide"
      >
        <div v-if="chatMessages.length === 0" class="flex flex-col items-center justify-center h-full max-w-lg mx-auto text-center space-y-md my-auto opacity-75">
          <span class="material-symbols-outlined text-primary text-[64px]" style="font-variation-settings: 'FILL' 1;">chat_bubble_outline</span>
          <h3 class="font-headline-md text-headline-md text-on-background">智能 AI 助手</h3>
          <p class="font-body-sm text-body-sm text-on-surface-variant">
            基于大语言模型与多路径知识库召回的 RAG 问答平台。您可以输入任何关于知识库文档的问题。
          </p>
          <p v-if="knowledgeBanner" class="max-w-md rounded-lg border px-md py-sm font-body-sm text-body-sm" :class="knowledgeBanner.kind === 'warning' ? 'border-secondary text-secondary bg-secondary-fixed/20' : 'border-error text-error bg-error-container'">
            {{ knowledgeBanner.text }}
          </p>
        </div>

        <div
          v-if="knowledgeBanner && chatMessages.length > 0"
          class="max-w-4xl mx-auto w-full rounded-lg border px-md py-sm font-body-sm text-body-sm"
          :class="knowledgeBanner.kind === 'warning' ? 'border-secondary text-secondary bg-secondary-fixed/20' : 'border-error text-error bg-error-container'"
        >
          {{ knowledgeBanner.text }}
        </div>

        <div
          v-for="(msg, index) in chatMessages"
          :key="index"
          class="flex w-full"
          :class="[msg.role === 'user' ? 'justify-end' : 'justify-start']"
        >
          <div
            class="max-w-[85%] md:max-w-[75%] flex gap-sm"
            :class="[msg.role === 'user' ? 'flex-row-reverse' : 'flex-row']"
          >
            <!-- Avatar -->
            <div
              class="w-8 h-8 rounded-full border border-outline-variant flex-shrink-0 flex items-center justify-center font-bold text-xs"
              :class="[msg.role === 'user' ? 'bg-primary-container text-on-primary' : 'bg-surface-container-lowest text-primary']"
            >
              {{ msg.role === 'user' ? authStore.username[0].toUpperCase() : 'AI' }}
            </div>

            <!-- Message Bubble -->
            <div
              class="p-md rounded-lg shadow-sm font-body-md text-body-md leading-relaxed break-words"
              :class="[
                msg.role === 'user'
                  ? 'bg-primary-container text-on-primary rounded-tr-none'
                  : 'bg-surface-container-lowest border border-outline-variant border-l-4 border-l-primary-container text-on-surface rounded-tl-none'
              ]"
            >
              <div class="markdown-content" v-html="formatMessageContent(msg.content, msg.role, index)"></div>
              <div
                v-if="msg.plan && msg.plan.status === 'WAITING_APPROVAL'"
                class="mt-md pt-sm border-t border-outline-variant flex flex-wrap gap-xs"
              >
                <button
                  @click="confirmPlan(msg.plan, index)"
                  :disabled="streaming"
                  class="inline-flex items-center gap-xs bg-primary-container text-on-primary px-3 py-1.5 rounded-md font-label-md text-label-md hover:opacity-90 disabled:opacity-50"
                >
                  <span class="material-symbols-outlined text-[16px]">play_arrow</span>
                  确认执行
                </button>
                <button
                  @click="cancelPlan(index)"
                  :disabled="streaming"
                  class="inline-flex items-center gap-xs bg-surface-container text-on-surface-variant px-3 py-1.5 rounded-md font-label-md text-label-md hover:bg-surface-container-high disabled:opacity-50"
                >
                  <span class="material-symbols-outlined text-[16px]">close</span>
                  取消
                </button>
                <button
                  @click="revisePlan(msg.plan, index)"
                  :disabled="streaming"
                  class="inline-flex items-center gap-xs bg-surface-container text-primary px-3 py-1.5 rounded-md font-label-md text-label-md hover:bg-surface-container-high disabled:opacity-50"
                >
                  <span class="material-symbols-outlined text-[16px]">edit</span>
                  修改需求
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Typing Loader -->
        <div v-if="streaming" class="flex w-full justify-start">
          <div class="max-w-[85%] md:max-w-[75%] flex gap-sm">
            <div class="w-8 h-8 rounded-full border border-outline-variant flex-shrink-0 flex items-center justify-center font-bold text-xs bg-surface-container-lowest text-primary">
              AI
            </div>
            <div class="bg-surface-container-lowest border border-outline-variant border-l-4 border-l-primary-container p-md rounded-lg rounded-tl-none shadow-sm flex items-center gap-sm">
              <span class="typing-indicator flex items-center text-primary">
                <span class="w-2 h-2 bg-primary rounded-full mx-0.5"></span>
                <span class="w-2 h-2 bg-primary rounded-full mx-0.5"></span>
                <span class="w-2 h-2 bg-primary rounded-full mx-0.5"></span>
              </span>
              <span class="font-body-sm text-body-sm text-outline">大模型生成中...</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input composer container -->
      <div class="flex-shrink-0 bg-gradient-to-t from-background via-background to-transparent pt-md pb-md px-md md:px-lg border-t border-outline-variant/60">
        <div class="max-w-4xl mx-auto">
          <div class="bg-surface-container-lowest border border-outline-variant rounded-xl shadow-md p-2 flex flex-col transition-all focus-within:border-primary focus-within:shadow-lg">
            <textarea
              v-model="inputMessage"
              @keydown.enter.prevent="sendMessage"
              class="w-full bg-transparent border-none focus:ring-0 resize-none font-body-md text-body-md p-3 text-on-surface placeholder:text-outline min-h-[5rem] max-h-48 outline-none"
              placeholder="请输入您的问题，按回车发送..."
              :disabled="streaming"
            ></textarea>
            <div class="flex flex-col gap-sm sm:flex-row sm:items-center sm:justify-between mt-2 px-2 pb-1">
              <div class="flex items-center gap-sm min-w-0 flex-wrap">
                <label class="flex items-center gap-xs min-w-0" title="选择本次对话使用的模型">
                  <span class="material-symbols-outlined text-[18px] text-outline">psychology</span>
                  <select
                    v-model="selectedModelCode"
                    :disabled="streaming"
                    class="model-select"
                  >
                    <option value="">系统默认模型</option>
                    <option v-for="model in chatModels" :key="model.modelCode" :value="model.modelCode">
                      {{ model.displayName }} / {{ model.providerName }}
                    </option>
                  </select>
                </label>
                <div class="inline-flex items-center bg-surface-container rounded-md p-0.5 border border-outline-variant">
                  <button
                    v-for="option in agentModeOptions"
                    :key="option.value"
                    type="button"
                    @click="selectedAgentMode = option.value"
                    class="px-2.5 py-1 rounded font-label-md text-label-md transition-colors"
                    :class="selectedAgentMode === option.value ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-outline hover:text-on-surface-variant'"
                    :title="option.title"
                  >
                    {{ option.label }}
                  </button>
                </div>
                <span
                  v-if="lastRouteDecision"
                  class="font-label-md text-label-md text-outline bg-surface-container px-2 py-1 rounded-md flex items-center gap-xs max-w-full"
                >
                  <span class="material-symbols-outlined text-[14px] flex-shrink-0">route</span>
                  <span class="truncate">{{ formatRouteDecision(lastRouteDecision) }}</span>
                </span>
              </div>
              <div class="flex items-center gap-xs self-end sm:self-auto">
                <!-- Recording status text indicator -->
                <span v-if="recording" class="text-error font-body-sm animate-pulse mr-xs flex items-center gap-2">
                  <span class="w-2 h-2 bg-error rounded-full animate-ping"></span>
                  正在录音...
                </span>
                <!-- Mic Button -->
                <button
                  @click="toggleRecording"
                  :disabled="streaming"
                  class="p-2 rounded-lg hover:bg-surface-container-high transition-colors flex items-center justify-center cursor-pointer shadow-sm"
                  :class="[recording ? 'bg-error-container text-error hover:bg-error-container-high' : 'bg-surface-container text-primary']"
                  :title="recording ? '结束录音并识别' : '语音对话'"
                >
                  <span class="material-symbols-outlined text-[20px]">
                    {{ recording ? 'mic_off' : 'mic' }}
                  </span>
                </button>
                <button
                  @click="sendMessage"
                  :disabled="streaming || !inputMessage.trim()"
                  class="bg-primary-container text-on-primary p-2 rounded-lg hover:opacity-90 active:opacity-100 transition-opacity flex items-center justify-center disabled:opacity-50 shadow-sm cursor-pointer"
                  title="发送消息"
                >
                  <span class="material-symbols-outlined text-[20px]" style="font-variation-settings: 'FILL' 1;">send</span>
                </button>
              </div>
            </div>
          </div>
          <p class="text-center font-label-md text-label-md text-outline mt-sm px-sm">大模型可能犯错。重要信息请通过引用来源进行复核。</p>
        </div>
      </div>
    </div>

    <CitationPanel
      :citations="citationsList"
      :retrieval-debug="retrievalDebug"
      :selected-index="selectedCitationIndex"
      @clear="clearCitations"
      @select="selectCitation"
    />

    <!-- PDF Preview Modal -->
    <div
      v-if="previewPdfUrl"
      class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-md"
      @click="previewPdfUrl = ''"
    >
      <div @click.stop class="bg-surface-container-lowest border border-outline-variant rounded-xl w-full max-w-4xl shadow-xl flex flex-col h-[85vh] overflow-hidden animate-scale-in">
        <header class="p-md border-b border-outline-variant flex justify-between items-center bg-surface-bright">
          <h3 class="font-headline-sm text-headline-sm text-on-background flex items-center gap-xs truncate pr-lg">
            <span class="material-symbols-outlined text-primary">picture_as_pdf</span>
            文档在线预览: {{ previewPdfName }}
          </h3>
          <button
            @click="previewPdfUrl = ''"
            class="text-on-surface-variant hover:bg-surface-container-high p-1 rounded-md transition-colors cursor-pointer flex-shrink-0"
          >
            <span class="material-symbols-outlined">close</span>
          </button>
        </header>
        <main class="flex-1 bg-surface-container-low p-sm flex items-center justify-center">
          <iframe
            :src="previewPdfUrl"
            class="w-full h-full border-none rounded-lg bg-white"
          ></iframe>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { useAuthStore } from '../store/auth'
import { ai, documents, llm } from '../api'
import { useRecorder } from '../composables/useRecorder'
import CitationPanel from '../components/CitationPanel.vue'
import { allowedUriPattern, isAllowedMarkdownLink } from '../utils/markdownSecurity'

const props = defineProps({
  newSessionTrigger: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['trigger-new-session'])

const authStore = useAuthStore()

const sessionsList = ref([])
const currentSessionId = ref('')
const chatMessages = ref([])
const citationsList = ref([])
const retrievalDebug = ref(null)
const selectedCitationIndex = ref(-1)
const inputMessage = ref('')
const streaming = ref(false)
const messageContainer = ref(null)
const hasNewTurns = ref(false)
const knowledgeBanner = ref(null)
const documentStatusMap = ref(new Map())
const selectedAgentMode = ref('AUTO')
const lastRouteDecision = ref(null)
const chatModels = ref([])
const selectedModelCode = ref('')
let knowledgePollTimer = null

const agentModeOptions = [
  { value: 'AUTO', label: '自动', title: '由路由模型判断使用哪种 agent 模式' },
  { value: 'REACT', label: 'ReAct', title: '直接使用当前多轮 RAG 对话模式' },
  { value: 'PLAN_EXECUTE', label: 'Plan + 执行', title: '先生成计划，确认后再执行' }
]

const previewPdfUrl = ref('')
const previewPdfName = ref('')

const { recording, toggleRecording, cleanup: cleanupRecordingResources } = useRecorder({
  transcribe: async (audioBlob) => {
    const res = await ai.asr(audioBlob)
    if (res.code === 200 && res.data) return res.data
    throw new Error(res.message || '语音识别失败，请检查密钥配置')
  },
  onPending: () => {
    inputMessage.value = '正在识别语音中...'
  },
  onText: (text) => {
    inputMessage.value = text
  },
  onError: (err, userMessage) => {
    console.error('录音失败:', err)
    inputMessage.value = ''
    alert(userMessage)
  }
})

const selectCitation = (cite, index) => {
  selectedCitationIndex.value = index
  openPdfFromCitation(cite)
}

const openPdfFromCitation = (cite) => {
  if (cite.minioUrl) {
    const ext = cite.sourceName?.split('.').pop().toLowerCase() || ''
    if (ext === 'pdf') {
      previewPdfUrl.value = buildPdfPreviewUrl(cite)
      previewPdfName.value = cite.sourceName
    } else {
      previewPdfUrl.value = cite.minioUrl
      previewPdfName.value = cite.sourceName
    }
  } else {
    alert('该演示引文不支持在线预览。')
  }
}

const buildPdfPreviewUrl = (cite) => {
  const page = extractCitationPage(cite)
  return page ? `${cite.minioUrl}#page=${page}` : cite.minioUrl
}

const extractCitationPage = (cite) => {
  const text = `${cite.label || ''} ${cite.text || ''}`
  const match = text.match(/(?:Page|第)\s*(\d+)/i)
  return match ? Number(match[1]) : null
}

// Watch layout-triggered event for new session
watch(() => props.newSessionTrigger, () => {
  initiateNewSession()
})

const handleBeforeUnload = () => {
  if (currentSessionId.value && hasNewTurns.value) {
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const data = JSON.stringify({
      sessionId: currentSessionId.value,
      userId: authStore.userId
    })
    fetch(`${baseURL}/ai/session/extract-profile`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'satoken': authStore.token
      },
      body: data,
      keepalive: true
    }).catch(() => {})
  }
}

onMounted(() => {
  loadSessions()
  loadChatModels()
  refreshKnowledgeStatus()
  knowledgePollTimer = setInterval(() => {
    refreshKnowledgeStatus()
  }, 4000)
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  if (knowledgePollTimer) {
    clearInterval(knowledgePollTimer)
  }
  recording.value = false
  cleanupRecordingResources()
  if (currentSessionId.value && hasNewTurns.value) {
    ai.extractProfile(currentSessionId.value, authStore.userId).catch(() => {})
  }
})

const loadChatModels = async () => {
  try {
    const res = await llm.listModels()
    if (res.code === 200) {
      chatModels.value = res.data || []
    }
  } catch (err) {
    console.error('获取模型列表失败:', err)
  }
}
const loadSessions = async () => {
  try {
    const res = await ai.listSessions(authStore.userId)
    if (res.code === 200) {
      sessionsList.value = res.data.sessions || []
      // Select the first session by default if available
      if (sessionsList.value.length > 0) {
        selectSession(sessionsList.value[0])
      } else {
        initiateNewSession()
      }
    }
  } catch (err) {
    console.error('获取会话列表失败:', err)
  }
}

const initiateNewSession = async () => {
  try {
    const res = await ai.createSession(authStore.userId)
    if (res.code === 200) {
      const newSession = res.data.sessionId
      sessionsList.value.unshift(newSession)
      currentSessionId.value = newSession
      chatMessages.value = []
      citationsList.value = []
      retrievalDebug.value = null
      selectedCitationIndex.value = -1
      lastRouteDecision.value = null
    }
  } catch (err) {
    console.error('创建新会话失败:', err)
  }
}

const selectSession = async (sessionId) => {
  if (currentSessionId.value && currentSessionId.value !== sessionId && hasNewTurns.value) {
    try {
      await ai.extractProfile(currentSessionId.value, authStore.userId)
    } catch (err) {
      console.error('切换会话时画像提炼失败:', err)
    }
  }

  currentSessionId.value = sessionId
  chatMessages.value = []
  citationsList.value = []
  retrievalDebug.value = null
  selectedCitationIndex.value = -1
  lastRouteDecision.value = null
  hasNewTurns.value = false
  try {
    const res = await ai.getHistory(sessionId)
    if (res.code === 200) {
      chatMessages.value = res.data.map(m => ({
        role: m.role, // 'user' or 'ai'
        content: m.content
      }))
      scrollToBottom()
    }
  } catch (err) {
    console.error('获取历史记录失败:', err)
  }
}

const confirmDeleteSession = async (sessionId) => {
  if (confirm('确认要删除此会话吗？删除后会话历史不可恢复。')) {
    try {
      const res = await ai.deleteSession(sessionId, authStore.userId)
      if (res.code === 200) {
        sessionsList.value = sessionsList.value.filter(s => s !== sessionId)
        if (currentSessionId.value === sessionId) {
          if (sessionsList.value.length > 0) {
            selectSession(sessionsList.value[0])
          } else {
            initiateNewSession()
          }
        }
      }
    } catch (err) {
      console.error('删除会话失败:', err)
    }
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || streaming.value) return

  const userQuery = inputMessage.value.trim()
  citationsList.value = []
  retrievalDebug.value = null
  selectedCitationIndex.value = -1
  updateKnowledgeBanner()
  chatMessages.value.push({ role: 'user', content: userQuery })
  inputMessage.value = ''
  scrollToBottom()

  streaming.value = true
  
  const aiMessageIndex = chatMessages.value.push({ role: 'ai', content: '' }) - 1

  await streamChatResponse({
    userQuery,
    aiMessageIndex,
    approvedPlanId: null
  })
}

const streamChatResponse = async ({ userQuery, aiMessageIndex, approvedPlanId }) => {
  try {
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(`${baseURL}/ai/multi-turn/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'satoken': authStore.token
      },
      body: JSON.stringify({
        userId: authStore.userId,
        sessionId: currentSessionId.value,
        turnCount: chatMessages.value.length - 1,
        message: userQuery,
        modeHint: selectedAgentMode.value,
        approvedPlanId,
        modelCode: selectedModelCode.value || null
      })
    })

    if (!response.ok) {
      throw new Error('对话流请求失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let done = false
    let buffer = ''

    while (!done) {
      const { value, done: readerDone } = await reader.read()
      done = readerDone
      if (value) {
        buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n').replace(/\r/g, '\n')
        const messages = buffer.split('\n\n')
        buffer = messages.pop() || ''

        for (const msg of messages) {
          handleSseMessage(msg, aiMessageIndex)
        }
        scrollToBottom()
      }
    }

    if (buffer) {
      handleSseMessage(buffer, aiMessageIndex)
      scrollToBottom()
    }

    hasNewTurns.value = true

  } catch (err) {
    console.error('Streaming error:', err)
    chatMessages.value[aiMessageIndex].content = '对不起，系统发生了异常，未能成功回答您的问题。'
  } finally {
    streaming.value = false
    scrollToBottom()
  }
}

const confirmPlan = async (plan, messageIndex) => {
  if (!plan?.planId || streaming.value) return
  chatMessages.value[messageIndex].plan = {
    ...plan,
    status: 'APPROVED'
  }
  chatMessages.value[messageIndex].content += '\n\n已确认，开始执行。'
  streaming.value = true
  const aiMessageIndex = chatMessages.value.push({ role: 'ai', content: '' }) - 1
  await streamChatResponse({
    userQuery: plan.message || '执行已确认的计划',
    aiMessageIndex,
    approvedPlanId: plan.planId
  })
}

const cancelPlan = (messageIndex) => {
  const current = chatMessages.value[messageIndex]
  if (!current?.plan) return
  current.plan = {
    ...current.plan,
    status: 'CANCELLED'
  }
  current.content += '\n\n已取消执行。'
}

const revisePlan = (plan, messageIndex) => {
  cancelPlan(messageIndex)
  inputMessage.value = plan?.message ? `${plan.message}\n\n请按以下修改重新规划：` : '请重新规划：'
  scrollToBottom()
}

const clearCitations = () => {
  citationsList.value = []
  selectedCitationIndex.value = -1
}

const parseSseMessage = (message) => {
  if (!message) return null

  let eventType = 'message'
  const dataLines = []

  for (const line of message.split('\n')) {
    if (!line) continue
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      let value = line.slice(5)
      if (value.startsWith(' ')) {
        value = value.slice(1)
      }
      dataLines.push(value)
    }
  }

  return {
    eventType,
    dataContent: dataLines.join('\n')
  }
}

const handleSseMessage = (message, aiMessageIndex) => {
  const parsed = parseSseMessage(message)
  if (!parsed) return

  if (parsed.eventType === 'citations') {
    try {
      citationsList.value = parsed.dataContent ? JSON.parse(parsed.dataContent) : []
      updateKnowledgeBanner()
    } catch (e) {
      console.error('解析引文失败:', e)
    }
    return
  }

  if (parsed.eventType === 'retrieval_debug') {
    try {
      retrievalDebug.value = parsed.dataContent ? JSON.parse(parsed.dataContent) : null
    } catch (e) {
      console.error('解析 RAG 诊断失败:', e)
    }
    return
  }

  if (parsed.eventType === 'route_decision') {
    try {
      lastRouteDecision.value = parsed.dataContent ? JSON.parse(parsed.dataContent) : null
    } catch (e) {
      console.error('解析路由决策失败:', e)
    }
    return
  }

  if (parsed.eventType === 'plan_required') {
    try {
      const plan = parsed.dataContent ? JSON.parse(parsed.dataContent) : null
      if (plan) {
        chatMessages.value[aiMessageIndex].content = `## 执行计划\n\n${plan.planText || ''}`
        chatMessages.value[aiMessageIndex].plan = plan
      }
    } catch (e) {
      console.error('解析计划失败:', e)
      chatMessages.value[aiMessageIndex].content = '计划生成失败，请稍后重试。'
    }
    return
  }

  if (parsed.eventType === 'token' || parsed.eventType === 'message') {
    chatMessages.value[aiMessageIndex].content += parsed.dataContent
    return
  }

  if (parsed.eventType === 'error') {
    chatMessages.value[aiMessageIndex].content += parsed.dataContent || 'AI 服务暂时不可用，请稍后重试。'
  }
}

const formatRouteDecision = (decision) => {
  const labels = {
    AUTO: '自动',
    REACT: 'ReAct',
    PLAN_EXECUTE: 'Plan + 执行'
  }
  return `${labels[decision.mode] || decision.mode} · ${decision.reason || '已路由'}`
}

const formatCitationPath = (cite) => {
  const segments = []
  const docName = cite.docTitle || cite.sourceName
  if (docName) segments.push(docName)
  if (cite.sectionTitle) segments.push(cite.sectionTitle)
  if (cite.chunkIndex) segments.push(`分段 ${cite.chunkIndex}`)
  return segments.join(' > ')
}

const formatSessionTitle = (sessionId) => {
  // Return readable title for sessions
  if (sessionId.length > 10) {
    return `会话: ${sessionId.substring(0, 8)}...`
  }
  return `会话: ${sessionId}`
}

const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true
})
markdownRenderer.validateLink = isAllowedMarkdownLink

const defaultLinkOpenRenderer = markdownRenderer.renderer.rules.link_open || ((tokens, idx, options, env, self) => {
  return self.renderToken(tokens, idx, options)
})

markdownRenderer.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpenRenderer(tokens, idx, options, env, self)
}

const sourcePillPattern = /(Page \d+|Section \d+|Chapter \d+|第\s*\d+\s*页|第\s*\d+\s*章|章节\s*\d+|分段\s*\d+)/g
const sourcePillDetectionPattern = /(Page \d+|Section \d+|Chapter \d+|第\s*\d+\s*页|第\s*\d+\s*章|章节\s*\d+|分段\s*\d+)/

const applySourcePills = (html) => {
  if (!html || typeof DOMParser === 'undefined' || typeof NodeFilter === 'undefined') return html || ''

  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  const root = doc.body.firstElementChild
  if (!root) return html

  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const textNodes = []

  while (walker.nextNode()) {
    const currentNode = walker.currentNode
    const parentTag = currentNode.parentElement?.tagName
    if (!currentNode.textContent || !sourcePillDetectionPattern.test(currentNode.textContent)) {
      continue
    }
    if (parentTag && ['CODE', 'PRE', 'A', 'SCRIPT', 'STYLE'].includes(parentTag)) {
      continue
    }
    textNodes.push(currentNode)
  }

  textNodes.forEach((node) => {
    const text = node.textContent || ''
    const fragment = doc.createDocumentFragment()
    let lastIndex = 0

    text.replace(sourcePillPattern, (match, _group, offset) => {
      if (offset > lastIndex) {
        fragment.appendChild(doc.createTextNode(text.slice(lastIndex, offset)))
      }

      const pill = doc.createElement('span')
      pill.className = 'source-pill'
      pill.textContent = match
      fragment.appendChild(pill)
      lastIndex = offset + match.length
      return match
    })

    if (lastIndex < text.length) {
      fragment.appendChild(doc.createTextNode(text.slice(lastIndex)))
    }

    node.parentNode?.replaceChild(fragment, node)
  })

  return root.innerHTML
}

const sanitizeHtml = (html) => {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'a', 'blockquote', 'br', 'code', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'hr', 'li', 'ol', 'p', 'pre', 's', 'span', 'strong', 'table', 'tbody', 'td',
      'th', 'thead', 'tr', 'ul'
    ],
    ALLOWED_ATTR: ['class', 'href', 'target', 'rel'],
    ALLOWED_URI_REGEXP: allowedUriPattern
  })
}

const secureRenderedLinks = (html) => {
  if (!html || typeof DOMParser === 'undefined') return html || ''

  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  const root = doc.body.firstElementChild
  if (!root) return html

  root.querySelectorAll('a[href]').forEach((link) => {
    const href = link.getAttribute('href')
    if (!isAllowedMarkdownLink(href)) {
      link.removeAttribute('href')
      link.removeAttribute('target')
      link.removeAttribute('rel')
      return
    }
    link.setAttribute('target', '_blank')
    link.setAttribute('rel', 'noopener noreferrer')
  })

  return root.innerHTML
}

const renderMarkdownToHtml = (content) => {
  if (!content) return ''

  const rendered = markdownRenderer.render(content)
  const enhanced = applySourcePills(rendered)
  return secureRenderedLinks(sanitizeHtml(enhanced))
}

const formatMessageContent = (content, role, index) => {
  if (!content) return ''

  if (role === 'ai' && streaming.value && index === chatMessages.value.length - 1) {
    return sanitizeHtml(content.replace(/\n/g, '<br />'))
  }

  return renderMarkdownToHtml(content)
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

const isProcessingStatus = (status) => {
  return ['UPLOADING', 'UPLOAD_SUCCESS', 'PROCESSING', 'CHUNKING', 'VECTORIZING', 'REINDEXING'].includes(status)
}

const updateKnowledgeBanner = () => {
  const statuses = Array.from(documentStatusMap.value.values())
  const processingCount = statuses.filter(isProcessingStatus).length
  const successCount = statuses.filter(status => status === 'SUCCESS').length
  const failedCount = statuses.filter(status => status === 'FAILED').length

  if (processingCount > 0) {
    knowledgeBanner.value = {
      kind: 'warning',
      text: `当前有 ${processingCount} 份文档仍在解析或向量化中。上传成功不代表已经可检索，请等待文档状态变为“成功”后再提问。`
    }
    return
  }

  if (successCount === 0 && failedCount > 0) {
    knowledgeBanner.value = {
      kind: 'error',
      text: '当前知识库里还没有可检索的成功文档，已有文档处理失败。请到“文档库管理”查看失败原因后重试上传。'
    }
    return
  }

  if (successCount === 0) {
    knowledgeBanner.value = {
      kind: 'warning',
      text: '当前还没有可检索的成功文档。本次回答可能不会引用知识库内容。'
    }
    return
  }

  if (chatMessages.value.length > 0 && citationsList.value.length === 0) {
    knowledgeBanner.value = {
      kind: 'warning',
      text: '本次回答未命中知识库引用，回答可能来自模型通用能力而不是上传文档。'
    }
    return
  }

  knowledgeBanner.value = null
}

const refreshKnowledgeStatus = async () => {
  try {
    const res = await documents.list({
      page: 1,
      pageSize: 100,
      userId: authStore.userId,
      sortBy: 'createdAt',
      sortOrder: 'DESC'
    })

    if (res.code !== 200) {
      return
    }

    const nextMap = new Map()
    for (const doc of (res.data.records || [])) {
      if (doc.fileHash) {
        nextMap.set(doc.fileHash, doc.status)
      }
    }
    documentStatusMap.value = nextMap
    updateKnowledgeBanner()
  } catch (err) {
    console.error('获取知识库状态失败:', err)
  }
}
</script>

<style scoped>
.model-select {
  max-width: 14rem;
  min-width: 9rem;
  border: 1px solid rgb(var(--tw-colors-outline-variant));
  border-radius: 0.375rem;
  background: rgb(var(--tw-colors-surface-container));
  padding: 0.25rem 0.5rem;
  color: rgb(var(--tw-colors-on-surface-variant));
  font-size: 0.75rem;
  line-height: 1rem;
  outline: none;
}

.model-select:focus {
  border-color: rgb(var(--tw-colors-primary));
}
</style>
