# GB模块迁移规范

## 核心理念

**只复制，不自创。** 老项目有什么就复制什么，不修改业务逻辑，不自己补充方法。

---

## 迁移标准流程

每次迁移新接口，按以下顺序执行，不要跳步。

### 第一步：完整阅读老项目代码

复制 Controller 前，把它的所有 import 和方法体**完整看完**，不要断章取义。

需要检查的内容：
- 所有 import 语句（内部类 / 工具类 / 第三方包）
- 方法签名（参数、返回值、注解）
- 方法体内部调用的所有 Service、Utils、Entity
- SQL 或 MyBatis 相关的 XML 文件

### 第二步：一次性复制所有依赖的类

按以下顺序复制：

**1. 工具类**（utils 包）
```
R.java                    老式响应类
UploadFile.java          文件上传
MyAPPIDConfig.java       微信配置
WeChatUtil.java          微信工具
DateUtils.java           日期工具
PinYin4jUtils.java       拼音工具
GbTypeUtils.java         所有 GB 常量（不拆分，一个文件搞定）
```

**2. 实体类**（entity 包）
- 复制后检查字段是否完整
- 补上缺失的 `@TableName` 注解
- 补上缺失的数据库字段

**3. Mapper 接口**（mapper 包）
- 直接复制，继承 `BaseMapper`

**4. Service 接口**
- 必须 `extends IService<实体>`
- **不要手动声明已继承的方法**（如 `save()`、`getById()` 等）
- 如需额外方法，**必须从老项目 Service 接口中复制，不要自己猜**

**5. Service 实现**
- 完整复制，包括所有私有方法
- 方法签名必须和老项目一致

**6. Controller**
- 完整复制，包括所有注解
- 返回值类型必须一致

### 第三步：处理 Spring Boot 3.x 兼容性

复制代码时，必须手动替换以下内容：

| 老项目（Spring Boot 2.x） | 新项目（Spring Boot 3.x） |
|---|---|
| `javax.servlet.http.HttpSession` | `jakarta.servlet.http.HttpSession` |
| `javax.servlet.ServletContext` | `jakarta.servlet.ServletContext` |
| `javax.servlet.http.HttpServletRequest` | `jakarta.servlet.http.HttpServletRequest` |
| 其他 javax 开头包 | 替换为 jakarta |

### 第四步：添加依赖

对比老项目 `pom.xml`，一次性补齐所有缺失的依赖。常见遗漏：

```xml
<!-- 拼音 -->
<dependency>
    <groupId>com.belerweb</groupId>
    <artifactId>pinyin4j</artifactId>
    <version>2.5.1</version>
</dependency>
```

### 第五步：配置 JSON 序列化

老项目如果使用 fastjson2，需要以下配置：

**1. 加依赖**（注意包名，不是 starter）

```xml
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.43</version>
</dependency>
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2-extension-spring6</artifactId>
    <version>2.0.43</version>
</dependency>
```

**2. 加配置类**

```java
package com.nongxinle.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FastJsonHttpMessageConverter());
    }
}
```

**3. Controller 的 `produces` 处理**

- 如果老项目有 `produces = "text/html;charset=UTF-8"`，去掉它
- 或者确保 JSON Converter 能正确处理 Content-Type

### 第六步：编译验证

完成以上所有步骤后，执行编译验证，通过后再调接口。

```
./apache-maven-3.9.6/bin/mvn compile -f /Users/lpy/Documents/javaWeb/kuangjia/aigrain/pom.xml
```

---

## 踩坑记录

### 1. Service 接口不要重复声明已继承的方法

**错误示例：**
```java
public interface GbDistributerFatherGoodsService extends IService<GbDistributerFatherGoodsEntity> {
    int save(GbDistributerFatherGoodsEntity entity); // 冲突！
}
```

**正确做法：**
```java
public interface GbDistributerFatherGoodsService extends IService<GbDistributerFatherGoodsEntity> {
    // 不声明，父接口的方法直接继承使用
}
```

### 2. 方法签名必须完全一致

从老项目复制 Service 方法时，返回值类型、参数类型必须完全一致，不要自己猜测。

### 3. 常量文件不要拆分

所有 GB 模块常量写在 `com.nongxinle.utils.GbTypeUtils` 一个文件里，不要按类型拆成多个文件。

### 4. `R extends HashMap` 的序列化问题

`R` 类本身不需要改，但必须配置好 JSON Converter 才能正常序列化返回。

---

## 迁移铁律

> **只复制，不自创。小程序接口完全依赖现有老接口，任何擅自改动都会导致接口不匹配。老项目有什么就复制什么，保持 100% 一致。**
