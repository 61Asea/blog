/*
 * @lc app=leetcode.cn id=82 lang=java
 *
 * [82] 删除排序链表中的重复元素 II
 *
 * https://leetcode-cn.com/problems/remove-duplicates-from-sorted-list-ii/description/
 *
 * algorithms
 * Medium (53.26%)
 * Likes:    848
 * Dislikes: 0
 * Total Accepted:    235.8K
 * Total Submissions: 442.4K
 * Testcase Example:  '[1,2,3,3,4,4,5]'
 *
 * 给定一个已排序的链表的头 head ， 删除原始链表中所有重复数字的节点，只留下不同的数字 。返回 已排序的链表 。
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：head = [1,2,3,3,4,4,5]
 * 输出：[1,2,5]
 * 
 * 
 * 示例 2：
 * 
 * 输入：head = [1,1,1,2,3]
 * 输出：[2,3]
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 链表中节点数目在范围 [0, 300] 内
 * -100 <= Node.val <= 100
 * 题目数据保证链表已经按升序 排列
 * 
 * 
 */

// @lc code=start
/**
 * Definition for singly-linked list.
 * public class ListNode {
 *     int val;
 *     ListNode next;
 *     ListNode() {}
 *     ListNode(int val) { this.val = val; }
 *     ListNode(int val, ListNode next) { this.val = val; this.next = next; }
 * }
 */
class Solution {
    // [1, 1, 1]
    // val = 1A, [-1, 1C]
    // 1A == 1C, val = 1C, [-1, null]
    public ListNode deleteDuplicates(ListNode head) {
        ListNode dummy = new ListNode(-1, head);
        ListNode curr = dummy;
        int val = dummy.val;
        while (curr.next != null && curr.next.next != null) {
            if (curr.next.val == curr.next.next.val) {
                val = curr.next.val;
                curr.next = curr.next.next.next;
            } else if (val == curr.next.val) {
                val = curr.next.val;
                curr.next = curr.next.next;
            } else {
                curr = curr.next;
            }
        }
        return dummy.next;
    }
}
// @lc code=end

