/*
 * @lc app=leetcode.cn id=121 lang=java
 *
 * [121] 买卖股票的最佳时机
 *
 * https://leetcode-cn.com/problems/best-time-to-buy-and-sell-stock/description/
 *
 * algorithms
 * Easy (57.53%)
 * Likes:    2176
 * Dislikes: 0
 * Total Accepted:    698K
 * Total Submissions: 1.2M
 * Testcase Example:  '[7,1,5,3,6,4]'
 *
 * 给定一个数组 prices ，它的第 i 个元素 prices[i] 表示一支给定股票第 i 天的价格。
 * 
 * 你只能选择 某一天 买入这只股票，并选择在 未来的某一个不同的日子 卖出该股票。设计一个算法来计算你所能获取的最大利润。
 * 
 * 返回你可以从这笔交易中获取的最大利润。如果你不能获取任何利润，返回 0 。
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：[7,1,5,3,6,4]
 * 输出：5
 * 解释：在第 2 天（股票价格 = 1）的时候买入，在第 5 天（股票价格 = 6）的时候卖出，最大利润 = 6-1 = 5 。
 * ⁠    注意利润不能是 7-1 = 6, 因为卖出价格需要大于买入价格；同时，你不能在买入前卖出股票。
 * 
 * 
 * 示例 2：
 * 
 * 输入：prices = [7,6,4,3,1]
 * 输出：0
 * 解释：在这种情况下, 没有交易完成, 所以最大利润为 0。
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= prices.length <= 10^5
 * 0 <= prices[i] <= 10^4
 * 
 * 
 */

// @lc code=start
class Solution {
    /**
     * 1. 贪心，历史最优
     * @param prices
     * @return
     */
    // public int maxProfit(int[] prices) {
    //     int minPrice = Integer.MAX_VALUE, maxProfit = 0, p;
    //     for (int i = 0; i < prices.length; i++) {
    //         if (prices[i] < minPrice) {
    //             minPrice = prices[i];
    //         } else if ((p = prices[i] - minPrice) > maxProfit) {
    //             maxProfit = p;
    //         }
    //     }
    //     return maxProfit;
    // }

    /**
     * 2. 动态规划
     * @param prices
     * @return
     */
    // public int maxProfit(int[] prices) {
    //     int m = prices.length;
    //     if (m < 2) {
    //         return 0;
    //     }

    //     // 前i天的最大利润值
    //     int[] dp = new int[m];
    //     dp[0] = 0;
    //     int minPrice = prices[0];
    //     for (int i = 1; i < m; i++) {
    //         if (prices[i] < minPrice) {
    //             minPrice = prices[i];
    //         }
    //         dp[i] = Math.max(dp[i - 1], prices[i] - minPrice);
    //     }
    //     return dp[m - 1];
    // }

    /**
     * 3. 动态规划
     */
    // public int maxProfit(int[] prices) {
    //     int len = prices.length;
    //     if (len < 2) {
    //         return 0;
    //     }
    //     int[] have = new int[len];  // 表示第i天持有股票所得最多现金
    //     int[] no = new int[len];    // 表示第i天不持有股票所得最多现金
    //     have[0] = -prices[0]; // 此时的持有股票就一定是买入股票了
    //     no[0] = 0;            // 不持有股票那么现金就是0

    //     for (int i = 1; i < len; i++) {
    //         have[i] = Math.max(have[i-1], -prices[i]);
    //         no[i] = Math.max(no[i-1], prices[i] + have[i-1]);
    //     }
    //     return no[len - 1];
    // }

}
// @lc code=end

