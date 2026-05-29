import http from './http'

export interface Project {
  id: string
  project_code: string
  project_name: string
  countries: string
  currencies: string
  status: string
}

export async function listProjects() {
  const res = await http.get('/projects')
  return res.data
}

export async function createProject(body: {
  project_code: string
  project_name: string
  countries: string
  currencies: string
}) {
  const res = await http.post('/projects', body)
  return res.data
}

export async function updateProject(id: string, body: Partial<{
  project_name: string
  countries: string
  currencies: string
  status: string
}>) {
  const res = await http.put(`/projects/${id}`, body)
  return res.data
}
