class PcmRecorderProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0]
    if (!input || input.length === 0 || !input[0]) {
      return true
    }

    this.port.postMessage(input[0].slice(0))
    return true
  }
}

registerProcessor('pcm-recorder-processor', PcmRecorderProcessor)
