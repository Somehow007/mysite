import { uploadImage, MAX_IMAGE_FILE_SIZE } from '@/api/image'
import { useToast } from '@/composables/useToast'

const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/svg+xml']

/**
 * Crepe 图片上传适配器。
 *
 * Crepe 的 image-block feature 对工具栏插入、粘贴、拖拽三种入口统一回调
 * uploader（签名 `(file: File) => Promise<string /* url *\/>`），
 * 这里对接站点现有的 `uploadImage` API，并沿用旧编辑器的校验与错误文案规则。
 */
export function createImageUploader(): (file: File) => Promise<string> {
  const toast = useToast()

  return async (file: File): Promise<string> => {
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      toast.error(`"${file.name || '文件'}" 不是支持的图片格式（JPEG / PNG / GIF / WebP / SVG）`)
      throw new Error('unsupported image type')
    }
    if (file.size > MAX_IMAGE_FILE_SIZE) {
      const maxSizeMB = MAX_IMAGE_FILE_SIZE / (1024 * 1024)
      toast.error(`图片 "${file.name || ''}" 超过大小限制（最大 ${maxSizeMB}MB）`)
      throw new Error('image too large')
    }

    try {
      const result = await uploadImage(file)
      return result.url
    } catch (e) {
      const errorMessage = e instanceof Error ? e.message : '未知错误'
      if (errorMessage.includes('413') || errorMessage.includes('过大') || errorMessage.toLowerCase().includes('too large')) {
        toast.error('图片文件过大，请压缩后重试（最大 5MB）')
      } else {
        toast.error('图片上传失败：' + errorMessage)
      }
      throw e
    }
  }
}
