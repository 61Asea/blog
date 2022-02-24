/*
 * @lc app=leetcode.cn id=20 lang=java
 *
 * [20] 有效的括号
 *
 * https://leetcode-cn.com/problems/valid-parentheses/description/
 *
 * algorithms
 * Easy (44.55%)
 * Likes:    3003
 * Dislikes: 0
 * Total Accepted:    940.9K
 * Total Submissions: 2.1M
 * Testcase Example:  '"()"'
 *
 * 给定一个只包括 '('，')'，'{'，'}'，'['，']' 的字符串 s ，判断字符串是否有效。
 * 
 * 有效字符串需满足：
 * 
 * 
 * 左括号必须用相同类型的右括号闭合。
 * 左括号必须以正确的顺序闭合。
 * 
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：s = "()"
 * 输出：true
 * 
 * 
 * 示例 2：
 * 
 * 输入：s = "()[]{}"
 * 输出：true
 * 
 * 
 * 示例 3：
 * 
 * 输入：s = "(]"
 * 输出：false
 * 
 * 
 * 示例 4：
 * 
 * 输入：s = "([)]"
 * 输出：false
 * 
 * 
 * 示例 5：
 * 
 * 输入：s = "{[]}"
 * 输出：true
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= s.length <= 10^4
 * s 仅由括号 '()[]{}' 组成
 * 
 * 
 */

// @lc code=start
class Solution {
    static class MatchChar {
        private char c;

        MatchChar(char c) {
            this.c = c;
        }

        public boolean isNext(char next) {
            switch (next) {
                case ']' :
                    return c == '[';
                case '}' :
                    return c == '{';
                case ')':
                    return c == '(';
                default:
                    return false;
            }
        }
    }

    // 使用栈
    public boolean isValid(String s) {
        Stack<MatchChar> stack = new Stack<>();
        for (int i = 0; i < s.length(); i++) {
            if (!stack.isEmpty()) {
                if (stack.peek().isNext(s.charAt(i))) {
                    stack.pop();
                    continue;
                }
            } else {
                if (s.charAt(i) == '}' || s.charAt(i) == ']' || s.charAt(i) == ')') {
                    return false;
                }
            }
            stack.add(new MatchChar(s.charAt(i)));
        }
        return stack.isEmpty();
    }
}
// @lc code=end

