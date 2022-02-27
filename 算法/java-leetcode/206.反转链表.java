/*
 * @lc app=leetcode.cn id=206 lang=java
 *
 * [206] 反转链表
 *
 * https://leetcode-cn.com/problems/reverse-linked-list/description/
 *
 * algorithms
 * Easy (72.69%)
 * Likes:    2306
 * Dislikes: 0
 * Total Accepted:    887.4K
 * Total Submissions: 1.2M
 * Testcase Example:  '[1,2,3,4,5]'
 *
 * 给你单链表的头节点 head ，请你反转链表，并返回反转后的链表。
 * 
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：head = [1,2,3,4,5]
 * 输出：[5,4,3,2,1]
 * 
 * 
 * 示例 2：
 * 
 * 输入：head = [1,2]
 * 输出：[2,1]
 * 
 * 
 * 示例 3：
 * 
 * 输入：head = []
 * 输出：[]
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 链表中节点的数目范围是 [0, 5000]
 * -5000 <= Node.val <= 5000
 * 
 * 
 * 
 * 
 * 进阶：链表可以选用迭代或递归方式完成反转。你能否用两种方法解决这道题？
 * 
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
    /**
     * 1. 递归
     * 思路：递归函数返回最底层引用，其余的引用修改用其他指针替换
     * @param head
     * @return
     */
    // public ListNode reverseList(ListNode head) {
    //     if (head == null || head.next == null) {
    //         return head;
    //     }
    //     // [1, 2, 3]
    //     // newHead = 3, head = 2, head.next = 3, head.next.next = 3
    //     // 3.next = 2, 2.next = null

    //     // newHead = 3, head = 1, head.next = 2, 2.next = 1, 1.next = null
    //     ListNode newHead = reverseList(head.next);
    //     head.next.next = head;
    //     head.next = null;
    //     return newHead;
    // }

    /**
     * 2. 迭代
     * 思路：使用两个指针保留之前的值
     * @param head
     * @return
     */
    public ListNode reverseList(ListNode head) {
        ListNode prev = null, current = head;
        // temp = 1, 1.next = null, prev = 1, current = 
        while (current != null) {
            ListNode temp = current.next;
            current.next = prev;
            prev = current;
            current = temp;
        }
        return prev;
    }
}
// @lc code=end

