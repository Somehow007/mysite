export enum ErrorCode {
  SUCCESS = '0',
  CLIENT_ERROR = 'A000001',
  USER_REGISTER_ERROR = 'A000100',
  USER_NAME_VERIFY_ERROR = 'A000110',
  USER_NAME_EXIST_ERROR = 'A000111',
  USER_NAME_SENSITIVE_ERROR = 'A000112',
  USER_NAME_SPECIAL_CHARACTER_ERROR = 'A000113',
  PASSWORD_VERIFY_ERROR = 'A000120',
  PASSWORD_SHORT_ERROR = 'A000121',
  PHONE_VERIFY_ERROR = 'A000151',
  IDEMPOTENT_TOKEN_NULL_ERROR = 'A000200',
  IDEMPOTENT_TOKEN_DELETE_ERROR = 'A000201',
  SERVICE_ERROR = 'B000001',
  SERVICE_TIMEOUT_ERROR = 'B000100',
  REMOTE_ERROR = 'C000001',
  NETWORK_ERROR = 'NETWORK_ERROR',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  NOT_FOUND = 'NOT_FOUND',
  SERVER_ERROR = 'SERVER_ERROR',
  TIMEOUT = 'TIMEOUT',
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
}

export const ErrorMessage: Record<ErrorCode, string> = {
  [ErrorCode.SUCCESS]: '操作成功',
  [ErrorCode.CLIENT_ERROR]: '客户端请求错误',
  [ErrorCode.USER_REGISTER_ERROR]: '用户注册错误',
  [ErrorCode.USER_NAME_VERIFY_ERROR]: '用户名校验失败',
  [ErrorCode.USER_NAME_EXIST_ERROR]: '用户名已存在',
  [ErrorCode.USER_NAME_SENSITIVE_ERROR]: '用户名包含敏感词',
  [ErrorCode.USER_NAME_SPECIAL_CHARACTER_ERROR]: '用户名包含特殊字符',
  [ErrorCode.PASSWORD_VERIFY_ERROR]: '密码校验失败',
  [ErrorCode.PASSWORD_SHORT_ERROR]: '密码长度不够',
  [ErrorCode.PHONE_VERIFY_ERROR]: '手机格式校验失败',
  [ErrorCode.IDEMPOTENT_TOKEN_NULL_ERROR]: '请求Token为空',
  [ErrorCode.IDEMPOTENT_TOKEN_DELETE_ERROR]: '请求Token已失效',
  [ErrorCode.SERVICE_ERROR]: '系统执行出错',
  [ErrorCode.SERVICE_TIMEOUT_ERROR]: '系统执行超时',
  [ErrorCode.REMOTE_ERROR]: '调用第三方服务出错',
  [ErrorCode.NETWORK_ERROR]: '网络连接失败，请检查网络',
  [ErrorCode.UNAUTHORIZED]: '未授权，请先登录',
  [ErrorCode.FORBIDDEN]: '没有权限访问',
  [ErrorCode.NOT_FOUND]: '请求的资源不存在',
  [ErrorCode.SERVER_ERROR]: '服务器内部错误',
  [ErrorCode.TIMEOUT]: '请求超时',
  [ErrorCode.UNKNOWN_ERROR]: '未知错误',
}

export class ApiError extends Error {
  code: string
  message: string
  requestId?: string
  data?: unknown

  constructor(code: string, message: string, requestId?: string, data?: unknown) {
    super(message)
    this.code = code
    this.message = message
    this.requestId = requestId
    this.data = data
    this.name = 'ApiError'
  }

  static fromResponse(response: ApiResponse): ApiError {
    const message = response.message || getErrorMessage(response.code)
    return new ApiError(response.code, message, response.requestId, response.data)
  }

  static networkError(): ApiError {
    return new ApiError(ErrorCode.NETWORK_ERROR, ErrorMessage.NETWORK_ERROR)
  }

  static timeout(): ApiError {
    return new ApiError(ErrorCode.TIMEOUT, ErrorMessage.TIMEOUT)
  }

  static unauthorized(): ApiError {
    return new ApiError(ErrorCode.UNAUTHORIZED, ErrorMessage.UNAUTHORIZED)
  }

  static serverError(): ApiError {
    return new ApiError(ErrorCode.SERVER_ERROR, ErrorMessage.SERVER_ERROR)
  }

  static unknownError(message?: string): ApiError {
    return new ApiError(ErrorCode.UNKNOWN_ERROR, message || ErrorMessage.UNKNOWN_ERROR)
  }

  isNetworkError(): boolean {
    return this.code === ErrorCode.NETWORK_ERROR
  }

  isTimeout(): boolean {
    return this.code === ErrorCode.TIMEOUT
  }

  isUnauthorized(): boolean {
    return this.code === ErrorCode.UNAUTHORIZED
  }

  isClientError(): boolean {
    return this.code.startsWith('A')
  }

  isServerError(): boolean {
    return this.code.startsWith('B') || this.code === ErrorCode.SERVER_ERROR
  }
}

export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
  requestId?: string
}

export function isSuccessResponse(response: ApiResponse): boolean {
  return response.code === ErrorCode.SUCCESS
}

export function getErrorMessage(code: string): string {
  return ErrorMessage[code as ErrorCode] || ErrorMessage.UNKNOWN_ERROR
}
