// package com.iflytek.astra.console.toolkit.config.mybatis;
//
// import com.baomidou.mybatisplus.annotation.DbType;
// import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
// import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
// import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
// import com.iflytek.astra.console.toolkit.handler.language.LanguageContext;
// import org.mybatis.spring.annotation.MapperScan;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// import java.util.*;
//
// @MapperScan({
// "com.iflytek.astra.console.toolkit.mapper",
// "com.iflytek.astra.console.commons.mapper"
// })
// @Configuration
// public class MybatisPlusConfig {
//
//
// @Bean(name = "mybatisPlusInterceptor")
// public MybatisPlusInterceptor mybatisPlusInterceptor() {
// MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
// PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
// paginationInnerInterceptor.setDbType(DbType.MYSQL);
// interceptor.addInnerInterceptor(paginationInnerInterceptor);
//
// DynamicTableNameInnerInterceptor dynamicTable = new DynamicTableNameInnerInterceptor();
// dynamicTable.setTableNameHandler((sql, tableName) -> {
// // Configure effective tables
// List<String> tableNames = new ArrayList<>(Arrays.asList("config_info", "prompt_template"));
// if (tableNames.contains(tableName)) {
// String lang = LanguageContext.get();
// // Domain name check if it's "en
// if ("en".equalsIgnoreCase(lang)) {
// return tableName + "_en";
// }
// }
// return tableName;
// });
//
// interceptor.addInnerInterceptor(dynamicTable);
// return interceptor;
// }
// }
