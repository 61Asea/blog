/*
 * @lc app=leetcode.cn id=62 lang=java
 *
 * [62] 不同路径
 *
 * https://leetcode-cn.com/problems/unique-paths/description/
 *
 * algorithms
 * Medium (66.74%)
 * Likes:    1285
 * Dislikes: 0
 * Total Accepted:    374.8K
 * Total Submissions: 561.5K
 * Testcase Example:  '3\n7'
 *
 * 一个机器人位于一个 m x n 网格的左上角 （起始点在下图中标记为 “Start” ）。
 * 
 * 机器人每次只能向下或者向右移动一步。机器人试图达到网格的右下角（在下图中标记为 “Finish” ）。
 * 
 * 问总共有多少条不同的路径？
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：m = 3, n = 7
 * 输出：28
 * 
 * 示例 2：
 * 
 * 输入：m = 3, n = 2
 * 输出：3
 * 解释：
 * 从左上角开始，总共有 3 条路径可以到达右下角。
 * 1. 向右 -> 向下 -> 向下
 * 2. 向下 -> 向下 -> 向右
 * 3. 向下 -> 向右 -> 向下
 * 
 * 
 * 示例 3：
 * 
 * 输入：m = 7, n = 3
 * 输出：28
 * 
 * 
 * 示例 4：
 * 
 * 输入：m = 3, n = 3
 * 输出：6
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= m, n <= 100
 * 题目数据保证答案小于等于 2 * 10^9
 * 
 * 
 */

// @lc code=start
class Solution {
    /**
     * 1. 数组元素dp[i][j]定义：表示i * j大小的网格下，有dp[i][j]个符合的路径
     * 2. 数组元素之间的关系： dp[m][n] = dp[m - 1][n] + dp[m][n - 1];
     * 3. 初始化条件： 0 * 0 = 0, 0 * n = 0, m * 0 = 0, 1 * 1 = 0, 1 * 2 = 1， 2 * 1 = 1， 2 * 2 = 2，
     * @param m
     * @param n
     * @return
     */
    public int uniquePaths(int m, int n) {
        if (m == 0 || n == 0) {
            return 1;
        }

        int[][] dp = new int[m][n];
        for (int i = 0; i < m; i++) {
            dp[i][0] = 1;
        }
        for (int j = 0; j < n; j++) {
            dp[0][j] = 1;
        }
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
            }
        }
        return dp[m - 1][n - 1];
    }
}
// @lc code=end

