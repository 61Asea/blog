# LangChain 基础概念

## LangChain模块和体系

LangChain 是一个用于开发由大型语言模型（LLMs）驱动的应用程序的框架。

LangChain 简化了LLM应用程序生命周期的每个阶段：
● 开发：使用LangChain的开源构建模块和组件构建您的应用程序。利用第三方集成和模板快速启动。
● 生产部署：使用LangSmith检查、监控和评估您的链，以便您可以持续优化并自信地部署。
● 部署：使用LangServe将任何链转换为API。

架构组成：
● langchain-core：基本抽象和LangChain表达语言。
● langchain-community：第三方集成。
  ○ 合作伙伴包（例如 langchain-openai，langchain-anthropic 等）：一些集成已进一步拆分为仅依赖于 langchain-core 的轻量级包。
● langchain：构成应用程序认知架构的链、代理和检索策略。
● langgraph：通过将步骤建模为图中的边缘和节点，使用LLMs构建稳健且有状态的多参与者应用程序。
● langserve：将LangChain链部署为REST API。
● LangSmith：一个开发平台，可让您调试、测试、评估和监控LLM应用程序。

## LLM & Chat models PromptTemplates, OutputParses Chains

### LLMs

指的是对接集成的大模型们，通过LangChain官方SDK提供的抽象行为，抹平各个LLM之间的细节差异。LangChain 不提供任何 LLMs，而是依赖于第三方集成。

### Message（消息）

抽象了用户与大语言模型交互涉及到的几种不同类型的消息。

所有消息都有 role、content 和 response_metadata 属性。 role 描述了消息的发出者是谁。 LangChain 为不同的角色设计了不同的消息类。

消息类型：
- Human Message：指用户发送的提问消息
- AI Message: 指大模型发送给用户的消息
    - response_metadata：指大模型消息的元数据，对所有不同大模型的消息进行统一抽象，以屏蔽细节
    - tool_calls：指大模型针对不同Reaction，选取对应策略来调用tool工具（API）。tool_calls表示了大模型的最后决定，而tool_calls的可选性由开发者实现并初始化时提供
        - name：调用工具的名称
        - args：调用时的入参
        - id：该工具的调用id
- System Message：指系统消息，告诉模型如何行为
- Function Message：本地python函数调用的消息，代表python函数调用的结果
- Tool Message：调用tools后生成的消息，代表着工具调用后的结果

### Prompt Template（提示词模板）

提示模板有助于将用户输入和参数转换为语言模型的指令。 这可用于引导模型的响应，帮助其理解上下文并生成相关和连贯的基于语言的输出。 提示模板以字典作为输入，其中每个键代表要填充的提示模板中的变量。 提示模板输出一个 PromptValue。这个 PromptValue 可以传递给 LLM 或 ChatModel，并且还可以转换为字符串或消息列表。 存在 PromptValue 的原因是为了方便在字符串和消息之间切换。

存在这几种不同类型的提示模板：

- String PromptTemplates: 字符串模板，格式化单个字符串

```python
from langchain_core.prompts import PromptTemplate
prompt_template = PromptTemplate.from_template("Tell me a joke about {topic}")
prompt_template.invoke({"topic": "cats"})
```

- ChatPromptTemplates: 消息列表模板，格式化消息列表

```python
from langchain_core.prompts import ChatPromptTemplate
prompt_template = ChatPromptTemplate.from_messages([
    ("system", "You are a helpful assistant"),
    ("user", "Tell me a joke about {topic}")
])
prompt_template.invoke({"topic": "cats"})
```

> 当调用此 ChatPromptTemplate 时，将构建两条消息。 第一条是系统消息，没有要格式化的变量。 第二条是 HumanMessage，并将根据用户传入的 topic 变量进行格式化。

- MessagesPlaceholder：消息列表模板的生成holder，格式化消息列表中的动态子消息，可以通过代码的方式生成任意多条子消息

```python
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import HumanMessage
prompt_template = ChatPromptTemplate.from_messages([
    ("system", "You are a helpful assistant"),
    MessagesPlaceholder("msgs")
])
prompt_template.invoke({"msgs": [HumanMessage(content="hi!")]})
```

> 这个提示模板负责在特定位置添加消息列表。 在上面的 ChatPromptTemplate 中，我们看到了如何格式化两条消息，每条消息都是一个字符串。 但是，如果我们希望用户传入一个消息列表，我们将其插入到特定位置，该怎么办？ 这就是您使用 MessagesPlaceholder 的方式。

```python
prompt_template = ChatPromptTemplate.from_messages([
    ("system", "You are a helpful assistant"),
    ("placeholder", "{msgs}") # <-- 这是更改的部分
])  
```

### Output parsers（输出解析器）

这里提到的是将模型的文本输出进行解析，转换为更结构化表示的解析器。 越来越多的模型支持函数（或工具）调用，可以自动处理这一过程。建议使用函数/工具调用，而不是输出解析。

