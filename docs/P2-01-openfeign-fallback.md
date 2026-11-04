# P2-01 从 0 到 1 配置 OpenFeign 降级

> 目标：被调用服务不可用、超时或异常时，调用方拿到可控的业务错误，而不是 500。
> 前置：`mall-api` 已定义 Feign 接口与 DTO，`mall-order` 通过 Feign 调用 `mall-product`、`mall-stock`。

---

## 一、先分清四个概念

| 概念 | 作用 | 本项目位置 |
| --- | --- | --- |
| 超时 | 防止慢依赖占满线程 | `connect-timeout` / `read-timeout` |
| 重试 | 失败后重新发起调用 | 已关闭 LoadBalancer 重试 |
| 降级 | 调用失败时返回兜底结果 | `fallbackFactory` |
| 熔断 | 失败率超阈值后快速失败，不再打下游 | Resilience4j CircuitBreaker |

降级是「失败后的出口」，熔断是「失败后的开关」。只配 `fallbackFactory` 不打开 CircuitBreaker，降级不会生效。

---

## 二、调用链视角

```
order-service
   └─ Feign 代理
        └─ CircuitBreaker + TimeLimiter
             ├─ 正常：负载均衡选实例 → mall-product / mall-stock
             └─ 异常：FallbackFactory 返回兜底 Result(503)
```

---

## 三、配置步骤

### 3.1 第 1 步：引入 CircuitBreaker 实现

`mall-order/pom.xml`：

```xml
<!-- Feign fallbackFactory 依赖 CircuitBreaker 实现 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

版本由 Spring Cloud BOM 统一管理，不需要写版本号。

常见坑：只加 `fallbackFactory` 不引这个依赖，启动不报错，但降级永远不触发。

### 3.2 第 2 步：打开开关并配置超时

`mall-order/src/main/resources/application.yml`：

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 5000
```

- `circuitbreaker.enabled` 是降级生效的总开关
- 没有超时配置时，Feign 默认连接 10s、读取 60s，线程会被长时间占用

### 3.3 第 3 步：对齐 TimeLimiter

开启熔断后，请求外层会再套一层 Resilience4j 的 `TimeLimiter`，默认只有 **1 秒**，会先于 5 秒的 `readTimeout` 打断调用。

`mall-order/src/main/java/com/mall/order/config/FeignCircuitBreakerConfig.java`：

```java
@Configuration
public class FeignCircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultTimeLimiterCustomizer() {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(6))
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }
}
```

原则：`TimeLimiter` 必须略大于 Feign 的 `readTimeout`，让超时策略以 Feign 配置为准。

### 3.4 第 4 步：定义降级返回码

`mall-common/src/main/java/com/mall/common/enums/ErrorCode.java`：

```java
INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),
SERVICE_UNAVAILABLE(503, "依赖服务暂不可用，已触发降级"),
```

### 3.5 第 5 步：编写 FallbackFactory

`mall-api/src/main/java/com/mall/api/product/fallback/ProductApiFallbackFactory.java`：

```java
@Slf4j
@Component
public class ProductApiFallbackFactory implements FallbackFactory<ProductApi> {

    @Override
    public ProductApi create(Throwable cause) {
        log.error("[fallback] mall-product 调用失败，返回降级结果", cause);
        return new ProductApi() {
            @Override
            public Result<ProductDTO> getById(Long id) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<ProductDTO>> list(ProductQuery query) {
                return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
```

关键点：

- `fallbackFactory` 能拿到 `Throwable`，比普通 `fallback` 更适合记录根因
- 每个接口方法都要实现，返回统一的兜底 `Result`
- **写操作不能返回成功**：库存预占如果兜底返回 `ok`，会造成超卖。查询类接口才适合返回缓存或默认值

`mall-api/src/main/java/com/mall/api/stock/fallback/StockApiFallbackFactory.java` 同理，`lock` 与 `unlock` 都返回 `Result.fail(SERVICE_UNAVAILABLE)`。

### 3.6 第 6 步：挂到 Feign 接口

```java
@FeignClient(name = "mall-product", path = "/product",
        fallbackFactory = ProductApiFallbackFactory.class)
public interface ProductApi {
    // ...
}
```

