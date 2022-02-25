/*
 * @lc app=leetcode.cn id=5 lang=java
 *
 * [5] 最长回文子串
 *
 * https://leetcode-cn.com/problems/longest-palindromic-substring/description/
 *
 * algorithms
 * Medium (36.17%)
 * Likes:    4709
 * Dislikes: 0
 * Total Accepted:    877K
 * Total Submissions: 2.4M
 * Testcase Example:  '"babad"'
 *
 * 给你一个字符串 s，找到 s 中最长的回文子串。
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：s = "babad"
 * 输出："bab"
 * 解释："aba" 同样是符合题意的答案。
 * 
 * 
 * 示例 2：
 * 
 * 输入：s = "cbbd"
 * 输出："bb"
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= s.length <= 1000
 * s 仅由数字和英文字母组成
 * 
 * 
 */

// @lc code=start
class Solution {
    /**
     * 1. 元素定义：以i结尾的子字符串的最长回文子串
     * 2. 元素间关系：
     * @param s
     * @return
     */
    // public String longestPalindrome(String s) {
        // int m = s.length();
        // if (m <= 1) {
        //     return s;
        // }

        // int[] dp = new int[m];
        // String maxStr = s.substring(0, 1);
        // dp[0] = 1;
        // dp[1] = s.charAt(0) == s.charAt(1) ? 2 : 0;
        // for (int i = 2; i < m; i++) {
        //     for (int j = 0; j < i; j++) {
        //         // if (dp[j - 1] > 0) {
        //         //     // 回文
        //         //     s.charAt(j) == charAt(i - 2) && 
        //         // } else {

        //         // }
        //     }
        // }    
    // }

    /**
     * 1. dp[i][j]：从i到j的子串回文大小
     * 2. 关系：dp[i][j] = dp[i + 1][j - 1] > 0 ? (s.charAt(i) == s.charAt(j) ? dp[i + 1][j - 1] + 1 : 0) : 0 
     * 3. 初始条件：
     * @param s
     * @return
     */
    // public String longestPalindrome(String s) {
        // int m = s.length();
        // if (m <= 1) {
        //     return s;
        // }

        // int[][] dp = new int[m][m];
        // dp[0][0] = 1;
        // int max = dp[0][0], maxI = 0, maxJ = 0;
        // for (int i = 0; i < m; i++) {
        //     for (int j = 1; j < i; j++) {
        //         if (dp[i + 1][j - 1] > 0) {
        //             dp[i][j] = s.charAt(i) == s.charAt(j) ? dp[i + 1][j - 1] + 1 : 0;
        //         } else {
        //             dp[i][j] = 0;
        //         }

        //         if (max < dp[i][j]) {
        //             max = dp[i][j];
        //             maxI = i;
        //             maxJ = j;
        //         }
        //     }
        // }
        // return s.substring(maxI, maxJ);
    // }

    /**
     * 1. 定义：代表从i到j的子串是否回文及其回文长度
     * 2. 关系：参考题解转换公式：dp[i + 1][j - 1]若是回文，则在s的i和j位置字符相等时，dp[i][j]也是回文字符串
     * 3. 初始条件：j == j和j - 1 == j都是回文
     * @param s
     * @return
     */
    public String longestPalindrome(String s) {
        int m = s.length();
        // if (m <= 1) {
        //     return m;
        // }
        int[][] dp = new int[m][m];
        int max = 0, maxLeft = 0, maxRight = 0;
        for (int j = 0; j < m; j++) {
            dp[j][j] = 1;
            if (j > 0 && s.charAt(j - 1) == s.charAt(j)) {
                dp[j - 1][j] = 2;
                if (max <= 2) {
                    max = 2;
                    maxLeft = j - 1;
                    maxRight = j;
                }
            } else {
                if (max <= 1) {
                    max = 1;
                    maxLeft = maxRight = j;
                }
            }


            for (int i = j - 2; i >= 0; i--) {
                if (dp[i + 1][j - 1] > 0 && s.charAt(i) == s.charAt(j)) {
                    int prev = dp[i + 1][j - 1] + 2;
                    dp[i][j] = prev;

                    if (max < prev) {
                        max = prev;
                        maxLeft = i;
                        maxRight = j;
                    }
                }
            }
        }
        return s.substring(maxLeft, maxRight + 1);
    }
}
// @lc code=end

