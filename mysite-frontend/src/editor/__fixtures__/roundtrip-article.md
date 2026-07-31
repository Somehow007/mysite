# Round-trip 兼容性测试文章

> 本文档覆盖 `useMarkdown.ts` 渲染管线支持的全部 Markdown 方言，
> 用于验证新编辑器「打开 → 编辑 → 保存」后内容无损（round-trip）。
> 验收方式：经编辑器序列化后，语义与渲染结果必须与原文一致。

## 一、行内格式

这段包含**加粗**、*斜体*、~~删除线~~、`行内代码`、<u>下划线 HTML</u>，
以及 [站内链接](/posts) 和 [外部链接](https://example.com "带标题的链接")。

组合格式：**粗体中的 *斜体* 和 `代码`**，以及 [链接中的 **加粗**](https://example.com)。

## 二、标题层级

### 三级标题
#### 四级标题
##### 五级标题
###### 六级标题

## 三、列表

### 无序列表

- 第一项
- 第二项
  - 嵌套子项 A
  - 嵌套子项 B
    - 三级嵌套
- 第三项

### 有序列表

1. 第一步
2. 第二步
   1. 子步骤 2.1
   2. 子步骤 2.2
3. 第三步

### 任务列表

- [x] 已完成任务
- [ ] 未完成任务
- [ ] 包含 **格式** 的任务

## 四、引用与 Callout

### 普通引用

> 这是一段普通引用。
> 引用可以有多行。
>
> > 嵌套引用。

### Callout：信息类

> [!NOTE] 备注标题
> 这是备注内容，支持 **行内格式** 和 `代码`。

> [!TIP]
> 无标题提示，只有正文。

> [!WARNING] 警告
> 多行内容第一行。
> 多行内容第二行。

### Callout：错误类

> [!ERROR] 出错了
> 错误详情。

### Callout：其他类型抽样

> [!SUCCESS] 成功
> 操作成功。

> [!QUESTION] 疑问
> 这是一个问题？

> [!EXAMPLE] 示例
> 示例内容。

## 五、代码块

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, 世界");
    }
}
```

```typescript
const greeting: string = "你好"
console.log(greeting)
```

```bash
# 无语言标识的 shell 注释测试
echo "hello"
```

```
无语言代码块
```

## 六、数学公式

行内公式：质能方程 $E = mc^2$ 与欧拉公式 $e^{i\pi} + 1 = 0$。

块级公式：

$$
\int_{-\infty}^{\infty} e^{-x^2} \, dx = \sqrt{\pi}
$$

$$
\frac{\partial}{\partial t} \Psi = \frac{i\hbar}{2m} \nabla^2 \Psi
$$

## 七、表格

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| title | varchar | 标题，支持 **加粗** |
| content | text | 正文，含 `代码` |

## 八、图片

![本地图片示例](/uploads/images/example.png)

![带空 alt 的图片]()

## 九、分割线与其他

上文内容。

---

下文内容。

混合场景：列表中嵌套代码块与公式。

- 列表项内公式 $a^2 + b^2 = c^2$
- 列表项内代码：

  ```python
  print("嵌套代码块")
  ```

- 列表项内引用：
  > 列表里的引用内容

结尾段落：中文长句测试，包含标点符号，全角字符！以及 English mixed content 混排场景。
