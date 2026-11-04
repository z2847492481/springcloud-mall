# P0-04 用 Docker 启动 XXL-JOB（MySQL 存储）

> 当前环境：Docker 29.1.3、无 compose、`mall-net` 已存在、MySQL 在 `mysql:3306`。
> 镜像 `xuxueli/xxl-job-admin:2.4.1` 与官方建表 SQL 已验证可用。
> 数据目录约定：`/home/zhq123/docker-data/xxl-job`。

---

## 一、整体结构

```
Windows 浏览器 ──localhost:8088──▶ xxl-job-admin 容器
                                      │ 调度 / 回调
                                      ▼
                              mall-order 执行器（Windows，:9999）
```

调度中心在容器里，执行器在 Windows 的 `mall-order` 进程里，所以容器要能主动访问 Windows 的 9999 端口。

当前 Windows 的局域网 IPv4 是 `192.168.1.5`，但实测 WSL 访问这个地址会被 Windows 防火墙拦截（`Connection refused`），而 host 网络容器的 `127.0.0.1` 可以直通 Windows 本机。因此调度中心改用 `--network host`，执行器注册地址使用 `http://127.0.0.1:9999/`。

---

## 二、初始化 `xxl_job` 库

在 WSL 终端执行：

```bash
export MYSQL_ROOT_PASSWORD=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)

docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"'

curl -fsSL -o /tmp/tables_xxl_job.sql \
  https://raw.githubusercontent.com/xuxueli/xxl-job/2.4.1/doc/db/tables_xxl_job.sql

docker exec -i mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" xxl_job' < /tmp/tables_xxl_job.sql
```

验证：

```bash
docker exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D xxl_job -e "SHOW TABLES;"'
```

应能看到 `xxl_job_info`、`xxl_job_log`、`xxl_job_registry`、`xxl_job_group` 等表。

---

## 三、启动调度中心

```bash
sudo mkdir -p /home/zhq123/docker-data/xxl-job/logs

docker run -d \
  --name xxl-job-admin \
  --network host \
  --restart unless-stopped \
  -v /home/zhq123/docker-data/xxl-job/logs:/data/applogs \
  -e TZ=Asia/Shanghai \
  -e "PARAMS=--server.port=8088 --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true --spring.datasource.username=root --spring.datasource.password=$(docker exec mysql printenv MYSQL_ROOT_PASSWORD)" \
  xuxueli/xxl-job-admin:2.4.1
```

关键点：

| 配置 | 作用 |
| --- | --- |
| `--network host` | 复用 WSL 网络，`127.0.0.1` 可直通 Windows 执行器 |
| `--server.port=8088` | 镜像内默认 8080，改成 8088 避免与网关冲突 |
| `PARAMS` | 覆盖数据源，host 网络下 MySQL 用 `127.0.0.1:3306` |
| `TZ=Asia/Shanghai` | 避免调度时间差 8 小时 |
| 挂载 `/data/applogs` | 只挂日志；业务数据在 MySQL |

> host 网络模式下不需要 `-p 8088:8080`，8088 直接绑定在 WSL/Windows 本机回环上。

### 验证

```bash
docker logs xxl-job-admin 2>&1 | grep -i "Started"
```

浏览器打开 `http://localhost:8088/xxl-job-admin`，默认账号密码 `admin / 123456`。

---

## 四、打通调度中心到 Windows 执行器

调度中心需要回调执行器，必须满足：

1. 执行器监听 `9999` 端口；
2. 执行器注册地址显式写成 `http://127.0.0.1:9999/`，**必须带 `http://` 前缀**；只写 `127.0.0.1:9999` 会报 `no protocol`；
3. 调度中心使用 `--network host`，与执行器共享回环地址。

验证方式（执行器启动后）：

```bash
docker run --rm --network host busybox sh -c \
  "nc -z -w 2 127.0.0.1 9999 && echo REACHABLE || echo BLOCKED"
```

输出 `REACHABLE` 即打通。

> 如果以后换网络或 Docker 网络模式，host 网络方案失效，兜底方案：调度中心改为 Windows 本地 `java -jar` 启动（端口 8088），数据库连 `localhost:3306`，执行器与调度中心同机，最稳。

---

## 五、日常运维

```bash
docker stop xxl-job-admin
docker start xxl-job-admin
docker logs -f xxl-job-admin
docker rm -f xxl-job-admin     # 业务数据在 MySQL，不会丢
```

日志目录：`/home/zhq123/docker-data/xxl-job/logs/xxl-job/`。

---

## 六、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报数据库连接失败 | 数据源 URL 或密码错 | host 网络下用 `127.0.0.1:3306`，并带 `allowPublicKeyRetrieval=true` |
| 控制台打不开 | 容器未启动或端口被占 | host 网络下看 8088 是否监听，`docker logs` 看启动日志 |
| 执行器一直离线 | 调度中心回调不到 Windows 9999 | 调度中心用 `--network host`，地址写 `http://127.0.0.1:9999/`，用 busybox `nc` 验证 |
| 任务执行时间差 8 小时 | 容器时区不对 | `TZ=Asia/Shanghai`，并确认 JDBC `serverTimezone` |
| 触发报 `no protocol: 127.0.0.1:9999/run` | 执行器地址没写协议 | 改成 `http://127.0.0.1:9999/` |
| 调度中心重启后任务丢失 | 没有使用外置 MySQL | 确认 `xxl_job` 库里有数据 |
| 执行器端口冲突 | 9999 被占用 | `netstat -ano | findstr 9999`，换端口并同步调度配置 |
