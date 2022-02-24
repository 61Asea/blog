/*
 * @lc app=leetcode.cn id=300 lang=java
 *
 * [300] 最长递增子序列
 *
 * https://leetcode-cn.com/problems/longest-increasing-subsequence/description/
 *
 * algorithms
 * Medium (52.66%)
 * Likes:    2240
 * Dislikes: 0
 * Total Accepted:    436.5K
 * Total Submissions: 828.9K
 * Testcase Example:  '[10,9,2,5,3,7,101,18]'
 *
 * 给你一个整数数组 nums ，找到其中最长严格递增子序列的长度。
 * 
 * 子序列 是由数组派生而来的序列，删除（或不删除）数组中的元素而不改变其余元素的顺序。例如，[3,6,2,7] 是数组 [0,3,1,6,2,2,7]
 * 的子序列。
 * 
 * 
 * 示例 1：
 * 
 * 输入：nums = [10,9,2,5,3,7,101,18]
 * 输出：4
 * 解释：最长递增子序列是 [2,3,7,101]，因此长度为 4 。
 * 
 * 
 * 示例 2：
 * 
 * 输入：nums = [0,1,0,3,2,3]
 * 输出：4
 * 
 * 
 * 示例 3：
 * 
 * 输入：nums = [7,7,7,7,7,7,7]
 * 输出：1
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= nums.length <= 2500
 * -10^4 <= nums[i] <= 10^4
 * 
 * 
 * 
 * 
 * 进阶：
 * 
 * 
 * 你能将算法的时间复杂度降低到 O(n log(n)) 吗?
 * 
 * 
 */

// @lc code=start
class Solution {
    /**
     * 错误
     * 1. 确定dp[i]的定义：当nums为i时，该数组的最长递增子序列长度
     * 2. 确定元素间关系：dp[i] = nums[i] > nums[i - 1] ? dp[i - 1] + 1 : dp[i - 1]
     * 3. 确定初始条件：dp[0] = 1
     * @param nums
     * @return
     */
    // public int lengthOfLIS(int[] nums) {
    //     int m = nums.length;
    //     if (m <= 1) {
    //         return m;
    //     }

    //     int[] dp = new int[m];
    //     dp[0] = 1;
    //     for (int i = 1; i < m; i++) {
    //         dp[i] = nums[i] >= nums[i - 1] ? dp[i - 1] + 1 : dp[i - 1];
    //     }
    //     if (dp[m - 1] == m && nums[0] == nums[m - 1]) {
    //         return 1;
    //     } else {
    //         return dp[m - 1];
    //     }
    // }

    // public int lengthOfLIS(int[] nums) {
    //     int m = nums.length;
    //     if (m <= 1) {
    //         return m;
    //     }

    //     int[] dp = new int[m];
    //     dp[0] = 1;
    //     for (int i = 1; i < m; i++) {
    //         int maxVal = 0;
    //         int maxPos = 0;
    //         for (int j = maxPos; j < i; j++) {
    //             if (nums[i] > maxVal) {
    //                 maxVal = nums[i];
    //                 maxPos = j;
    //                 dp[i] = dp[i - 1] + 1;
    //             } else {
    //                 dp[i] = dp[i - 1];
    //             }
    //         }
    //     }
    //     return dp[m - 1];
    // }

    public int lengthOfLIS(int[] nums) {
        int m = nums.length;
        if (m <= 1) {
            return m;
        }

        int[] dp = new int[m];
        dp[0] = 1;
        int max = 1;
        for (int i = 1; i < m; i++) {
            dp[i] = 1;
            for (int j = 0; j < i; j++) {
                if (nums[i] > nums[j]) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
//                    dp[i] = dp[j] + 1;
                }
            }
            max = Math.max(max, dp[i]);
//            dp[i] = nums[i] >= nums[i - 1] ? dp[i - 1] + 1 : dp[i - 1];
        }
        return max;
    }
}
// @lc code=end

