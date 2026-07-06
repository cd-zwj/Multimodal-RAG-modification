import assert from 'node:assert/strict'
import { JSDOM } from 'jsdom'
import createDOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { allowedUriPattern, isAllowedMarkdownLink } from '../src/utils/markdownSecurity.js'

const allowed = [
  'https://example.com/path',
  'http://example.com/path',
  'mailto:security@example.com',
  '/docs/page',
  './relative',
  '../relative',
  '#section',
  '?q=test'
]

const rejected = [
  '',
  '   ',
  'javascript:alert(1)',
  ' JaVaScRiPt:alert(1)',
  'data:text/html,<svg onload=alert(1)>',
  'vbscript:msgbox(1)',
  '//evil.example.com/path',
  'ftp://example.com/file'
]

for (const url of allowed) {
  assert.equal(isAllowedMarkdownLink(url), true, `${url} should be allowed`)
}

for (const url of rejected) {
  assert.equal(isAllowedMarkdownLink(url), false, `${url} should be rejected`)
}

const { window } = new JSDOM('')
const DOMPurify = createDOMPurify(window)
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

const sanitizeHtml = (html) => DOMPurify.sanitize(html, {
  ALLOWED_TAGS: [
    'a', 'blockquote', 'br', 'code', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'hr', 'li', 'ol', 'p', 'pre', 's', 'span', 'strong', 'table', 'tbody', 'td',
    'th', 'thead', 'tr', 'ul'
  ],
  ALLOWED_ATTR: ['class', 'href', 'target', 'rel'],
  ALLOWED_URI_REGEXP: allowedUriPattern
})

const secureRenderedLinks = (html) => {
  const doc = new JSDOM(`<div>${html}</div>`).window.document
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

const renderMarkdownToHtml = (content) => secureRenderedLinks(sanitizeHtml(markdownRenderer.render(content)))

const safeLinkHtml = renderMarkdownToHtml('[safe](https://example.com/docs)')
assert.match(safeLinkHtml, /href="https:\/\/example\.com\/docs"/)
assert.match(safeLinkHtml, /target="_blank"/)
assert.match(safeLinkHtml, /rel="noopener noreferrer"/)

const maliciousSamples = [
  '[x](javascript:alert(1))',
  '[x](JaVaScRiPt:alert(1))',
  '[x](data:text/html,<svg onload=alert(1)>)',
  '[x](//evil.example.com/path)',
  '<img src=x onerror=alert(1)>',
  '<svg><script>alert(1)</script></svg>',
  '<a href="javascript:alert(1)" target="_blank">x</a>'
]

for (const sample of maliciousSamples) {
  const html = renderMarkdownToHtml(sample)
  const doc = new JSDOM(`<main>${html}</main>`).window.document
  assert.equal(doc.querySelector('[onerror]'), null, `${sample} should not keep event attributes`)
  assert.equal(doc.querySelector('script'), null, `${sample} should not keep script tags`)
  assert.equal(doc.querySelector('svg'), null, `${sample} should not keep svg tags`)
  for (const link of doc.querySelectorAll('a[href]')) {
    const href = link.getAttribute('href')
    assert.equal(isAllowedMarkdownLink(href), true, `${sample} should not keep unsafe href ${href}`)
    assert.doesNotMatch(href, /^\/\//, `${sample} should not keep protocol-relative links`)
  }
}
