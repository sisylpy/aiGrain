# 本地 Maven 必须使用 JDK 17+

本项目 **`java.version`** 为 **17**（Spring Boot 3）。若 `mvn -version` 仍显示 **`1.8`**，会看到编译错误：

```text
无效的标记: --release
```

请以 **`mvn -version` 输出中的 「Java version」** 为准（不要只看默认 `java` 命令）。

## macOS 示例（OpenJDK 17 已安装在用户目录）

```bash
/usr/libexec/java_home -V
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

若本机另有 JDK 21/24，只要 **≥17** 且与 Boot 插件一致即可。

## 校验

```bash
mvn -q compile test
```

应无 `--release` 相关报错。
