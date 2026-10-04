package com.example.tokenservice.user;

import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 定義於 {@code resources/mapper/UserMapper.xml}。
 */
@Mapper
public interface UserMapper {

    /**
     * 依帳號名稱（需已轉小寫）查詢使用者與其角色；查無時回傳 {@code null}。
     */
    UserAccount findByUsername(String username);
}
