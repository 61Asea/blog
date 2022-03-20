/*
 * @lc app=leetcode.cn id=215 lang=java
 *
 * [215] 数组中的第K个最大元素
 *
 * https://leetcode-cn.com/problems/kth-largest-element-in-an-array/description/
 *
 * algorithms
 * Medium (64.70%)
 * Likes:    1543
 * Dislikes: 0
 * Total Accepted:    564.9K
 * Total Submissions: 873.1K
 * Testcase Example:  '[3,2,1,5,6,4]\n2'
 *
 * 给定整数数组 nums 和整数 k，请返回数组中第 k 个最大的元素。
 * 
 * 请注意，你需要找的是数组排序后的第 k 个最大的元素，而不是第 k 个不同的元素。
 * 
 * 
 * 
 * 示例 1:
 * 
 * 输入: [3,2,1,5,6,4] 和 k = 2
 * 输出: 5
 * 
 * 
 * 示例 2:
 * 
 * 输入: [3,2,3,1,2,4,5,5,6] 和 k = 4
 * 输出: 4
 * 
 * 
 * 
 * 提示： 
 * 
 * 
 * 1 <= k <= nums.length <= 10^4
 * -10^4 <= nums[i] <= 10^4
 * 
 * 
 */

// @lc code=start
class Solution {
    private int position;

    public int findKthLargest(int[] nums, int k) {
        this.position = nums.length - k;
        findKthByPartition(nums, 0, nums.length - 1);
        return nums[position];
    }

    private void findKthByPartition(int[] nums, int low, int high) {
        int index = partition(nums, low, high);
        if (index == position) {
            return;
        }

        if (index > position) {
            findKthByPartition(nums, low, index - 1);
        } else {
            findKthByPartition(nums, index + 1, high);
        }
    }

    private int partition(int[] nums, int low, int high) {
        moveMiddleToLow(nums, low, high);
        int pivot = nums[low];
        while (low < high) {
            while (low < high && nums[high] >= pivot) {
                high--;
            }
            if (low < high) {
                nums[low] = nums[high];
            }
            while (low < high && nums[low] <= pivot) {
                low++;
            }
            if (low < high) {
                nums[high] = nums[low];
            }
        }
        nums[low] = pivot;
        return low;
    }

    private void moveMiddleToLow(int[] nums, int low, int high) {
        int mid = ((high - low) >> 1) + low;
        if (nums[low] > nums[mid]) {
            swap(nums, low, mid);
        }
        if (nums[high] > nums[mid]) {
            swap(nums, low, mid);
        }
        if (nums[high] < nums[mid] && nums[high] > nums[low]) {
            swap(nums, low, high);
        }
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}
// @lc code=end

