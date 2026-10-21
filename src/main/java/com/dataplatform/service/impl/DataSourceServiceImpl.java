package com.dataplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.dto.DataSourceRequest;
import com.dataplatform.entity.DataSource;
import com.dataplatform.entity.Job;
import com.dataplatform.enums.DataSourceType;
import com.dataplatform.exception.BusinessException;
import com.dataplatform.mapper.DataSourceMapper;
import com.dataplatform.mapper.JobMapper;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * 数据源服务实现类
 */
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final JobMapper jobMapper;
    private final EncryptUtil encryptUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSource create(DataSourceRequest request) {
        // 检查编码是否重复
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getSourceCode, request.getSourceCode());
        if (dataSourceMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("数据源编码已存在");
        }

        // 创建数据源
        DataSource dataSource = new DataSource();
        BeanUtils.copyProperties(request, dataSource);

        // 加密密码
        dataSource.setPassword(encryptUtil.encrypt(request.getPassword()));

        dataSourceMapper.insert(dataSource);
        return dataSource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSource update(Long id, DataSourceRequest request) {
        // 检查数据源是否存在
        DataSource dataSource = getById(id);

        // 检查编码是否重复（排除自己）
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getSourceCode, request.getSourceCode())
               .ne(DataSource::getId, id);
        if (dataSourceMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("数据源编码已存在");
        }

        // 更新数据源
        BeanUtils.copyProperties(request, dataSource);

        // 加密密码
        dataSource.setPassword(encryptUtil.encrypt(request.getPassword()));

        dataSourceMapper.updateById(dataSource);
        return dataSource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查数据源是否存在
        DataSource dataSource = getById(id);

        // 检查是否被作业引用
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getSourceDatasourceId, id);
        long count = jobMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("数据源正在被作业引用，无法删除");
        }

        dataSourceMapper.deleteById(id);
    }

    @Override
    public DataSource getById(Long id) {
        DataSource dataSource = dataSourceMapper.selectById(id);
        if (dataSource == null) {
            throw new BusinessException("数据源不存在");
        }
        return dataSource;
    }

    @Override
    public DataSource getByCode(String sourceCode) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getSourceCode, sourceCode);
        return dataSourceMapper.selectOne(wrapper);
    }

    @Override
    public Page<DataSource> page(int pageNum, int pageSize, String keyword) {
        Page<DataSource> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DataSource::getSourceCode, keyword)
                             .or()
                             .like(DataSource::getSourceName, keyword));
        }

        wrapper.orderByDesc(DataSource::getCreateTime);
        return dataSourceMapper.selectPage(page, wrapper);
    }

    @Override
    public List<DataSource> listAll() {
        // MOCK: 返回 3 条写死的可用数据源供作业创建使用
        DataSource ds1 = new DataSource();
        ds1.setId(1L);
        ds1.setSourceCode("DB2_PROD");
        ds1.setSourceName("DB2生产库");
        ds1.setSourceType(DataSourceType.DB2);
        ds1.setDriver("com.ibm.db2.jcc.DB2Driver");
        ds1.setJdbcUrl("jdbc:db2://192.168.1.11:50000/PRODDB");

        DataSource ds2 = new DataSource();
        ds2.setId(2L);
        ds2.setSourceCode("ORACLE_PROD");
        ds2.setSourceName("Oracle生产库");
        ds2.setSourceType(DataSourceType.ORACLE);
        ds2.setDriver("oracle.jdbc.OracleDriver");
        ds2.setJdbcUrl("jdbc:oracle:thin:@192.168.1.12:1521:ORCL");

        DataSource ds3 = new DataSource();
        ds3.setId(3L);
        ds3.setSourceCode("MYSQL_PROD");
        ds3.setSourceName("MySQL生产库");
        ds3.setSourceType(DataSourceType.MYSQL);
        ds3.setDriver("com.mysql.cj.jdbc.Driver");
        ds3.setJdbcUrl("jdbc:mysql://192.168.1.13:3306/prod");

        return List.of(ds1, ds2, ds3);
    }

    @Override
    public Long getIdByName(String sourceName) {
        // MOCK: 从写死的3个数据源中查找
        if ("DB2生产库".equals(sourceName)) {
            return 1L;
        } else if ("Oracle生产库".equals(sourceName)) {
            return 2L;
        } else if ("MySQL生产库".equals(sourceName)) {
            return 3L;
        }

        // 也尝试从数据库查找
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getSourceName, sourceName);
        DataSource dataSource = dataSourceMapper.selectOne(wrapper);
        if (dataSource != null) {
            return dataSource.getId();
        }

        throw new BusinessException("数据源不存在：" + sourceName);
    }

    @Override
    public boolean testConnection(Long id) {
        DataSource dataSource = getById(id);

        try {
            // 解密密码
            String password = encryptUtil.decrypt(dataSource.getPassword());

            // 测试连接
            try (Connection conn = DriverManager.getConnection(
                    dataSource.getJdbcUrl(),
                    dataSource.getUsername(),
                    password)) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean testConnection(DataSourceRequest request) {
        // HBase 等非 JDBC 数据源没有 JDBC 驱动，这里做配置项完整性校验
        if (request.getSourceType() == DataSourceType.HBASE) {
            return StringUtils.hasText(request.getJdbcUrl());
        }

        try {
            // 若指定了驱动类，先尝试加载
            if (StringUtils.hasText(request.getDriver())) {
                Class.forName(request.getDriver());
            }

            DriverManager.setLoginTimeout(5);
            try (Connection conn = DriverManager.getConnection(
                    request.getJdbcUrl(),
                    request.getUsername(),
                    request.getPassword())) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
