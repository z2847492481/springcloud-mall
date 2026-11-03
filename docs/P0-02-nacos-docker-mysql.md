# P0-02 用 Docker 启动 Nacos（MySQL 存储）

> 已在当前环境确认：
> - MySQL 容器名 `mysql`，映射 `3306`，但只接了默认 `bridge` 网络（默认网络**不能按容器名互相访问**）。
> - Docker 版本 29.1.3，**没有安装 compose 插件**，也没有 `docker-compose`，所以下面全部用 `docker run`。
> - `nacos/nacos-server:v2.3.2` 镜像和官方 `mysql-schema.sql` 地址均已验证可用。
>
> 以下命令都在 **WSL 终端**里执行。

---

## 一、整体思路

```
Windows 上的 Spring Boot ──localhost:8848──▶ Nacos 容器
                                          │
                                          │ 同一自定义网络 mall-net
                                          ▼
                                     MySQL 容器（mysql:3306）
```

要点：

1. 新建自定义网络 `mall-net`，把已有的 MySQL 容器接进去（不用重启 MySQL）。
2. 在 MySQL 里建 `nacos_config` 库并导入官方表结构。
3. Nacos 容器加入 `mall-net`，用容器名 `mysql` 访问数据库。
4. 8848 / 9848 / 9849 三个端口都要映射，Windows 客户端才能注册成功。
5. 容器数据统一放在宿主机 `/home/zhq123/docker-data/<服务名>/` 下，不污染容器内部。

### 数据目录约定

当前 MySQL 的实际挂载已确认：

| 宿主机目录 | 容器内路径 | 用途 |
| --- | --- | --- |
| `/home/zhq123/docker-data/mysql/init` | `/docker-entrypoint-initdb.d` | 初始化 SQL |
| `/home/zhq123/docker-data/mysql/data` | `/var/lib/mysql` | 数据文件 |

Nacos 沿用同样风格：

| 宿主机目录 | 容器内路径 | 用途 |
| --- | --- | --- |
| `/home/zhq123/docker-data/nacos/logs` | `/home/nacos/logs` | Nacos 日志 |
| `/home/zhq123/docker-data/nacos/data` | `/home/nacos/data` | 本地数据目录 |

`docker-data` 目前属主是 root，创建目录需要 `sudo`。

---

## 二、把 MySQL 接入自定义网络

```bash
# 创建网络（已存在会报错，可忽略）
docker network create mall-net

# 把正在运行的 MySQL 容器接进去，不需要重启容器
docker network connect mall-net mysql

# 验证：Networks 里应同时出现 bridge 和 mall-net
docker inspect mysql --format '{{json .NetworkSettings.Networks}}'
```

> MySQL 原来映射到宿主机的 3306 不受影响，Windows 仍可用 `localhost:3306`。
> 如果以后删掉 MySQL 容器重建，需要重新执行 `docker network connect`。

---

## 三、初始化 Nacos 数据库

### 3.1 取出 MySQL root 密码（不打印到屏幕）

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)
```

### 3.2 建库

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS nacos_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"'
```

### 3.3 下载官方表结构并导入

```bash
curl -fsSL -o /tmp/nacos-mysql-schema.sql \
  https://raw.githubusercontent.com/alibaba/nacos/2.3.2/distribution/conf/mysql-schema.sql

docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" nacos_config' < /tmp/nacos-mysql-schema.sql
```

### 3.4 验证

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D nacos_config -e "SHOW TABLES;"'
```

应能看到 `config_info`、`config_info_beta`、`his_config_info`、`users`、`roles` 等表。

---

## 四、启动 Nacos

先创建数据目录：

```bash
sudo mkdir -p /home/zhq123/docker-data/nacos/logs
sudo mkdir -p /home/zhq123/docker-data/nacos/data
```

然后启动容器：

```bash
docker run -d \
  --name nacos \
  --network mall-net \
  --restart unless-stopped \
  -p 8848:8848 \
  -p 9848:9848 \
  -p 9849:9849 \
  -v /home/zhq123/docker-data/nacos/logs:/home/nacos/logs \
  -v /home/zhq123/docker-data/nacos/data:/home/nacos/data \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=mysql \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos_config \
  -e MYSQL_SERVICE_USER=root \
  -e "MYSQL_SERVICE_PASSWORD=$MYSQL_ROOT_PASSWORD" \
  -e "MYSQL_SERVICE_DB_PARAM=characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
  -e JVM_XMS=256m \
  -e JVM_XMX=512m \
  -e JVM_XMN=128m \
  -e NACOS_AUTH_ENABLE=false \
  nacos/nacos-server:v2.3.2
