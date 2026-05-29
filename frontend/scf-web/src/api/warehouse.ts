import http from './http'

export interface DictItem {
  code: string
  label: string
}

export interface WarehouseMeta {
  right_statuses: DictItem[]
}

export interface Warehouse {
  id: string
  operator_id: string
  project_id: string
  warehouse_company_id: string
  warehouse_code: string
  warehouse_name: string
  country_region: string
  address: string
  warehouse_type: string
  status: string
}

export interface Inventory {
  id: string
  warehouse_id: string
  operator_id: string
  project_id: string
  sku_id: string
  batch_no: string
  owner_id: string
  location_code?: string
  quantity: string
  available_quantity: string
  frozen_quantity: string
  pledged_quantity: string
  outbound_pending_quantity: string
  valuation_amount?: string
  currency?: string
  right_status: string
  right_status_label: string
  stocktake_exception: boolean
  version_no: number
  created_by: string
  created_at: string
  updated_at?: string
}

export interface PageResult<T> {
  page_no: number
  page_size: number
  total: number
  records: T[]
}

export interface ApiResult<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export async function getWarehouseMeta() {
  const res = await http.get('/warehouse/meta')
  return res.data as ApiResult<WarehouseMeta>
}

export async function listWarehouses(params?: { page_no?: number; page_size?: number; status?: string }) {
  const res = await http.get('/warehouse/warehouses', { params })
  return res.data as ApiResult<PageResult<Warehouse>>
}

export async function getWarehouse(id: string) {
  const res = await http.get(`/warehouse/warehouses/${id}`)
  return res.data as ApiResult<Warehouse>
}

export async function listInventories(params?: {
  page_no?: number
  page_size?: number
  warehouse_id?: string
  right_status?: string
}) {
  const res = await http.get('/warehouse/inventories', { params })
  return res.data as ApiResult<PageResult<Inventory>>
}

export async function getInventory(id: string) {
  const res = await http.get(`/warehouse/inventories/${id}`)
  return res.data as ApiResult<Inventory>
}

export async function createInbound(body: {
  warehouse_id: string
  sku_id: string
  batch_no: string
  owner_id: string
  location_code?: string
  quantity: number
  valuation_amount?: number
  currency?: string
}) {
  const res = await http.post('/warehouse/inbounds', body)
  return res.data as ApiResult<{ id: string; inbound_no: string; inventory_id: string }>
}

export async function freezeInventory(id: string, body: { quantity: number; remark?: string }) {
  const res = await http.post(`/warehouse/inventories/${id}/freeze`, body)
  return res.data as ApiResult<Inventory>
}

export async function pledgeInventory(id: string, body: { quantity: number; remark?: string }) {
  const res = await http.post(`/warehouse/inventories/${id}/pledge`, body)
  return res.data as ApiResult<Inventory>
}

export async function applyRelease(id: string, body: { quantity: number; remark?: string }) {
  const res = await http.post(`/warehouse/inventories/${id}/release`, body)
  return res.data as ApiResult<{ id: string; request_no: string }>
}

export async function approveRelease(requestId: string) {
  const res = await http.post(`/warehouse/release-requests/${requestId}/approve`)
  return res.data as ApiResult<Inventory>
}

export async function applyOutbound(body: { inventory_id: string; quantity: number; remark?: string }) {
  const res = await http.post('/warehouse/outbounds', body)
  return res.data as ApiResult<{ id: string; request_no: string }>
}

export async function confirmOutbound(requestId: string) {
  const res = await http.post(`/warehouse/outbounds/${requestId}/confirm`)
  return res.data as ApiResult<Inventory>
}
