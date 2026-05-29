import http from './http'

export interface PriceRecord {
  id: string
  sku_id: string
  price_date: string
  price: string
  currency: string
  unit: string
  review_status: string
  trust_level: string
}

export interface FxRate {
  id: string
  base_currency: string
  quote_currency: string
  rate: string
  rate_date: string
  review_status: string
}

export async function listPrices(params?: { page_no?: number; page_size?: number; sku_id?: string }) {
  const res = await http.get('/pricing/prices', { params })
  return res.data
}

export async function createPrice(body: {
  sku_id: string
  price_date: string
  price: string
  currency: string
  unit: string
  source_type: string
  source_name?: string
  trust_level: string
}) {
  const res = await http.post('/pricing/prices', body)
  return res.data
}

export async function submitPrice(id: string) {
  const res = await http.post(`/pricing/prices/${id}/submit`)
  return res.data
}

export async function approvePrice(id: string) {
  const res = await http.post(`/pricing/prices/${id}/approve`)
  return res.data
}

export async function listFxRates(params?: { page_no?: number; page_size?: number }) {
  const res = await http.get('/pricing/fx-rates', { params })
  return res.data
}

export async function listSkus() {
  const res = await http.get('/pricing/skus')
  return res.data
}
