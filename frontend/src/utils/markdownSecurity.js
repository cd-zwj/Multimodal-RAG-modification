const SAFE_PROTOCOL_PATTERN = /^(https?:|mailto:)/i
const SAFE_RELATIVE_PATTERN = /^(\/(?!\/)|#|\?|\.\.?\/)/

export const isAllowedMarkdownLink = (url) => {
  if (!url || typeof url !== 'string') {
    return false
  }

  const trimmed = url.trim()
  if (!trimmed) {
    return false
  }

  if (SAFE_RELATIVE_PATTERN.test(trimmed)) {
    return true
  }

  return SAFE_PROTOCOL_PATTERN.test(trimmed)
}

export const allowedUriPattern = /^(?:(?:https?|mailto):|\/(?!\/)|#|\?|\.\.?\/)/i
