import { ref } from 'vue'

const downsampleBuffer = (buffer, inputSampleRate, outputSampleRate = 16000) => {
  if (inputSampleRate === outputSampleRate) return buffer
  if (inputSampleRate < outputSampleRate) throw new Error('输入采样率过低')

  const sampleRateRatio = inputSampleRate / outputSampleRate
  const newLength = Math.round(buffer.length / sampleRateRatio)
  const result = new Float32Array(newLength)
  let offsetResult = 0
  let offsetBuffer = 0

  while (offsetResult < result.length) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio)
    let accum = 0
    let count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
      accum += buffer[i]
      count++
    }
    result[offsetResult] = accum / count
    offsetResult++
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

export function useRecorder({ transcribe, onText, onPending, onError }) {
  const recording = ref(false)
  let audioContext = null
  let mediaStream = null
  let recorderNode = null
  let sourceNode = null
  let audioBuffers = []

  const startRecording = async () => {
    try {
      audioBuffers = []
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
      if (!audioContext.audioWorklet) {
        throw new Error('当前浏览器不支持 AudioWorklet 录音')
      }

      await audioContext.audioWorklet.addModule('/pcm-recorder-worklet.js')
      sourceNode = audioContext.createMediaStreamSource(mediaStream)
      recorderNode = new AudioWorkletNode(audioContext, 'pcm-recorder-processor')
      recorderNode.port.onmessage = (event) => {
        audioBuffers.push(new Float32Array(event.data))
      }
      sourceNode.connect(recorderNode)
      recording.value = true
    } catch (err) {
      await cleanup()
      onError?.(err, '启动麦克风录音失败，请确保已授予麦克风权限！')
    }
  }

  const stopAndTranscribe = async () => {
    if (!recording.value) return
    recording.value = false

    try {
      const inputSampleRate = audioContext?.sampleRate || 16000
      await cleanup()
      const totalLength = audioBuffers.reduce((acc, buf) => acc + buf.length, 0)
      if (totalLength === 0) {
        onError?.(new Error('录音为空'), '录音为空，请再试一次！')
        return
      }

      const mergedBuffer = new Float32Array(totalLength)
      let offset = 0
      for (const buf of audioBuffers) {
        mergedBuffer.set(buf, offset)
        offset += buf.length
      }

      const downsampled = downsampleBuffer(mergedBuffer, inputSampleRate, 16000)
      const pcmBuffer = new Int16Array(downsampled.length)
      for (let i = 0; i < downsampled.length; i++) {
        const s = Math.max(-1, Math.min(1, downsampled[i]))
        pcmBuffer[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
      }

      onPending?.()
      const audioBlob = new Blob([pcmBuffer.buffer], { type: 'audio/pcm' })
      const text = await transcribe(audioBlob)
      onText?.(text)
    } catch (err) {
      onError?.(err, `录音处理或识别失败：${err.message}`)
    }
  }

  const toggleRecording = async () => {
    if (recording.value) {
      await stopAndTranscribe()
    } else {
      await startRecording()
    }
  }

  const cleanup = async () => {
    if (recorderNode) {
      recorderNode.port.onmessage = null
      recorderNode.disconnect()
      recorderNode = null
    }
    if (sourceNode) {
      sourceNode.disconnect()
      sourceNode = null
    }
    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop())
      mediaStream = null
    }
    if (audioContext) {
      await audioContext.close()
      audioContext = null
    }
  }

  return {
    recording,
    toggleRecording,
    cleanup
  }
}
