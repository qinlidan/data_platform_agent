package com.dataplatform.knowledge.dialect;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * 自定义 PostgreSQL 方言以支持 pgvector
 *
 * Hibernate 6+ 已经通过 UserType / AttributeConverter 自动处理自定义类型，
 * 因此只需继承标准方言并通过 VectorConverter 处理向量类型转换即可。
 */
public class PostgreSQLVectorDialect extends PostgreSQLDialect {
    // 无需额外配置，VectorConverter 已处理 float[] <-> PGvector 的转换
}
