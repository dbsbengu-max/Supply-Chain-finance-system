import http from './http'

export interface ExcelImportRow {
  row_no: number
  row_status: 'OK' | 'ERROR' | 'WARNING'
  row_data: string
  error_message?: string
  warning_message?: string
}

export interface ExcelImportJob {
  id: string
  file_id: string
  import_type: string
  batch_id: string
  dry_run: boolean
  status: string
  total_rows: number
  ok_rows: number
  error_rows: number
  warning_rows: number
  rows: ExcelImportRow[]
}

export async function createExcelImportJob(body: {
  file_id: string
  import_type: string
  dry_run?: boolean
}) {
  const res = await http.post('/imports/excel/jobs', body)
  return res.data.data as ExcelImportJob
}

export async function getExcelImportJob(id: string) {
  const res = await http.get(`/imports/excel/jobs/${id}`)
  return res.data.data as ExcelImportJob
}

export async function confirmExcelImportJob(
  id: string,
  body: { batch_id: string; ignore_warning?: boolean }
) {
  const res = await http.post(`/imports/excel/jobs/${id}/confirm`, body)
  return res.data.data
}
