# 错误状态码体系文档

## 编码规范

### 格式定义

错误码采用 **`XYYZZZZ`** 七位编码格式：

| 位置 | 含义 | 说明 |
|------|------|------|
| X | 错误来源 | A=客户端错误, B=服务端错误, C=远程服务错误 |
| YY | 业务模块 | 00=通用, 01=认证, 02=用户, 03=文章, 04=分类, 05=标签, 06=管理, 07=安全 |
| ZZZZ | 具体错误 | 模块内唯一编号，0001=模块通用错误，0100+=具体错误 |

### 响应结构

```json
{
  "code": "A010101",
  "message": "用户名或密码错误",
  "data": null,
  "requestId": "xxx"
}
```

- `code` 为 `"0"` 时表示成功
- `code` 非 `"0"` 时为错误码，`message` 为对应的错误描述

---

## A: 客户端错误

### A00 通用客户端错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A000001 | `CLIENT_ERROR` | 客户端请求错误 | 通用客户端异常 | 检查请求参数和请求方式 |
| A000002 | `PARAM_VALIDATION_ERROR` | 参数校验失败 | 请求参数不符合 Bean Validation 规则 | 根据返回的 message 检查对应字段 |
| A000003 | `PARAM_REQUIRED_MISSING` | 必要参数缺失 | 缺少必要的请求参数 | 补充缺失的参数后重试 |
| A000004 | `OPERATION_TOO_FREQUENT` | 操作过于频繁，请稍后重试 | 短时间内重复提交（如并发收藏） | 等待一段时间后重试 |

### A01 认证模块错误

#### 登录相关

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A010001 | `AUTH_ERROR` | 认证模块错误 | 认证模块通用异常 | 检查认证相关参数 |
| A010100 | `USER_LOGIN_ERROR` | 用户登录失败 | 登录过程异常 | 检查用户名和密码 |
| A010101 | `USER_LOGIN_BAD_CREDENTIALS` | 用户名或密码错误 | 用户名不存在或密码不匹配 | 确认用户名和密码后重试 |
| A010102 | `USER_ACCOUNT_DISABLED` | 账户已被禁用，请联系管理员 | 管理员已禁用该账户 | 联系管理员启用账户 |
| A010103 | `AUTH_REFRESH_TOKEN_INVALID` | 无效的刷新令牌 | Refresh Token 过期或伪造 | 重新登录获取新的 Token |
| A010104 | `AUTH_USER_NOT_FOUND` | 用户不存在 | 刷新 Token 时对应用户已删除 | 重新注册或联系管理员 |

#### 注册相关

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A010200 | `USER_REGISTER_ERROR` | 用户注册失败 | 注册过程通用异常 | 检查注册信息 |
| A010201 | `USER_REGISTER_USERNAME_EXISTS` | 用户名已存在 | 用户名被其他用户注册 | 更换用户名 |
| A010202 | `USER_REGISTER_PHONE_EXISTS` | 手机号已被注册 | 手机号被其他用户注册 | 更换手机号或找回账号 |
| A010203 | `USER_REGISTER_DUPLICATE` | 用户名或手机号已存在 | 唯一键冲突但无法确定具体字段 | 更换用户名或手机号 |
| A010204 | `USER_REGISTER_USERNAME_OR_PASSWORD_BLANK` | 用户名或密码未填写 | 注册时未提供用户名或密码 | 填写用户名和密码 |
| A010205 | `USER_REGISTER_PHONE_BLANK` | 手机号未填写 | 注册时未提供手机号 | 填写手机号 |
| A010206 | `USER_REGISTER_REAL_NAME_BLANK` | 真实姓名未填写 | 注册时未提供真实姓名 | 填写真实姓名 |
| A010207 | `USER_REGISTER_EMAIL_BLANK` | 邮箱未填写 | 注册时未提供邮箱 | 填写邮箱地址 |

#### 密码相关

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A010300 | `PASSWORD_VERIFY_ERROR` | 密码校验失败 | 密码校验通用异常 | 检查密码格式 |
| A010301 | `PASSWORD_OLD_INCORRECT` | 旧密码错误 | 修改密码时旧密码输入错误 | 确认旧密码后重试 |
| A010302 | `PASSWORD_SAME_AS_OLD` | 新密码不能与旧密码相同 | 新密码与当前密码一致 | 设置不同的新密码 |
| A010303 | `PASSWORD_CHANGE_FAILED` | 修改密码失败 | 数据库更新失败 | 稍后重试或联系管理员 |