注意 Spring 扫描范围：降级类在 `com.mall.api` 包下，调用方启动类必须能扫描到，否则启动时会找不到这个 Bean。本项目 `OrderApplication` 使用 `scanBasePackages = "com.mall"`。

### 3.7 第 7 步：调用方识别降级结果

`mall-order/src/main/java/com/mall/order/service/impl/OrderServiceImpl.java`：

```java
private ProductDTO queryProduct(Long productId) {
    Result<ProductDTO> result = productApi.getById(productId);
    if (result == null || result.getCode() == ErrorCode.SERVICE_UNAVAILABLE.getCode()) {
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
    }
    if (result.getCode() != ErrorCode.SUCCESS.getCode() || result.getData() == null) {
        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
    }
    // ...
}
```

降级结果要转成业务异常，再由全局异常处理器统一返回，避免把「服务不可用」误判成「商品不存在」。

### 3.8 第 8 步：打开 Feign 日志（推荐）

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            logger-level: basic

logging:
  level:
    com.mall.api: debug
```

Feign 的 Logger 只有 DEBUG 级别才会输出，两个配置必须配套。`basic` 输出方法、URL、状态码与耗时，不含 body，开销可控。

---

## 四、验证

| 步骤 | 操作 | 预期 |
| --- | --- | --- |
| 1 | 正常启动全部服务 | 下单成功，订单落库 |
| 2 | 停掉 `mall-product`，再调用 `POST /order/create` | 返回 `{"code":503,"message":"依赖服务暂不可用，已触发降级"}` |
| 3 | 查看 order 日志 | 出现 `[fallback] mall-product 调用失败` 与具体异常堆栈 |
| 4 | 重启 `mall-product` | 下单恢复正常 |

验证命令：

```bash
curl -s -X POST http://127.0.0.1:8084/order/create \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":1}]}'
```

预期响应体：

```json
{"code":503,"message":"依赖服务暂不可用，已触发降级","data":null}
```

日志预期：

```text
DEBUG com.mall.api.product.ProductApi : [ProductApi#getById] ---> GET http://mall-product/product/1
DEBUG com.mall.api.product.ProductApi : [ProductApi#getById] <--- HTTP/1.1 503 (85ms)
ERROR c.m.a.p.f.ProductApiFallbackFactory : [fallback] mall-product 调用失败，返回降级结果
WARN  c.m.c.exception.GlobalExceptionHandler : 业务异常: code=503, message=依赖服务暂不可用，已触发降级
```

---

## 五、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 写了 `fallbackFactory` 却不生效 | 没引 CircuitBreaker 实现或没开 `circuitbreaker.enabled` | 同时补依赖与开关 |
| 5 秒超时没生效，1 秒就失败 | TimeLimiter 默认 1s 抢先打断 | TimeLimiter 调到略大于 readTimeout |
| 降级后订单继续走到扣库存 | 兜底返回了成功结果 | 写操作一律返回失败码 |
| 降级 Bean 启动时找不到 | 降级类不在调用方扫描范围 | 调整 `scanBasePackages` 或把降级类放到调用方包下 |
| 看不到 Feign 日志 | 只配了 `logger-level`，没开 DEBUG | 同时配置 `logging.level.com.mall.api: debug` |
| 网络超时后下游被重复调用 | LoadBalancer 默认换实例重试 | 关闭 LB 重试，或给业务加幂等键 |

---

## 六、面试讲法

1. Feign 降级依赖 CircuitBreaker，`fallbackFactory` 只是兜底出口，不是熔断本身。
2. 超时要讲两层：Feign 的 `readTimeout` 和 Resilience4j 的 `TimeLimiter`，二者要排序好。
3. 降级要有业务语义：查询可返回缓存，写操作必须失败，否则会产生脏数据。
4. 重试与降级是两回事：关掉 LoadBalancer 重试是为了不重复执行非幂等写操作，生产环境更稳的做法是保留重试并加幂等键。
5. 小流量下熔断器可能一直处于 CLOSED（默认最少 100 次调用才统计），但 fallback 依然会在每次调用失败时生效。
