/*
 * @lc app=leetcode.cn id=4 lang=java
 *
 * [4] 寻找两个正序数组的中位数
 *
 * https://leetcode-cn.com/problems/median-of-two-sorted-arrays/description/
 *
 * algorithms
 * Hard (41.13%)
 * Likes:    4993
 * Dislikes: 0
 * Total Accepted:    610.5K
 * Total Submissions: 1.5M
 * Testcase Example:  '[1,3]\n[2]'
 *
 * 给定两个大小分别为 m 和 n 的正序（从小到大）数组 nums1 和 nums2。请你找出并返回这两个正序数组的 中位数 。
 * 
 * 算法的时间复杂度应该为 O(log (m+n)) 。
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：nums1 = [1,3], nums2 = [2]
 * 输出：2.00000
 * 解释：合并数组 = [1,2,3] ，中位数 2
 * 
 * 
 * 示例 2：
 * 
 * 输入：nums1 = [1,2], nums2 = [3,4]
 * 输出：2.50000
 * 解释：合并数组 = [1,2,3,4] ，中位数 (2 + 3) / 2 = 2.5
 * 
 * 
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * nums1.length == m
 * nums2.length == n
 * 0 <= m <= 1000
 * 0 <= n <= 1000
 * 1 <= m + n <= 2000
 * -10^6 <= nums1[i], nums2[i] <= 10^6
 * 
 * 
 */

// @lc code=start
class Solution {
    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
        // 1. 中位数归并排序
        int m = nums1.length, n = nums2.length, len = m + n;
        if (len == 0) {
            return 0.0d;
        }
        double left = 0, right = 0;
        int lStart = 0, rStart = 0;
        for (int i = 0; i <= len / 2; i++) {
            left = right;
            if (lStart < m && (rStart >= n || nums1[lStart] < nums2[rStart])) {
                right = nums1[lStart++];
            } else {
                right = nums2[rStart++];
            }
        }
        return len % 2 == 0 ? (left + right) / 2 : right;

        // 2. 二分法
//         int m = nums1.length, n = nums2.length, len = m + n;
//         if (len == 0) {
//             return 0.0d;
//         }

//         boolean isOdd = len % 2 != 0;
//         if (m == 0 || n == 0) {
// //            return nums1.length > 0 ? nums1[0] : nums2[0];
//             return nums1.length == 0 ? (isOdd ? nums2[len / 2] : (double) (nums2[len / 2] + nums2[len / 2 - 1]) / 2 ) : (isOdd ? nums1[len / 2 ] : (double) (nums1[len / 2] + nums1[len / 2 - 1]) / 2);
//         }

//         int lp = 0, rp = 0, middle = isOdd ? (len / 2 + 1) : len / 2;
//         // 3 / 2 = 1（取1）, 4 / 2 = 2（取1和2）, 5 / 2 = 2（取2）
//         for (int k = len / 2, halfK = k / 2; middle > 0; halfK = k / 2) {
//             int step = halfK > 0 ? halfK : 1;
//             k -= step;
//             middle -= step;
//             if (nums1[lp + halfK] < nums2[rp + halfK]) {
//                 if (middle == 0) {
//                     if (isOdd) {
//                         return nums1[lp];
//                     } else {
//                         if (lp + 1 > m - 1) {
//                             return (double) (nums1[lp] + nums2[rp + halfK]) / 2;
//                         } else {
//                             return (double) (nums1[lp] + (nums1[Math.min(lp + 1, m - 1)] > nums2[rp + halfK] ? nums2[rp + halfK] : nums1[Math.min(lp + 1, m - 1)])) / 2;
//                         }
//                     }
// //                    return isOdd ? nums1[lp] : (double) (nums1[lp] + (nums1[Math.min(lp + 1, m - 1)] > nums2[rp + halfK] ? nums2[rp + halfK] : nums1[Math.min(lp + 1, m - 1)])) / 2;
//                 }
//                 lp += step;
//             } else {
//                 if (middle == 0) {
//                     if (isOdd) {
//                         return nums2[rp];
//                     } else {
//                         if (rp + 1 > n - 1) {
//                             return (double) (nums2[rp] + nums1[lp + halfK]) / 2;
//                         } else {
//                             return (double) (nums2[rp] + (nums2[Math.min(rp + 1, n - 1)] > nums1[lp + halfK] ? nums1[lp + halfK] : nums2[Math.min(rp + 1, n - 1)])) / 2;
//                         }
//                     }
// //                    return isOdd ? nums2[rp] : (double) (nums2[rp] + (nums2[Math.min(rp + 1, n - 1)] > nums1[lp + halfK] ? nums1[lp + halfK] : nums2[Math.min(rp + 1, n - 1)])) / 2;
//                 }
//                 rp += step;
//             }
//         }
//         return 0.0;
    }
}
// @lc code=end

