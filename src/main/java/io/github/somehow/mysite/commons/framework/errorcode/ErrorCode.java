package io.github.somehow.mysite.commons.framework.errorcode;

public enum ErrorCode implements IErrorCode {

    CLIENT_ERROR("A000001", "客户端请求错误"),
    PARAM_VALIDATION_ERROR("A000002", "参数校验失败"),
    PARAM_REQUIRED_MISSING("A000003", "必要参数缺失"),
    OPERATION_TOO_FREQUENT("A000004", "操作过于频繁，请稍后重试"),

    AUTH_ERROR("A010001", "认证模块错误"),
    USER_LOGIN_ERROR("A010100", "用户登录失败"),
    USER_LOGIN_BAD_CREDENTIALS("A010101", "用户名或密码错误"),
    USER_ACCOUNT_DISABLED("A010102", "账户已被禁用，请联系管理员"),
    AUTH_REFRESH_TOKEN_INVALID("A010103", "无效的刷新令牌"),
    AUTH_USER_NOT_FOUND("A010104", "用户不存在"),

    USER_REGISTER_ERROR("A010200", "用户注册失败"),
    USER_REGISTER_USERNAME_EXISTS("A010201", "用户名已存在"),
    USER_REGISTER_PHONE_EXISTS("A010202", "手机号已被注册"),
    USER_REGISTER_DUPLICATE("A010203", "用户名或手机号已存在"),
    USER_REGISTER_USERNAME_OR_PASSWORD_BLANK("A010204", "用户名或密码未填写"),
    USER_REGISTER_PHONE_BLANK("A010205", "手机号未填写"),
    USER_REGISTER_REAL_NAME_BLANK("A010206", "真实姓名未填写"),
    USER_REGISTER_EMAIL_BLANK("A010207", "邮箱未填写"),

    PASSWORD_VERIFY_ERROR("A010300", "密码校验失败"),
    PASSWORD_OLD_INCORRECT("A010301", "旧密码错误"),
    PASSWORD_SAME_AS_OLD("A010302", "新密码不能与旧密码相同"),
    PASSWORD_CHANGE_FAILED("A010303", "修改密码失败"),

    USER_ERROR("A020001", "用户模块错误"),
    USER_NOT_FOUND("A020100", "用户不存在"),
    USER_QUERY_FAILED("A020101", "查询失败，用户不存在"),
    USER_UPDATE_FAILED("A020102", "用户更新失败"),
    USER_FOLLOW_PARAM_INVALID("A020103", "关注参数无效"),
    USER_CANNOT_FOLLOW_SELF("A020104", "不能关注自己"),
    USER_FOLLOW_DUPLICATE("A020105", "重复关注"),

    // ---------- A03: 文章模块错误 ----------
    ARTICLE_ERROR("A030001", "文章模块错误"),
    ARTICLE_NOT_FOUND("A030100", "文章不存在"),
    ARTICLE_PARAM_REQUIRED("A030101", "文章参数不能为空"),
    ARTICLE_AUTHOR_REQUIRED("A030102", "文章作者不能为空"),
    ARTICLE_TITLE_REQUIRED("A030103", "文章标题不能为空"),
    ARTICLE_CONTENT_REQUIRED("A030104", "文章内容不能为空"),
    ARTICLE_UPDATE_FAILED("A030105", "文章更新失败"),
    ARTICLE_DELETE_FAILED("A030106", "文章删除失败"),
    ARTICLE_FAVORITE_PARAM_INCOMPLETE("A030107", "收藏参数不完整"),
    ARTICLE_OWNERSHIP_VERIFY_FAILED("A030108", "无法验证文章所有权，请重新登录"),
    ARTICLE_PERMISSION_DENIED("A030109", "权限不足，只能操作自己的文章"),

    // ---------- A04: 分类模块错误 ----------
    CATEGORY_ERROR("A040001", "分类模块错误"),
    CATEGORY_NOT_FOUND("A040100", "分类不存在"),
    CATEGORY_PARENT_NOT_FOUND("A040101", "父分类不存在"),
    CATEGORY_LEVEL_EXCEEDED("A040102", "分类层级不能超过三级"),
    CATEGORY_SLUG_EXISTS("A040103", "分类别名已存在"),
    CATEGORY_CANNOT_SET_SELF_AS_PARENT("A040104", "不能将自己设置为父分类"),
    CATEGORY_HAS_CHILDREN_CANNOT_CHANGE_PARENT("A040105", "该分类下有子分类，不能修改父分类"),
    CATEGORY_HAS_CHILDREN_CANNOT_DELETE("A040106", "该分类下有子分类，无法删除"),
    CATEGORY_HAS_ARTICLES_CANNOT_DELETE("A040107", "该分类下还有文章，无法删除"),

    // ---------- A05: 标签模块错误 ----------
    TAG_ERROR("A050001", "标签模块错误"),
    TAG_NOT_FOUND("A050100", "标签不存在"),
    TAG_SLUG_EXISTS("A050101", "标签别名已存在"),
    TAG_HAS_ARTICLES_CANNOT_DELETE("A050102", "该标签下还有文章关联，无法删除"),

    ADMIN_ERROR("A060001", "管理模块错误"),
    ADMIN_USER_NOT_FOUND("A060100", "用户不存在"),
    ADMIN_INVALID_ROLE_TYPE("A060101", "无效的角色类型"),
    ADMIN_INVALID_STATUS_VALUE("A060102", "无效的状态值"),
    ADMIN_CANNOT_MODIFY_OWN_ROLE("A060103", "不能修改自己的角色"),
    ADMIN_CANNOT_MODIFY_OWN_STATUS("A060104", "不能修改自己的状态"),
    ADMIN_CANNOT_DELETE_SELF("A060105", "不能删除自己"),

    SECURITY_ERROR("A070001", "安全模块错误"),
    SECURITY_NOT_AUTHENTICATED("A070100", "未登录或Token已过期"),
    SECURITY_ACCESS_DENIED("A070101", "权限不足，无法访问该资源"),

    // ---------- A08: 图片模块错误 ----------
    IMAGE_ERROR("A080001", "图片模块错误"),
    IMAGE_UPLOAD_FAILED("A080100", "图片上传失败"),
    IMAGE_FILE_TOO_LARGE("A080101", "图片文件大小超出限制"),
    IMAGE_TYPE_NOT_ALLOWED("A080102", "不支持的图片类型"),
    IMAGE_FILE_INVALID("A080103", "图片文件内容无效"),
    IMAGE_NOT_FOUND("A080104", "图片不存在"),
    IMAGE_DELETE_FAILED("A080105", "图片删除失败"),
    IMAGE_URL_INVALID("A080200", "图片URL格式无效"),
    IMAGE_URL_UNREACHABLE("A080201", "图片URL不可访问"),
    IMAGE_URL_NOT_IMAGE("A080202", "URL指向的不是图片文件"),
    IMAGE_URL_TOO_LARGE("A080203", "远程图片文件大小超出限制"),
    IMAGE_URL_FETCH_FAILED("A080204", "远程图片拉取失败"),
    IMAGE_SSRF_BLOCKED("A080205", "不允许访问内网地址"),
    IMAGE_UPLOAD_RATE_LIMITED("A080300", "上传频率过高，请稍后再试"),

    // ==================== B: 服务端错误 ====================
    SERVICE_ERROR("B000001", "系统执行出错"),
    SERVICE_TIMEOUT_ERROR("B000100", "系统执行超时"),

    REMOTE_ERROR("C000001", "调用第三方服务出错");

    private final String code;

    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