常见的解析器：
```python
from langchain_core.output_parsers import StrOutputParser
parser = StrOutputParser()

from langchain_core.output_parsers import JsonOutputParser
parser = JsonOutputParser()
```

### Few-shot、Example Selector

LangChain支持通过用于给LLM提供一个示例，减少幻觉的产生，增加LLM的推理能力

包括：Few-shot少量示例、ExampleSelector示例选择器，

- Few-shot：

  ```python
  from langchain_core.prompts import PromptTemplate
  from langchain_core.prompts import FewShotPromptTemplate

  examples = [{
          "question": "谁活得更长，穆罕默德·阿里还是艾伦·图灵？",
          "answer": """
          是否需要后续问题：是的。
          后续问题：穆罕默德·阿里去世时多大年纪？
          中间答案：穆罕默德·阿里去世时74岁。
          后续问题：艾伦·图灵去世时多大年纪？
          中间答案：艾伦·图灵去世时41岁。
          所以最终答案是：穆罕默德·阿里
          """,
              },
              {
                  "question": "克雷格斯列表的创始人是什么时候出生的？",
                  "answer": """
          是否需要后续问题：是的。
          后续问题：克雷格斯列表的创始人是谁？
          中间答案：克雷格斯列表的创始人是克雷格·纽马克。
          后续问题：克雷格·纽马克是什么时候出生的？
          中间答案：克雷格·纽马克于1952年12月6日出生。
          所以最终答案是：1952年12月6日
          """,
              }]

  example_prompt = PromptTemplate.from_template("问题：{question}\n{answer}")
  prompt = FewShotPromptTemplate(
    examples = examples,
    example_prompt = example_prompt,
    suffix = "问题: {input}",
  )

  result = prompt.invoke({"input": "乔治·华盛顿的父亲是谁？"})
  print(result)

  ```
- SemanticSimilarityExampleSelector

  ```python
  from langchain_chroma import Chroma
  from langchain_core.example_selectors import SemanticSimilarityExampleSelector
  from langchain_core.prompts import PromptTemplate
  from langchain_openai import OpenAIEmbeddings

  examples = [{}, {}] # 如上

  # 将示例存储到chroma向量库中（内存型数据库），具体的嵌入通过openAI的embeddings接口进行操作
  selector = SemanticSimilarityExampleSelector.from_examples(examples, OpenAIEmbeddings(), Chroma, k=1)
  example_prompt = PromptTemplate.from_template("问题:{question}\n{answer}")

  # 测试代码，用于展示出与用户输入问题中，最接近的示例（接近程度通过余弦相似度进行计算）
  question = "。。。"
  selected_examples = selector.select_examples({"question": question})
  print(f"与输入最相似的示例: {question}")
  for example in selected_examples:
    print("\n")
    for k, v in example.items():
        print(f"{k}: {v}")

  # 基于Few-shot提示词模板进行
  prompt = FewShotPromptTemplate(
    example_selector=selector,
    example_prompt=example_prompt,
    suffix="...{input}"
  )
  prompt.invoke({"input": "请问xxxx"})
  ```

## LCEL & Runnable Interface

### RunnableLambda、RunnableParallel

### Chains（链式调用）

```python
# langchain对openAi的整合包
import asyncio

from langchain_openai import ChatOpenAI
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.messages import HumanMessage
import os

print(os.getenv('OPENAI_API_KEY'))
print(os.getenv('OPENAI_BASE_URL'))

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个英雄联盟职业比赛的资深观众，同时也是一个百度贴吧抗压背锅吧的正义人士，你对串子和阴阳怪气的言论深恶痛绝。"
               "请你对以下百度帖子的标题和帖子内容进行情感分析，判断他的言论是否属于串子、引战、阴阳怪气"),
    ("system", "并展示出你的分析思考过程"),
    ("placeholder", "{msgs}")
])
model = ChatOpenAI(model="gpt-4o")
parser = StrOutputParser()

chain = prompt | model | parser

input = {"msgs": [HumanMessage(content="标题：小天他没骗我们，选出狗就是2-0开局"), HumanMessage(content="帖子内容：用户转发了一个图片。图片内容主要介绍了小天这个职业选手在苦练新的英雄，并表示小天玩这个英雄想杀谁就杀谁，主宰别人的生死。WBG打野小天就练了最近大热打野虚化狗，无限超神，不禁感叹我比赛怎么不选这个啊！玩得好爽，想杀谁就杀谁，主宰别人的生死！")]}

# invoke
# result = chain.invoke(input)
# print(result)

# stream
for chunk in chain.stream(input):
    print(chunk, end= "", flush=True)

# ainvoke
# async def async_invoke():
#     result = await chain.ainvoke(input)
#     print(result)
#
# asyncio.run(async_invoke())

# astream
# async def async_stream():
#     async for chunk in chain.astream(input):
#         print(chunk, end="", flush=True)
# asyncio.run(async_stream())
```

## LangServer


## LangChain Agent相关
### tavily - 网页查询API
