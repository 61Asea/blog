# Mybatis：xml配置示范

传统Mybatis的xml配置分为：主配置、单个mapper的statement配置

## 主配置：

> \<environments>和\<mappers>必有，其它按需配置

- 数据库环境（\<environments>及其子节点）：事务管理器、**数据源**
- 全部Mapper（\<mappers>及其子结点）：mapper的**资源解析方式与位置**
- 设置（\<settings>）：指定Mybatis的一些设置，如db字段自动转换小驼峰等
- 别名（\<typeAliases>）：设置pojo/mapper命名空间的别名
- 属性引用：用于动态读取某些配置的值

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
"http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
	<properties resource="mybatis-mysql.properties">
		<property name="driver-mysql" value="com.mysql.jdbc.Driver"></property>
		<property name="url" value="jdbc:mysql://127.0.0.1:3306/etao" ></property>
		<property name="username" value="root"></property>
		<property name="password" value="cope9020"></property>
	</properties>
 
	<settings>
        <!-- 将数据库字段命名规则A_COLUMN转换为Java使用的驼峰式命名规则aCloumn -->
		<setting name="mapUnderscoreToCamelCase" value="true" />
	</settings>
 
	<typeAliases>
		<typeAlias alias="User" type="com.emerson.learning.pojo.User" />
	</typeAliases>
 
	<environments default="development">
		<environment id="development">
			<transactionManager type="JDBC" />
			<dataSource type="POOLED">
				<property name="driver" value="${driver-mysql}" />
				<property name="url" value="${url}" />
				<property name="username" value="${username}" />
				<property name="password" value="${password}" />
			</dataSource>
		</environment>
 
		<environment id="product">
			<transactionManager type="JDBC" />
			<dataSource type="POOLED">
				<property name="driver" value="com.mysql.jdbc.Driver" />
				<property name="url" value="jdbc:mysql://127.0.0.1:3306/mybatis" />
				<property name="username" value="root" />
				<property name="password" value="cope9020" />
			</dataSource>
		</environment>
	</environments>
 
	<mappers>
        <!-- 通过xml读取statement -->
		<mapper resource="com/emerson/learning/mapping/User.xml" />
        <!-- 通过Mapper类读取statement -->
		<mapper class="com.emerson.learning.dao.ICommunicatorDao" />
	</mappers>
</configuration>
```

## **单个mapper的statement配置**

包含：
- 命名空间（\<mapper namespace="...">）：可以认为是mapper的标识符
- sql模板（\<sql>）：子结点，用于抽取某些共同的sql片段
- 具体statement(\<select>、\<update>、\<insert>)：子结点，表示具体的PreparedStatement，加载到Configuration中

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
"http://mybatis.org/dtd/mybatis-3-mapper.dtd">
 
<mapper namespace="com.emerson.learning.mapping.user">
	<!-- 根据传入的Id值，到数据库中查询记录 -->
	<select id="getByID" parameterType="int" resultType="User">
		SELECT user_id, user_name, user_password, nick_name, email, is_valid, created_time, updated_time
		FROM sys_user WHERE user_id = #{id}
	</select>
 
	<!-- 按用户名进行模糊查询 -->
    <select id="queryByName" parameterType="User" resultType="User">
		SELECT user_id, user_name, user_password, nick_name, email, is_valid, created_time, updated_time
		FROM sys_user
		<where>
			<if test="userName != null">user_name like '%' #{userName} '%'</if>
		</where>
	</select>
 
	<!-- 创建新用户，并写入到数据表中 -->
    <!-- 写入新记录并返回主键值，注意，这里的KeyProperty应该是Java类里的属性名称，而非数据表中的字段名 -->
	<insert id="insertUser" parameterType="User" useGeneratedKeys="true"
		keyProperty="userId">
		INSERT INTO sys_user(user_name, user_password, nick_name,
		user_type_id,
		is_valid, created_time)
		VALUES(#{userName},
		#{userPassword}, #{nickName}, #{userTypeId}, #{isValid},
		#{createdTime})
	</insert>
 
	<!-- 更新用户信息，并写回到数据表中 -->
    <update id="udpateUser" parameterType="User">
		UPDATE sys_user
		SET
		user_name = #{userName}, user_password = #{userPassword}, nick_name =
		#{nickName}, user_type_id = #{userTypeId}, is_valid, = #{isValid}
		WHERE user_id = #{id}
	</update>
 
	<!-- 根据传入的Id值，删除单条记录 -->
    <delete id="deleteById" parameterType="int">
		DELETE FROM sys_user WHERE
		user_id = #{id}
	</delete>
 
	<!-- 根据传入的Id值列表，删除多条记录 -->
    <delete id="deleteBatch" parameterType="java.util.List">
		DELETE FROM sys_user WHERE user_id in
		<foreach collection="list" item="item" index="index" open="("
			close=")" separator=",">
			#{item}
		</foreach>
	</delete>
</mapper>
```