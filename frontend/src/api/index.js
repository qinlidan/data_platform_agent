import request from '@/utils/request'

/**
 * 作业管理 API
 */
export const jobApi = {
  // 分页查询作业列表
  getJobList(params) {
    return request({
      url: '/jobs',
      method: 'get',
      params
    })
  },

  // 创建作业
  createJob(data) {
    return request({
      url: '/jobs',
      method: 'post',
      data
    })
  },

  // 获取作业详情
  getJobDetail(id) {
    return request({
      url: `/jobs/${id}`,
      method: 'get'
    })
  },

  // 更新作业
  updateJob(id, data) {
    return request({
      url: `/jobs/${id}`,
      method: 'put',
      data
    })
  },

  // 删除作业
  deleteJob(id) {
    return request({
      url: `/jobs/${id}`,
      method: 'delete'
    })
  },

  // 发布作业
  publishJob(id, version) {
    return request({
      url: `/jobs/${id}/publish`,
      method: 'post',
      params: { version }
    })
  },

  // 归档作业
  archiveJob(id) {
    return request({
      url: `/jobs/${id}/archive`,
      method: 'post'
    })
  }
}

/**
 * 作业版本 API
 */
export const jobVersionApi = {
  // 1. 获取作业版本列表 (对应后端 @GetMapping("/job/{jobId}"))
  getVersionList(jobId) {
    return request({
      url: `/job-versions/job/${jobId}`,
      method: 'get'
    })
  },

  // 2. 配置 SQL (对应后端 @PostMapping("/{jobId}/configure-sql"))
  configureSql(jobId, data) {
    return request({
      url: `/job-versions/${jobId}/configure-sql`,
      method: 'post',
      data
    })
  },

  // 3. 查询版本详情 (对应后端 @GetMapping("/{id}"))
  getVersionDetail(id) {
    return request({
      url: `/job-versions/${id}`,
      method: 'get'
    })
  },

  // 4. 发布版本 (对应后端 @PostMapping("/{id}/publish"))
  publishVersion(versionId) {
    return request({
      url: `/job-versions/${versionId}/publish`,
      method: 'post'
    })
  },

  // 5. 删除版本 (对应后端 @DeleteMapping("/{id}"))
  deleteVersion(versionId) {
    return request({
      url: `/job-versions/${versionId}`,
      method: 'delete'
    })
  }
}
/**
 * 字段元数据 API
 */
export const fieldMetadataApi = {
  // 1. 查询版本的字段元数据 (对应后端 @GetMapping("/version/{jobVersionId}"))
  getFieldList(versionId) {
    return request({
      url: `/field-metadata/version/${versionId}`,
      method: 'get'
    })
  },

  // 2. 批量保存字段元数据 (对应后端 @PostMapping("/batch"))
  // 注意：后端接收的 Body 是 { jobVersionId: xxx, fields: [...] }
  saveFields(versionId, fields) {
    return request({
      url: '/field-metadata/batch',
      method: 'post',
      data: {
        jobVersionId: versionId,
        fields: fields
      }
    })
  },

  // 3. 从 SQL 解析字段 (对应后端 @PostMapping("/parse"))
  // 注意：后端期望请求体是一个原始字符串，而不是 JSON 对象
  parseFieldsFromSql(sql) {
    return request({
      url: '/field-metadata/parse',
      method: 'post',
      data: sql,
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }
}

/**
 * 数据源 API
 */
export const datasourceApi = {
  // 分页查询数据源列表 (对应后端 GET /api/datasources)
  getDatasourcePage(params) {
    return request({
      url: '/datasources',
      method: 'get',
      params // { pageNum, pageSize, keyword }
    })
  },

  // 查询所有数据源 (对应后端 GET /api/datasources/all)
  getAllDatasources() {
    return request({
      url: '/datasources/all',
      method: 'get'
    })
  },

  // 获取数据源详情 (对应后端 GET /api/datasources/{id})
  getDatasourceById(id) {
    return request({
      url: `/datasources/${id}`,
      method: 'get'
    })
  },

  // 创建数据源 (对应后端 POST /api/datasources)
  createDatasource(data) {
    return request({
      url: '/datasources',
      method: 'post',
      data
    })
  },

  // 更新数据源 (对应后端 PUT /api/datasources/{id})
  updateDatasource(id, data) {
    return request({
      url: `/datasources/${id}`,
      method: 'put',
      data
    })
  },

  // 删除数据源 (对应后端 DELETE /api/datasources/{id})
  deleteDatasource(id) {
    return request({
      url: `/datasources/${id}`,
      method: 'delete'
    })
  },

  // 测试数据源连接（已保存，按 id）
  testConnection(id) {
    return request({
      url: `/datasources/${id}/test`,
      method: 'post'
    })
  },

  // 测试数据源连接（未保存，按参数）
  testConnectionByParams(data) {
    return request({
      url: '/datasources/test-connection',
      method: 'post',
      data
    })
  }
}

/**
 * 元数据 API
 */
export const metadataApi = {
  // 获取数据源的库列表
  getDatabases(datasourceId) {
    return request({
      url: '/metadata/databases',
      method: 'get',
      params: { datasourceId }
    })
  },

  // 获取指定库的表列表（含字段）
  getTables(datasourceId, database) {
    return request({
      url: '/metadata/tables',
      method: 'get',
      params: { datasourceId, database }
    })
  }
}

/**
 * AI 辅助 API
 */
export const aiApi = {
  // SQL 语法检查
  checkSqlSyntax(sql) {
    return request({
      url: '/ai/check-sql-syntax',
      method: 'post',
      data: { sqlContent: sql }
    })
  },

  // 生成字段元数据
  generateFieldMetadata(sql) {
    return request({
      url: '/ai/generate-field-metadata',
      method: 'post',
      data: { sqlContent: sql }
    })
  },

  // 自然语言对话
  chat(message) {
    return request({
      url: '/nl/chat',
      method: 'post',
      data: { message }
    })
  }
}
