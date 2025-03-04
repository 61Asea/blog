输入：一堆向量（a set of vector），也称为sequence序列，如一段文字、图片、语音。

输出：每一个向量都有一个对应的label
- 如果每一个label是一个数值，那么就是一个regression的问题
- 如果每一个label是一个Class，就是一个Classfication的问题。

输入与输出关系：
1. sequence labeling：输入与输出数目一致。常见于：词性标注、Social Network（推荐算法）
2. 输入是sequence，输出只有一个label。常见于：评价
3. sequence to sequence：输出由机器自己决定。常见于：翻译、语音辨识

Phonetic？

word embedding：每一个词其对应的向量，会根据种类相对聚集