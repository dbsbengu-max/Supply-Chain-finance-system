import http from './http'

export interface FileUploadResult {
  file_id: string
  file_name?: string
  mime_type?: string
  file_size?: number
  checksum?: string
  storage_key?: string
  business_type?: string
  business_id?: string
}

export interface FileMetadata {
  id: string
  file_name: string
  file_ext?: string
  mime_type?: string
  file_size: number
  checksum?: string
  storage_bucket?: string
  storage_key?: string
  operator_id: string
  project_id: string
  uploaded_by: string
  uploaded_at: string
  status: string
  business_type?: string
  business_id?: string
}

export async function uploadFile(
  file: File,
  options?: { business_type?: string; business_id?: string }
): Promise<FileUploadResult> {
  const form = new FormData()
  form.append('file', file)
  if (options?.business_type) form.append('business_type', options.business_type)
  if (options?.business_id) form.append('business_id', options.business_id)
  const res = await http.post('/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data.data
}

export async function getFileMetadata(id: string): Promise<FileMetadata> {
  const res = await http.get(`/files/${id}`)
  return res.data.data
}
