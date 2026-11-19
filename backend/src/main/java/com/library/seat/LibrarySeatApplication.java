package com.library.seat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 自习室座位预约管理系统启动类
 *
 * @author graduation-project
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@MapperScan("com.library.seat.mapper")
public class LibrarySeatApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibrarySeatApplication.class, args);
        System.out.println();
        System.out.println("  自习室座位预约管理系统已启动");
        System.out.println("  请用浏览器访问：http://localhost:8080");
        System.out.println("  管理员 admin / 123456    学生 20230001 / 123456");
        System.out.println();
    }
}