/**
 * 编辑器开发验证 harness（仅 dev 环境使用，不进入生产路由）。
 * 通过 /editor-test.html 访问，供 Playwright 无头测试驱动。
 */
import '@/styles/main.css'
import '@/styles/typography.css'
import 'katex/dist/katex.min.css'

import { createApp, h, ref } from 'vue'
import MarkdownWysiwygEditor from '@/components/editor/MarkdownWysiwygEditor.vue'
import fixture from '@/editor/__fixtures__/roundtrip-article.md?raw'

declare global {
  interface Window {
    __test: {
      set: (md: string) => void
      get: () => string
      fixture: string
    }
  }
}

const content = ref('')

window.__test = {
  set: (md: string) => {
    content.value = md
  },
  get: () => content.value,
  fixture,
}

const App = {
  setup() {
    return () =>
      // 完整复刻 DashboardLayout → PostEditorView 的高度链（min-h-screen，无固定视口高度）
      h('div', { style: 'min-height: 100vh; display: flex; background: var(--bg-primary);' }, [
        h('main', { style: 'flex: 1; padding: 2rem; min-width: 0; display: flex; flex-direction: column;' }, [
          h(
            'div',
            { style: 'max-width: 1400px; margin: 0 auto; flex: 1; display: flex; flex-direction: column; min-height: 0; width: 100%;' },
            [
              h('div', { style: 'display: flex; gap: 1.5rem; flex: 1; min-height: 0;' }, [
                h(
                  'div',
                  { style: 'flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0;' },
                  [
                    h('input', {
                      placeholder: '文章标题...',
                      style: 'padding: 0.75rem 0; font-size: 1.5rem; font-weight: 600; background: transparent; border: none; outline: none; margin-bottom: 1rem;',
                    }),
                    h(
                      'div',
                      {
                        style:
                          'height: calc(100vh - 280px); min-height: 500px; border: 1px solid var(--border); border-radius: 12px; overflow: hidden; background: var(--bg-secondary);',
                      },
                      [
                        h(MarkdownWysiwygEditor, {
                          modelValue: content.value,
                          'onUpdate:modelValue': (v: string) => {
                            content.value = v
                          },
                          placeholder: '开始创作，输入 / 唤起更多功能...',
                        }),
                      ],
                    ),
                  ],
                ),
              ]),
            ],
          ),
        ]),
      ])
  },
}

createApp(App).mount('#app')