### A02 用户模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A020001 | `USER_ERROR` | 用户模块错误 | 用户模块通用异常 | 检查用户相关参数 |
| A020100 | `USER_NOT_FOUND` | 用户不存在 | 用户ID对应的记录不存在或已删除 | 确认用户ID是否正确 |
| A020101 | `USER_QUERY_FAILED` | 查询失败，用户不存在 | 查询用户信息时未找到对应用户 | 确认用户ID是否正确 |
| A020102 | `USER_UPDATE_FAILED` | 用户更新失败 | 用户ID为空或更新条件不满足 | 提供正确的用户信息 |
| A020103 | `USER_FOLLOW_PARAM_INVALID` | 关注参数无效 | 关注请求中 followerId 或 followeeId 为空 | 提供完整的关注参数 |
| A020104 | `USER_CANNOT_FOLLOW_SELF` | 不能关注自己 | 用户尝试关注自己 | 选择其他用户进行关注 |
| A020105 | `USER_FOLLOW_DUPLICATE` | 重复关注 | 已存在关注关系且并发插入 | 刷新页面查看当前关注状态 |

### A03 文章模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A030001 | `ARTICLE_ERROR` | 文章模块错误 | 文章模块通用异常 | 检查文章相关参数 |
| A030100 | `ARTICLE_NOT_FOUND` | 文章不存在 | 文章ID对应的记录不存在或已删除 | 确认文章ID是否正确 |
| A030101 | `ARTICLE_PARAM_REQUIRED` | 文章参数不能为空 | 创建/更新文章时未传递请求体 | 提供完整的文章参数 |
| A030102 | `ARTICLE_AUTHOR_REQUIRED` | 文章作者不能为空 | 创建文章时未指定作者 | 提供作者ID |
| A030103 | `ARTICLE_TITLE_REQUIRED` | 文章标题不能为空 | 创建文章时未填写标题 | 填写文章标题 |
| A030104 | `ARTICLE_CONTENT_REQUIRED` | 文章内容不能为空 | 创建文章时未填写内容 | 填写文章内容 |
| A030105 | `ARTICLE_UPDATE_FAILED` | 文章更新失败 | 文章不存在或更新条件不满足 | 确认文章存在后再更新 |
| A030106 | `ARTICLE_DELETE_FAILED` | 文章删除失败 | 文章不存在或已删除 | 确认文章存在后再删除 |
| A030107 | `ARTICLE_FAVORITE_PARAM_INCOMPLETE` | 收藏参数不完整 | 收藏请求中文章ID或用户ID为空 | 提供完整的收藏参数 |
| A030108 | `ARTICLE_OWNERSHIP_VERIFY_FAILED` | 无法验证文章所有权，请重新登录 | 用户上下文中无用户信息 | 重新登录获取有效Token |
| A030109 | `ARTICLE_PERMISSION_DENIED` | 权限不足，只能操作自己的文章 | 非管理员尝试操作他人文章 | 仅操作自己的文章或联系管理员 |

### A04 分类模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A040001 | `CATEGORY_ERROR` | 分类模块错误 | 分类模块通用异常 | 检查分类相关参数 |
| A040100 | `CATEGORY_NOT_FOUND` | 分类不存在 | 分类ID对应的记录不存在或已删除 | 确认分类ID是否正确 |
| A040101 | `CATEGORY_PARENT_NOT_FOUND` | 父分类不存在 | 指定的父分类ID不存在 | 确认父分类ID是否正确 |
| A040102 | `CATEGORY_LEVEL_EXCEEDED` | 分类层级不能超过三级 | 父分类已达到最大层级 | 选择层级较低的父分类 |
| A040103 | `CATEGORY_SLUG_EXISTS` | 分类别名已存在 | 分类别名（slug）已被其他分类使用 | 更换分类别名 |
| A040104 | `CATEGORY_CANNOT_SET_SELF_AS_PARENT` | 不能将自己设置为父分类 | 更新分类时将自身设为父分类 | 选择其他分类作为父分类 |
| A040105 | `CATEGORY_HAS_CHILDREN_CANNOT_CHANGE_PARENT` | 该分类下有子分类，不能修改父分类 | 修改父分类时当前分类仍有子分类 | 先移除或迁移子分类 |
| A040106 | `CATEGORY_HAS_CHILDREN_CANNOT_DELETE` | 该分类下有子分类，无法删除 | 删除分类时仍有子分类 | 先删除所有子分类 |
| A040107 | `CATEGORY_HAS_ARTICLES_CANNOT_DELETE` | 该分类下还有文章，无法删除 | 删除分类时仍有文章关联 | 先移除或迁移该分类下的文章 |

