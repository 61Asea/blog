/*
 * @lc app=leetcode.cn id=92 lang=java
 *
 * [92] 反转链表 II
 *
 * https://leetcode-cn.com/problems/reverse-linked-list-ii/description/
 *
 * algorithms
 * Medium (55.14%)
 * Likes:    1166
 * Dislikes: 0
 * Total Accepted:    254.2K
 * Total Submissions: 460.9K
 * Testcase Example:  '[1,2,3,4,5]\n2\n4'
 *
 * 给你单链表的头指针 head 和两个整数 left 和 right ，其中 left <= right 。请你反转从位置 left 到位置 right
 * 的链表节点，返回 反转后的链表 。
 * 
 * 
 * 示例 1：
 * 
 * 输入：head = [1,2,3,4,5], left = 2, right = 4
 * 输出：[1,4,3,2,5]
 * 
 * 
 * 示例 2：
 * 
 * 输入：head = [5], left = 1, right = 1
 * 输出：[5]
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 链表中节点数目为 n
 * 1 <= n <= 500
 * -500 <= Node.val <= 500
 * 1 <= left <= right <= n
 * 
 * 
 * 
 * 
 * 进阶： 你可以使用一趟扫描完成反转吗？
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
    // 1. 迭代
    // public ListNode reverseBetween(ListNode head, int left, int right) {
    //     // [1, 2, 3, 4, 5], left = 2, right = 4, l = 1, ln = 1, current = 1, prev = null
    //     // counter = 1, temp = 2, l = 1, ln = 2, current = 2
    //     // counter = 2, temp = 3, 2.next = null, prev = 2 | [1, 2] , current = 3
    //     // counter = 3, temp = 4, 3.next = 2, prev = 3 | [1, 2], current = 4
    //     // counter = 4, temp = 5, 4.next = 3, prev = 4 | current = 5
    //     // counter = 5, 1.next = 4, 2.next = 5

    //     // [1, 2, 3] left = 2, right = 3, l = 1, ln = 1, current = 1, prev = null , result = 1
    //     // counter = 1, temp = 2, l = 1, ln = 2, current = 2
    //     // counter = 2, temp = 3, 2.next = null, prev = 2, current = 3
    //     // counter = 3, temp = null, 3.next = 2, prev = 3, current = null
    //     // counter ==

    //     // [1, 2, 3] left = 1, right = 2, l = 1, ln = 1, current = 1, prev = null ,result = 1
    //     // counter = 1 , temp = 2, 1.next = null, prev = 1, current = 2
    //     // counter = 2, temp = 3, 2.next = 1, prev = 2, current = 3
    //     // counter = 3, 1.next = 2, 1.next = 3

    //     ListNode l = head, ln = head, current = head, prev = null, result = l;
    //     int counter = 0;
    //     while (current != null) {
    //         counter += 1;

    //         ListNode temp = current.next;
    //         if (counter == right + 1) {
    //             l.next = prev;
    //             ln.next = current;
    //             break;
    //         }
            
    //         if (counter < left) {
    //             l = current;
    //             ln = current.next;
    //         } else {
    //             current.next = prev;
    //             prev = current;
    //         }
    //         current = temp;
    //     }

    //     if (counter != right + 1) {
    //         l.next = prev;
    //         ln.next = current;
    //     }

    //     return left > 1 ? result : prev;
    // }

    public ListNode reverseBetween(ListNode head, int left, int right) {
        ListNode dummy = new ListNode(-1);
        dummy.next = head;
        ListNode l = dummy;
        for (int i = 1; i < left; i++) {
            l = l.next;
        }

        ListNode current = l.next, prev = null, ln = current;
        for (int i = 0; i <= right - left; i++) {
            ListNode temp = current.next;
            current.next = prev;
            prev = current;
            current = temp;
        }
        l.next = prev;
        ln.next = current;
        return dummy.next;
    }
}
// @lc code=end

