# Domain Primitive（DP）

> Value Object：VO值对象

Domain Primitive 是一个在特定领域里，拥有精准定义的、可自我验证的、拥有行为的`Value Object`

- 是传统意义上的Value Object，拥有不可变（Immutable）的特性
- 是一个完整的概念（对象）整体，拥有精准定义
- 在原始 VO 的基础上要求每个 DP 拥有概念的整体，而不仅仅是值对象。在 VO 的 Immutable 基础上增加了 Validity 和行为。

DP三大原则：

1. Make Implicit Concepts Explicit：将隐性的`概念`显性化

    做法：将use case的业务逻辑归属放在已有的概念中判断是否合理，如若不合理，则将涉及到的**隐性概念**进行**独立显性**，强调创建新概念

    效果：
    - 提高接口清晰度
    - 提供入参数据的验证与错误处理
    - 提高业务流程的清晰度
    - 提高模块代码的可测试性

2. Make Implicit Context Explicit：将隐性的`上下文`显性化

    做法：在已有概念上，将概念涉及到的**隐性上下文**进行**组合显性**，强调合并已有概念以形成独立完整的概念

    > 需要把所有隐性的条件显性化，而这些条件整体组成当前的上下文

    效果：确保代码接口在保持清晰度的情况下，仍旧保证内部对改变的适配逻辑。而且适配逻辑是聚合到了DP对象中的，而不是作为形参传递进来

3. Encapsulate Multi-Object Behavior：封装多对象行为

    做法：use case中涉及到多个对象之间的行为，通过dp包装具体的业务逻辑

使用场景：复杂的数据结构：比如 Map<String, List\<Integer\>> 等，尽量能把 Map 的所有操作包装掉，仅暴露必要行为

总结：是一种带有业务行为逻辑的Value Object，它一般用于封装跟实体无关的`无状态`计算逻辑

> 与之对应，跟实体有关的有状态计算（单对象的有状态的行为，包括业务校验），则用Entity Object进行封装

# 参考
- [Domain Primitive](https://mp.weixin.qq.com/s?__biz=MzAxNDEwNjk5OQ==&chksm=83953c2cb4e2b53a6af3b5a82c3b7d7ed932bfe83f59877a935445ae89edd0ff4ee1c4e82fba&idx=1&mid=2650403892&scene=21&sn=a91fa477392e80f9420a8ca4d26bcace#wechat_redirect)