### A05 标签模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A050001 | `TAG_ERROR` | 标签模块错误 | 标签模块通用异常 | 检查标签相关参数 |
| A050100 | `TAG_NOT_FOUND` | 标签不存在 | 标签slug对应的记录不存在或已删除 | 确认标签slug是否正确 |
| A050101 | `TAG_SLUG_EXISTS` | 标签别名已存在 | 标签别名（slug）已被其他标签使用 | 更换标签别名 |
| A050102 | `TAG_HAS_ARTICLES_CANNOT_DELETE` | 该标签下还有文章关联，无法删除 | 删除标签时仍有文章关联 | 先移除文章与该标签的关联 |

### A06 管理模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A060001 | `ADMIN_ERROR` | 管理模块错误 | 管理模块通用异常 | 检查管理操作参数 |
| A060100 | `ADMIN_USER_NOT_FOUND` | 用户不存在 | 管理操作的目标用户不存在 | 确认目标用户ID |
| A060101 | `ADMIN_INVALID_ROLE_TYPE` | 无效的角色类型 | 指定的角色名称不在枚举范围内 | 使用有效的角色类型（DEVELOPER/USER） |
| A060102 | `ADMIN_INVALID_STATUS_VALUE` | 无效的状态值 | 状态值不是0或1 | 使用 0（禁用）或 1（启用） |
| A060103 | `ADMIN_CANNOT_MODIFY_OWN_ROLE` | 不能修改自己的角色 | 管理员尝试修改自身角色 | 让其他管理员操作 |
| A060104 | `ADMIN_CANNOT_MODIFY_OWN_STATUS` | 不能修改自己的状态 | 管理员尝试禁用自身 | 让其他管理员操作 |
| A060105 | `ADMIN_CANNOT_DELETE_SELF` | 不能删除自己 | 管理员尝试删除自身账号 | 让其他管理员操作 |

### A07 安全模块错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| A070001 | `SECURITY_ERROR` | 安全模块错误 | 安全模块通用异常 | 检查认证和授权状态 |
| A070100 | `SECURITY_NOT_AUTHENTICATED` | 未登录或Token已过期 | 请求未携带有效Token | 重新登录获取有效Token |
| A070101 | `SECURITY_ACCESS_DENIED` | 权限不足，无法访问该资源 | 当前用户角色无权访问该接口 | 联系管理员获取相应权限 |

---

## B: 服务端错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| B000001 | `SERVICE_ERROR` | 系统执行出错 | 服务端未捕获的异常 | 查看服务端日志定位问题，稍后重试 |
| B000100 | `SERVICE_TIMEOUT_ERROR` | 系统执行超时 | 服务端处理超时 | 稍后重试，如持续出现请联系管理员 |

---

## C: 远程服务错误

| 错误码 | 枚举值 | 错误信息 | 可能原因 | 处理建议 |
|--------|--------|---------|---------|---------|
| C000001 | `REMOTE_ERROR` | 调用第三方服务出错 | 第三方服务不可用或返回异常 | 检查第三方服务状态，稍后重试 |

---

## 异常处理机制

### 异常体系

```
RuntimeException
  └── AbstractException (errorCode, errorMessage)
        ├── ClientException      → 客户端错误（A类）
        ├── ServiceException     → 服务端错误（B类）
        └── RemoteException      → 远程服务错误（C类）
```

### 全局异常处理

| 拦截异常 | 错误码 | 说明 |
|---------|--------|------|
| `MethodArgumentNotValidException` | A000002 | Bean Validation 参数校验失败 |
| `ClientException` | 对应错误码 | 业务校验失败 |
| `ServiceException` | 对应错误码 | 服务端业务异常 |
| `RemoteException` | 对应错误码 | 远程服务调用异常 |
| `BadCredentialsException` / `UsernameNotFoundException` | A010101 | Spring Security 登录失败 |
| 其他 `Throwable` | B000001 | 兜底处理，未预期的服务端异常 |

### 使用方式

```java
// 推荐方式：使用 ErrorCode 枚举
throw new ClientException(ErrorCode.ARTICLE_NOT_FOUND);

// 不推荐：硬编码消息（所有错误统一返回 A000001）
throw new ClientException("文章不存在");
```

使用 `ErrorCode` 枚举时，异常会自动携带对应的错误码和错误信息，前端可根据 `code` 精确判断错误类型并做相应处理。
