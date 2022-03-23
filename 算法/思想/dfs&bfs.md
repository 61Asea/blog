# 深度优先、广度优先

# **深度优先**

场景：树的前、中、后序遍历、图

思想：从未访问的顶点V开始，沿着其一条分支路一直走到底，然后从底部**回溯**到上一个节点，再从另一条路走到底，直到所有的顶点都遍历完成

实现：`栈` + 迭代、`方法栈` + 递归

- 递归模板

    ```java
    class Solution {
        public List<Integer> xxxOrder(TreeNode root) {
            List<Integer> ans = new ArrayList<>();
            // xxx(c, ans);
            return ans;
        }

        // 1. 后序遍历
        private void xxxPostOrder(TreeNode c, List<Integer> ans) {
            if (c == null) {
                return;
            }

            xxxPostOrder(c.left); // 左
            xxxPostOrder(c.right); // 右
            ans.add(c); // 根
        }


        // 2. 中序遍历
        private void xxxInOrder(TreeNode c, List<Integer> ans) {
            if (c == null) {
                return;
            }

            xxxInOrder(c.left); // 左
            ans.add(c); // 根
            xxxInOrder(c.right); // 右
        }

        // 3. 前序遍历
        private void xxxPreOrder(TreeNode c, List<Integer> ans) {
            if (c == null) {
                return;
            }

            ans.add(c); // 根
            xxxPreOrder(c.left); // 左
            xxxPreOrder(c.right); // 右
        }
    }
    ```

- 迭代模板：通过栈来模拟方法栈，**入栈顺序与遍历顺序`相反`**

    问题：正常编写的前、中、后序的迭代不太统一，主要在于处理节点和节点入栈的方式不同，节点入栈需要依托对根节点的左、右指针判断，这样容易出现**重复操作**

    解决方案：在重复入栈的节点后，再入栈null节点，null节点表示**该节点已出过栈，且其指针已处理完毕，下次处理仅需输出结果即可**

    ```java
    class Solution {
        // 以中序作为例子
        public List<Integer> inOrder(TreeNode root) {
            List<Integer> res = new ArrayList<>();
            Stack<TreeNode> stack = new Stack<>();
            if (root != null) {
                stack.push(root);
            }
            while (!stack.isEmpty()) {
                TreeNode c = stack.pop();
                if (c != null) {
                    // 左
                    if (c.left != null) {
                        stack.push(c.left);
                    }

                    // 中
                    stack.push(c);
                    stack.push(null);

                    // 右
                    if (c.right != null) {
                        stack.push(c.right);
                    }
                } else {
                    // 其子节点关系已入栈，取出直接处理即可
                    c = stack.pop();
                    res.add(c.val);
                }
            }
            return res;
        }
    }
    ```

# **广度优先**

场景：树的层序遍历、图

实现：`队列`

# 重点参考
- [彻底吃透前中后序递归法（递归三部曲）和迭代法（不统一写法与统一写法）](https://leetcode-cn.com/problems/binary-tree-inorder-traversal/solution/dai-ma-sui-xiang-lu-che-di-chi-tou-qian-xjof1/)