```

### 关键环境变量说明

| 变量 | 作用 |
| --- | --- |
| `MODE=standalone` | 单机模式；不配集群时默认是 cluster，会启动失败 |
| `SPRING_DATASOURCE_PLATFORM=mysql` | 启用外置 MySQL 存储，不加则用内嵌 derby |
| `MYSQL_SERVICE_HOST=mysql` | 容器名，依赖 `mall-net` 的 DNS；用 `127.0.0.1` 会连到 Nacos 容器自己 |
| `MYSQL_SERVICE_DB_PARAM` | MySQL 8 必须带 `allowPublicKeyRetrieval=true`，否则认证失败 |
| `NACOS_AUTH_ENABLE=false` | 本地学习关闭鉴权，避免客户端还要配 token |

---

## 五、验证

### 5.1 看日志

```bash
docker logs nacos 2>&1 | grep -i "started successfully"
```

出现 `Nacos started successfully in stand alone mode. use external storage` 即成功。

### 5.2 控制台

浏览器打开 `http://localhost:8848/nacos`。若出现登录页，账号密码默认都是 `nacos`。

### 5.3 确认数据真的写进 MySQL

两类数据落在不同的表：

| 操作 | 写入表 |
| --- | --- |
| 新建命名空间 | `tenant_info` |
| 新建配置项 | `config_info` |

新建命名空间后执行：

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D nacos_config -e "SELECT tenant_id, tenant_name FROM tenant_info;"'
```

新建配置后执行：

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D nacos_config -e "SELECT data_id, group_id, tenant_id FROM config_info;"'
```

两张表都能查到记录，说明用的是外置 MySQL 而不是内嵌 derby。
注意：只建命名空间时 `config_info` 为空是正常现象，它只存配置项。

### 5.4 Windows 侧端口

```powershell
Test-NetConnection localhost -Port 8848
Test-NetConnection localhost -Port 9848
Test-NetConnection localhost -Port 9849
```

三个都通，Spring Boot 客户端才能注册。

### 5.5 命名空间 ID 与名称的区别（重要）

控制台新建命名空间时有两个字段：

| 字段 | 作用 |
| --- | --- |
| 命名空间 ID | 客户端配置里真正使用的值；留空会由 Nacos 生成一串 UUID |
| 命名空间名称 | 仅用于控制台展示，客户端不认这个值 |

Spring Cloud 中配置的是 **ID**：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        namespace: dev
```

如果建命名空间时 ID 留空（变成 UUID），而客户端写 `namespace: dev`，服务会注册到 `namespaceId=dev` 这个“隐形”命名空间：注册日志显示成功，但控制台列表为空，`tenant_info` 里也查不到对应的 ID。

正确做法：

1. 新建命名空间时把 **ID 显式写成 `dev`**，名称可以也叫 dev。
2. 用 SQL 核对 ID：
   ```bash
   docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D nacos_config -e "SELECT tenant_id, tenant_name FROM tenant_info;"'
   ```
3. 客户端 `namespace` 填 `tenant_id` 的值，而不是 `tenant_name`。

已经踩坑时的修复：

- 删除这个空的命名空间，按 ID=`dev` 重建；或
- 客户端直接填 `tenant_id` 查到的 UUID。

---

## 六、日常运维命令

```bash
docker stop nacos                 # 停止
docker start nacos                # 启动
docker logs -f nacos              # 跟踪日志
docker rm -f nacos                # 删除容器（数据在 MySQL，不会丢）
```

以后重建 Nacos 容器，只要 `mall-net` 和 `nacos_config` 库还在，配置与注册数据都保留。

---

## 七、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动日志报数据库连接失败 | Nacos 不在 `mall-net`，或 MySQL 未接入该网络 | 检查 `docker inspect` 的网络列表 |
| `Access denied` | 密码变量为空或 MySQL 认证插件 | 重新执行 3.1 的 export，确认 DB_PARAM 含 `allowPublicKeyRetrieval=true` |
| 日志报 `Nacos started successfully` 但控制台打不开 | 端口未映射或被占用 | `docker ps` 确认 8848 映射；`netstat -ano | findstr 8848` |
| 客户端注册报 `connection refused` | 只映射了 8848，缺 9848/9849 | 补映射后重建容器 |
| 表导入报错 | 库字符集或重复导入 | 库用 utf8mb4；重复导入可先 `DROP DATABASE nacos_config` 再重建 |
| 控制台要求登录 | 鉴权开关 | 本配置已关闭；若仍登录，账号密码 `nacos/nacos` |
| 服务已注册但控制台看不到 | `tenant_id` 是 UUID，客户端却把命名空间名称当成 ID 使用 | 建命名空间时显式填写 ID（如 `dev`），或查 `tenant_info` 后按真实 ID 配置 |

---

## 八、完成后

Nacos 启动成功后告诉我，我继续把 `GatewayApplication` 补上。届时网关日志里不应再有 Nacos 连接失败，`/actuator/health` 返回 `UP`，服务列表里会先出现 `mall-gateway`。
