package one.axim.framework.demo;

import one.axim.framework.mybatis.annotation.XRepositoryScan;
import one.axim.framework.rest.annotation.XRestServiceScan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

@ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "one.axim.framework.demo"})
@SpringBootApplication
@XRepositoryScan("one.axim.framework.demo")
@XRestServiceScan("one.axim.framework.demo.restclient")
@MapperScan(value = {"one.axim.framework.mybatis.mapper", "one.axim.framework.demo"})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
