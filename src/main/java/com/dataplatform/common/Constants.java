package com.dataplatform.common;

/**
 * 常量类
 */
public class Constants {

    /**
     * JWT Token 请求头
     */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * JWT Token 前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 默认页码
     */
    public static final Long DEFAULT_PAGE = 1L;

    /**
     * 默认每页大小
     */
    public static final Long DEFAULT_SIZE = 10L;

    /**
     * 最大每页大小
     */
    public static final Long MAX_SIZE = 100L;

    /**
     * 状态：启用
     */
    public static final Integer STATUS_ENABLE = 1;

    /**
     * 状态：禁用
     */
    public static final Integer STATUS_DISABLE = 0;

    /**
     * 是否标识：是
     */
    public static final Integer YES = 1;

    /**
     * 是否标识：否
     */
    public static final Integer NO = 0;

    /**
     * 初始版本号
     */
    public static final Integer INITIAL_VERSION = 1;
}
