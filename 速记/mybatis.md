1. oldschool做法：配mybatis的xml，指定mapper，再配置mapper的xml

XMLConfigBuilder解析mybatis的配置

->

加载Environment和Mappers

->

根据mapper的配置类型（resource、url、class）进行解析，前两种通过XMLMapperBuilder直接读mapper的xml，后者直接读mapper接口类元数据

2. springboot：@Mapper注解，不使用@MapperScan，自动扫描整个项目含有@Mapper注解的接口类（由@SpringBootApplication -> @EnableAutoConfiguration -> @AutoConfigurationPackage实现，会将整个应用的包路径作为bean注入）

MybatisAutoConfiguration

->

MybatisAutoConfiguration.MapperScannerRegistrarNotFoundConfiguration，具备`@Import(AutoConfiguredMapperScannerRegistrar.class)`注解

该注解与@MapperScanner注解互斥（@ConditionalOnMissingBean(MapperFactoryBean.class)，只要使用了MapperScan注解，容器就会产生MapperFactoryBean，触发Condition注解）

-> 

AutoConfiguredMapperScannerRegistrar扫描整个项目包路径下的@Mapper注解接口组件

->

再注册SqlSessionFactory的bean

3. springboot：只配mapper的xml，再根据xml的namespace添加mapper到mapperRegistry（configuration）

MyBatisAutoConfiguration

-> 

注入一个SqlSessionFactory的bean

-> 

调用SqlSesssionFactoryBean的afterProperties()

-> 

调用buildSqlSessionFactory()方法

->

调用直接读取应用yml文件下的`mybatis.mapperLocations`属性，获取mapper的xml文件路径

->

XMLMapperBuilder直接读mapper的xml文件注册








1. 先修改Mapper接口类的bean定义，添加sqlSession、sqlSessionFactory等引用到属性定义中，并包装成一个MapperFactoryBean的工厂bean

2. 将mapper的xml配置和类里面的db操作注解全部解析成statement缓存到configuration中，并将mapper对象缓存到registry里

3. 业务注入mapper的bean时，相当于从configuration中获取mapper的代理类