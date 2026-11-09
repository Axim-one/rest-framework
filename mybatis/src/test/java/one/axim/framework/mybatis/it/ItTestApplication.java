package one.axim.framework.mybatis.it;

import one.axim.framework.mybatis.annotation.XRepositoryScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** 소비자 앱과 같은 필수 설정(README 「Setup」)으로 띄우는 통합 테스트 앱. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("one.axim.framework.mybatis")
@XRepositoryScan("one.axim.framework.mybatis.it.repo")
@MapperScan({"one.axim.framework.mybatis.mapper", "one.axim.framework.mybatis.it.mapper"})
public class ItTestApplication {
}
