# P0-03 用 Docker 启动 Kafka（KRaft 单节点）

> 当前环境已确认：Docker 29.1.3、无 compose 插件、自定义网络 `mall-net` 已存在。
> 镜像 `apache/kafka:3.7.0` 已确认可用。
> 数据目录沿用约定：`/home/zhq123/docker-data/kafka`。

---

## 一、端口与监听器设计

| 监听器 | 容器内端口 | 宿主机端口 | 谁用 |
| --- | --- | --- | --- |
| `EXTERNAL` | 9094 | 9094 | Windows 上的 Spring Boot |
| `INTERNAL` | 9092 | 不映射 | `mall-net` 内的其他容器 |
| `CONTROLLER` | 9093 | 不映射 | Kafka 自己（KRaft 控制器） |

必须配两个 listener 的原因：

- Spring Boot 在 Windows 上，通过 `localhost:9094` 访问；
- 以后如果有容器要连 Kafka（比如 Kafka UI、其他中间件），走 `kafka:9092`；
- 如果只配一个 `localhost:9092`，容器内访问会解析到自己，Windows 访问又需要 9092 映射，容易互相打架。

---

## 二、创建数据目录

官方镜像以 UID 1000 的 `kafka` 用户运行，目录要给对应权限：

```bash
sudo mkdir -p /home/zhq123/docker-data/kafka
sudo chown -R 1000:1000 /home/zhq123/docker-data/kafka
```

---

## 三、启动容器

```bash
docker run -d \
  --name kafka \
  --network mall-net \
  --restart unless-stopped \
  -p 9094:9094 \
  -v /home/zhq123/docker-data/kafka:/var/lib/kafka/data \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=INTERNAL://:9092,CONTROLLER://:9093,EXTERNAL://:9094 \
  -e KAFKA_ADVERTISED_LISTENERS=INTERNAL://kafka:9092,EXTERNAL://localhost:9094 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL \
  -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e KAFKA_NUM_PARTITIONS=3 \
  apache/kafka:3.7.0
```

关键变量：

| 变量 | 作用 |
| --- | --- |
| `KAFKA_PROCESS_ROLES` | KRaft 模式下单节点同时当 broker 和 controller |
| `KAFKA_ADVERTISED_LISTENERS` | 告诉客户端「连上之后该用哪个地址」，配错会连上后立刻断开 |
| `KAFKA_LOG_DIRS` | 数据目录，配合 `-v` 持久化 |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` | 单节点必须为 1，默认 3 会创建失败 |
| `CLUSTER_ID` | KRaft 集群 ID，首次格式化时用 |

---

## 四、验证

### 4.1 容器状态与日志

```bash
docker ps --filter name=kafka
docker logs kafka 2>&1 | grep -i "started"
```

看到 Kafka 启动日志即可。

### 4.2 创建业务 topic

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic order-created \
  --partitions 3 --replication-factor 1

docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic order-cancelled \
  --partitions 3 --replication-factor 1

docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

### 4.3 生产/消费自测

```bash
echo "hello-kafka" | docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic order-created

docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-created \
  --from-beginning --max-messages 1
```

能消费到 `hello-kafka` 即可。

### 4.4 Windows 侧端口

```powershell
Test-NetConnection localhost -Port 9094
```

必须通，Spring Boot 生产端才能连上。

### 4.5 确认数据落在宿主机

```bash
ls -la /home/zhq123/docker-data/kafka
```

应能看到 Kafka 的日志段目录与 meta 文件。

---

## 五、日常运维命令

```bash
docker stop kafka
docker start kafka
docker logs -f kafka
docker rm -f kafka          # 数据在 bind mount 里，不会丢
```

---

## 六、可选：Kafka Web UI

命令行创建 topic 不方便时，可以起一个 Web 控制台。推荐 Kafbat UI（原 Provectus Kafka UI 的社区维护版）：

```bash
docker run -d \
  --name kafka-ui \
  --network mall-net \
  --restart unless-stopped \
  -p 8090:8080 \
  -e KAFKA_CLUSTERS_0_NAME=local \
  -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092 \
  ghcr.io/kafbat/kafka-ui:latest
```

访问 `http://localhost:8090`，在 **Topics → Add Topic** 里填：

| 字段 | 值 |
| --- | --- |
| Topic name | `order-created` |
| Partitions | `3` |
| Replication factor | `1` |

同样方式再建 `order-cancelled`。

注意：

- 必须用 `kafka:9092`，不能用 `localhost:9094`。UI 在容器里，`localhost` 是它自己；`9094` 的 advertised 地址只对 Windows 客户端有效。
- UI 本身无状态，不需要单独的数据目录。
- 想换成旧版可用镜像 `provectuslabs/kafka-ui:v0.7.2`，环境变量完全一样。
- 停止/删除：`docker stop kafka-ui`、`docker rm -f kafka-ui`。

---

## 七、常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动即退出，日志报权限错误 | 宿主机目录属主不对 | `sudo chown -R 1000:1000 /home/zhq123/docker-data/kafka` |
| 客户端能连上但立刻断开 / 超时 | advertised listeners 配错 | Windows 用 `localhost:9094`，容器用 `kafka:9092` |
| 创建 topic 报 replication factor 过大 | 单节点默认副本数 3 | 加 `--replication-factor 1`，并设置 `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1` |
| 9092 端口冲突 | 想同时映射 9092，但该端口已被占用 | 宿主机只映射 9094，内部继续用 9092 |
| 数据写到了容器内部 | 忘记挂载 `/var/lib/kafka/data` | 检查 `docker inspect kafka --format '{{json .Mounts}}'` |
| 容器重建后 topic 丢失 | 没挂载数据目录，或目录被删 | 保持 bind mount，删除容器不要加 `-v` 清卷 |
