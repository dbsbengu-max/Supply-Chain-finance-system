import http from './http'

export interface TradeOrder {
  id: string
  order_no: string
  order_type: string
  buyer_id: string
  seller_id: string
  trade_company_id?: string
  total_amount: string
  currency: string
  order_status: string
  created_at: string
}

export async function listOrders(params?: { page_no?: number; page_size?: number; order_status?: string }) {
  const res = await http.get('/trade/orders', { params })
  return res.data
}

export async function getOrder(id: string) {
  const res = await http.get(`/trade/orders/${id}`)
  return res.data
}

export async function createOrder(body: {
  order_type: string
  buyer_id: string
  seller_id: string
  trade_company_id?: string
  currency: string
  country_from?: string
  country_to?: string
  items: Array<{
    sku_id: string
    quantity: string
    unit: string
    unit_price: string
    delivery_date?: string
  }>
}) {
  const res = await http.post('/trade/orders', body)
  return res.data
}

export async function updateOrder(
  id: string,
  body: Parameters<typeof createOrder>[0]
) {
  const res = await http.put(`/trade/orders/${id}`, body)
  return res.data
}

export async function submitOrder(id: string) {
  const res = await http.post(`/trade/orders/${id}/submit`)
  return res.data
}

export async function confirmOrder(id: string) {
  const res = await http.post(`/trade/orders/${id}/confirm`)
  return res.data
}

export async function cancelOrder(id: string) {
  const res = await http.post(`/trade/orders/${id}/cancel`)
  return res.data
}

export async function validateBackground(id: string) {
  const res = await http.post(`/trade/orders/${id}/validate-background`)
  return res.data
}
