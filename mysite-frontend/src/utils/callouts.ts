/**
 * Callout（Obsidian 式 `> [!TYPE]` 标注）共享常量。
 *
 * 站点有两处需要同一份类型定义，必须保持一致：
 * - 渲染管线 composables/useMarkdown.ts（marked 输出 HTML 后正则替换为卡片）
 * - 编辑器 editor/calloutPlugin.ts（所见即所得卡片 NodeView）
 */

export interface CalloutConfig {
  icon: string
  color: string
}

/** 22 种受支持的 callout 类型（小写），与 useMarkdown.ts 识别集合一致 */
export const CALLOUT_TYPES = [
  'note',
  'info',
  'todo',
  'tip',
  'success',
  'check',
  'done',
  'question',
  'warning',
  'caution',
  'attention',
  'failure',
  'fail',
  'missing',
  'error',
  'danger',
  'bug',
  'example',
  'quote',
  'cite',
  'abstract',
  'summary',
  'tldr',
] as const

export type CalloutTypeName = (typeof CALLOUT_TYPES)[number]

export const CALLOUT_CONFIG: Record<string, CalloutConfig> = {
  note: { icon: '📝', color: '#448aff' },
  info: { icon: 'ℹ️', color: '#448aff' },
  todo: { icon: '☑️', color: '#448aff' },
  tip: { icon: '💡', color: '#00c853' },
  success: { icon: '✅', color: '#00c853' },
  check: { icon: '✔️', color: '#00c853' },
  done: { icon: '🏁', color: '#00c853' },
  warning: { icon: '⚠️', color: '#ff9100' },
  caution: { icon: '⚠️', color: '#ff9100' },
  question: { icon: '❓', color: '#ff9100' },
  attention: { icon: '👀', color: '#ff9100' },
  error: { icon: '❌', color: '#ff1744' },
  danger: { icon: '⚡', color: '#ff1744' },
  failure: { icon: '🚫', color: '#ff1744' },
  bug: { icon: '🐛', color: '#ff1744' },
  example: { icon: '📋', color: '#7c4dff' },
  quote: { icon: '💬', color: '#9e9e9e' },
  cite: { icon: '📖', color: '#9e9e9e' },
  abstract: { icon: '📄', color: '#9e9e9e' },
  summary: { icon: '📊', color: '#9e9e9e' },
  tldr: { icon: '⚡', color: '#9e9e9e' },
}

/** 类型未单独配置时（如 fail / missing）回退到 note 的展示样式 */
export function getCalloutConfig(type: string): CalloutConfig {
  return CALLOUT_CONFIG[type.toLowerCase()] ?? CALLOUT_CONFIG['note']!
}

export function isCalloutType(value: string): boolean {
  return (CALLOUT_TYPES as readonly string[]).includes(value.toLowerCase())
}

export interface CalloutMenuItem {
  /** 大写类型名，如 NOTE */
  type: string
  label: string
  icon: string
}

export interface CalloutMenuGroup {
  label: string
  color: string
  types: CalloutMenuItem[]
}

/** 斜杠菜单/类型选择器的分组数据（沿用旧编辑器的 5 组划分） */
export const CALLOUT_GROUPS: CalloutMenuGroup[] = [
  {
    label: '信息',
    color: '#448aff',
    types: [
      { type: 'NOTE', label: '备注', icon: '📝' },
      { type: 'INFO', label: '信息', icon: 'ℹ️' },
      { type: 'TODO', label: '待办', icon: '☑️' },
    ],
  },
  {
    label: '成功/提示',
    color: '#00c853',
    types: [
      { type: 'TIP', label: '提示', icon: '💡' },
      { type: 'SUCCESS', label: '成功', icon: '✅' },
      { type: 'CHECK', label: '检查', icon: '✔️' },
      { type: 'DONE', label: '完成', icon: '🏁' },
    ],
  },
  {
    label: '警告/注意',
    color: '#ff9100',
    types: [
      { type: 'WARNING', label: '警告', icon: '⚠️' },
      { type: 'CAUTION', label: '注意', icon: '⚠️' },
      { type: 'QUESTION', label: '问题', icon: '❓' },
      { type: 'ATTENTION', label: '关注', icon: '👀' },
    ],
  },
  {
    label: '错误/危险',
    color: '#ff1744',
    types: [
      { type: 'ERROR', label: '错误', icon: '❌' },
      { type: 'DANGER', label: '危险', icon: '⚡' },
      { type: 'FAILURE', label: '失败', icon: '🚫' },
      { type: 'BUG', label: '缺陷', icon: '🐛' },
    ],
  },
  {
    label: '示例/引用',
    color: '#7c4dff',
    types: [
      { type: 'EXAMPLE', label: '示例', icon: '📋' },
      { type: 'QUOTE', label: '引用', icon: '💬' },
      { type: 'ABSTRACT', label: '摘要', icon: '📄' },
      { type: 'SUMMARY', label: '总结', icon: '📊' },
    ],
  },
]
