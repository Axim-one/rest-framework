# Setup & Configuration Reference

## Application Setup

All annotations below are required. Add `@XRestServiceScan` if using `@XRestService` REST clients.

```java
@ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "com.myapp"})
@SpringBootApplication
@XRepositoryScan("com.myapp.repository")
@MapperScan({"one.axim.framework.mybatis.mapper", "com.myapp.mapper"})
@XRestServiceScan("com.myapp.client")  // Only if using @XRestService REST clients
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

| Annotation | Required | Purpose |
|---|---|---|
| `@ComponentScan` | **Yes** | Must include `one.axim.framework.rest`, `one.axim.framework.mybatis`, and app packages |
| `@XRepositoryScan` | **Yes** | Scans for `@XRepository` interfaces |
| `@MapperScan` | **Yes** | Must include `one.axim.framework.mybatis.mapper` + app mapper packages |
| `@XRestServiceScan` | If using REST client | Scans for `@XRestService` interfaces |

## application.properties — Complete Reference

```properties
# -- DataSource --
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
mybatis.config-location=classpath:mybatis-config.xml

# -- Framework: HTTP Client --
axim.rest.client.pool-size=200                    # Max connection pool (default: 200)
axim.rest.client.connection-request-timeout=30    # seconds (default: 30)
axim.rest.client.response-timeout=30              # seconds (default: 30)
axim.rest.debug=false                             # REST client logging (default: false)

# -- Framework: Gateway --
axim.rest.gateway.host=http://api-gateway:8080    # Enables gateway mode for @XRestService

# -- Framework: XWebClient Beans --
axim.web-client.services.userClient=http://user-service:8080    # Named XWebClient bean
axim.web-client.services.orderClient=http://order-service:8080  # Named XWebClient bean

# -- Framework: Session / Token --
axim.rest.session.secret-key=your-hmac-secret-key # HMAC-SHA256 signing (omit = unsigned)
axim.rest.session.token-expire-days=90            # Token lifetime (default: 90)

# -- Framework: i18n --
axim.rest.message.default-language=ko-KR          # Default locale (default: ko-KR)
axim.rest.message.language-header=Accept-Language  # Language header (default: Accept-Language)
spring.messages.basename=messages                  # App message files (default: messages)
spring.messages.encoding=UTF-8
```

## mybatis-config.xml

All three elements (objectFactory, plugins, mappers) are **required**.

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <settings>
        <setting name="cacheEnabled" value="true"/>
        <setting name="useGeneratedKeys" value="true"/>
        <setting name="defaultExecutorType" value="SIMPLE"/>
        <setting name="defaultStatementTimeout" value="10"/>
        <setting name="callSettersOnNulls" value="true"/>
        <setting name="mapUnderscoreToCamelCase" value="true"/>
    </settings>
    <!-- REQUIRED: Entity instantiation -->
    <objectFactory type="one.axim.framework.mybatis.plugin.XObjectFactory"/>
    <!-- REQUIRED: Pagination + result mapping -->
    <plugins>
        <plugin interceptor="one.axim.framework.mybatis.plugin.XResultInterceptor"/>
    </plugins>
    <!-- REQUIRED: Framework internal CRUD mapper -->
    <mappers>
        <mapper class="one.axim.framework.mybatis.mapper.CommonMapper"/>
    </mappers>
</configuration>
```

## Maven Installation

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>core</artifactId>
        <version>1.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>rest-api</artifactId>
        <version>1.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>mybatis</artifactId>
        <version>1.3.0</version>
    </dependency>
</dependencies>
```

## i18n Message Source

Hierarchical message resolution: Application messages override framework defaults.

```
messages.properties (your app)  ->  overrides  ->  framework-messages.properties (built-in)
```

Built-in framework messages:
```properties
server.http.error.invalid-parameter=Invalid request parameter.
server.http.error.required-auth=Authentication required.
server.http.error.invalid-auth=Invalid authentication credentials.
server.http.error.expire-auth=Authentication expired.
server.http.error.notfound-api=API not found.
server.http.error.server-error=Internal server error.
```

If key not found in any source, the key string itself is returned (no exception).

## Argument Resolvers

Two resolvers are auto-registered via `XWebMvcConfiguration`:

### XPaginationResolver

Resolves `XPagination` from query parameters via `@XPaginationDefault`. See `references/query-and-pagination.md` for full details.

### XSessionResolver

Resolves any `SessionData` subclass from `Access-Token` HTTP header. No annotation required — auto-detected by parameter type. See `references/rest-client-and-auth.md` for full details.
