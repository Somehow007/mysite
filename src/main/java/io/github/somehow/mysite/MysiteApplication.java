package io.github.somehow.mysite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(value = "io.github.somehow.mysite.dao.mapper",
            sqlSessionFactoryRef = "sqlSessionFactory")
public class MysiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(MysiteApplication.class, args);
    }

}
