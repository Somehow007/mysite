# 分类模块 API 文档

## 概述

分类模块已完成全面的完善与优化，支持多级分类（最多三级）、分类属性扩展、缓存策略、权限控制等功能。本文档描述了所有可用的API接口。

## 数据结构

### 分类实体 (Category)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 分类ID |
| name | String | 分类名称 |
| slug | String | URL友好别名（唯一） |
| description | String | 分类描述 |
| sortOrder | Integer | 排序值 |
| parentId | Long | 父分类ID |
| level | Integer | 分类层级（1-3） |
| path | String | 分类路径（如：1,2,3） |
| status | Integer | 状态（0:禁用 1:启用） |
| icon | String | 分类图标 |
| color | String | 分类颜色 |
| seoTitle | String | SEO标题 |
| seoDescription | String | SEO描述 |
| seoKeywords | String | SEO关键词 |
| articleCount | Long | 文章数量 |
| children | List<Category> | 子分类列表 |

## API 接口

### 1. 获取所有分类

**接口地址**：`GET /v1/categories`

**权限**：公开

**返回示例**：
```json
[
  {
    "id": "1",
    "name": "技术",
    "slug": "tech",
    "description": "技术相关文章",
    "sortOrder": 0,
    "parentId": null,
    "level": 1,
    "path": "1",
    "status": 1,
    "articleCount": 10
  }
]
```

### 2. 获取分类树形结构

**接口地址**：`GET /v1/categories/tree`

**权限**：公开

**返回示例**：
```json
[
  {
    "id": "1",
    "name": "技术",
    "slug": "tech",
    "level": 1,
    "children": [
      {
        "id": "2",
        "name": "前端开发",
        "slug": "frontend",
        "parentId": "1",
        "level": 2,
        "children": []
      }
    ]
  }
]
```

### 3. 查询分类（支持多条件筛选）

**接口地址**：`GET /v1/categories/query`

**权限**：公开

**请求参数**：
- `name`: 分类名称（模糊查询）
- `parentId`: 父分类ID
- `level`: 分类层级
- `status`: 状态
- `tree`: 是否返回树形结构
- `current`: 页码
- `size`: 每页大小

**返回示例**：
```json
[
  {
    "id": "1",
    "name": "技术",
    "slug": "tech",
    "status": 1,
    "articleCount": 10
  }
]
```

### 4. 根据ID获取分类详情

**接口地址**：`GET /v1/categories/id/{id}`

**权限**：公开

**路径参数**：
- `id`: 分类ID

### 5. 根据slug获取分类详情

**接口地址**：`GET /v1/categories/{slug}`

**权限**：公开

**路径参数**：
- `slug`: 分类别名

### 6. 获取子分类列表

**接口地址**：`GET /v1/categories/{parentId}/children`

**权限**：需要认证

**路径参数**：
- `parentId`: 父分类ID

### 7. 创建分类

**接口地址**：`POST /v1/categories`

**权限**：需要认证

**请求体**：
```json
{
  "name": "技术",
  "slug": "tech",
  "description": "技术相关文章",
  "sortOrder": 0,
  "parentId": null,
  "level": 1,
  "status": 1,
  "icon": "tech-icon",
  "color": "#1890ff",
  "seoTitle": "技术文章分类",
  "seoDescription": "技术相关文章分类",
  "seoKeywords": "技术,编程,开发"
}
```

**必填字段**：
- `name`: 分类名称
- `slug`: 分类别名

### 8. 更新分类

**接口地址**：`PUT /v1/categories/{id}`

**权限**：需要认证

**路径参数**：
- `id`: 分类ID

**请求体**：
```json
{
  "name": "更新后的分类",
  "slug": "updated-category",
  "description": "更新后的描述",
  "sortOrder": 10,
  "status": 1
}
```

### 9. 删除分类

**接口地址**：`DELETE /v1/categories/{id}`

**权限**：需要认证

**路径参数**：
- `id`: 分类ID

**注意**：
- 如果分类下有子分类，无法删除
- 如果分类下有文章，无法删除

### 10. 更新分类状态

**接口地址**：`PATCH /v1/categories/{id}/status`

**权限**：需要认证

**路径参数**：
- `id`: 分类ID

**请求参数**：
- `status`: 状态（0:禁用 1:启用）

### 11. 批量更新分类状态

**接口地址**：`PATCH /v1/categories/batch/status`

**权限**：需要认证

**请求体**：
```json
{
  "ids": ["1", "2", "3"],
  "status": 0
}
```

### 12. 批量删除分类

**接口地址**：`DELETE /v1/categories/batch`

**权限**：需要认证

**请求体**：
```json
{
  "ids": ["1", "2", "3"]
}
```

### 13. 更新分类排序

**接口地址**：`PATCH /v1/categories/sort`

**权限**：需要认证

**请求体**：
```json
{
  "id": "1",
  "sortOrder": 10
}
```

## 缓存策略

分类模块使用 Redis 缓存，缓存策略如下：

1. **缓存名称**：
   - `categories`: 分类列表缓存
   - `category_tree`: 分类树缓存

2. **缓存时间**：
   - 分类列表缓存：2小时
   - 分类树缓存：1小时

3. **缓存失效**：
   - 创建、更新、删除分类时自动清除相关缓存
   - 批量操作时清除所有缓存

## 权限控制

1. **公开接口**：
   - 获取所有分类
   - 获取分类树形结构
   - 查询分类
   - 根据ID/slug获取分类详情

2. **需要认证的接口**：
   - 创建分类
   - 更新分类
   - 删除分类
   - 更新分类状态
   - 批量操作
   - 更新排序

## 错误提示

所有错误都会返回清晰的错误信息：

```json
{
  "code": "A000001",
  "message": "分类不存在",
  "data": null
}
```

常见错误：
- `分类不存在`: 指定的分类ID不存在
- `分类别名已存在`: slug重复
- `父分类不存在`: 指定的父分类不存在
- `分类层级不能超过三级`: 尝试创建第四级分类
- `该分类下还有文章，无法删除`: 分类下有文章
- `该分类下有子分类，无法删除`: 分类下有子分类
- `不能将自己设置为父分类`: 父分类ID不能等于自己的ID

## 数据库迁移

执行以下SQL脚本进行数据库迁移：

```bash
mysql -u root -p mysite < docker/init/migration_category.sql
```

## 测试

运行单元测试：

```bash
./mvnw test
```

## 前端使用

前端分类管理界面位于：`/dashboard/categories`

功能包括：
- 树形展示分类结构
- 创建、编辑、删除分类
- 启用/禁用分类
- 批量删除
- 添加子分类（最多三级）
- SEO设置
