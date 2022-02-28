/*
 * @lc app=leetcode.cn id=21 lang=java
 *
 * [21] 合并两个有序链表
 *
 * https://leetcode-cn.com/problems/merge-two-sorted-lists/description/
 *
 * algorithms
 * Easy (66.69%)
 * Likes:    2212
 * Dislikes: 0
 * Total Accepted:    876.8K
 * Total Submissions: 1.3M
 * Testcase Example:  '[1,2,4]\n[1,3,4]'
 *
 * 将两个升序链表合并为一个新的 升序 链表并返回。新链表是通过拼接给定的两个链表的所有节点组成的。 
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：l1 = [1,2,4], l2 = [1,3,4]
 * 输出：[1,1,2,3,4,4]
 * 
 * 
 * 示例 2：
 * 
 * 输入：l1 = [], l2 = []
 * 输出：[]
 * 
 * 
 * 示例 3：
 * 
 * 输入：l1 = [], l2 = [0]
 * 输出：[0]
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 两个链表的节点数目范围是 [0, 50]
 * -100 <= Node.val <= 100
 * l1 和 l2 均按 非递减顺序 排列
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
     * 1. 迭代：||和&&
     * @param list1
     * @param list2
     * @return
     */
    // public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
    //     ListNode result = new ListNode(-1), temp = result;
    //     // [1, 2, 4] [1, 3, 4], result = -1, temp = -1
    //     // node1 = 1, node2 = 1, ok = true, temp = [-1, 1], list1 = 2, list2 = 1
    //     // node1 = 2, node2 = 1, ok = false, temp = [-1, 1, 1], list1= 2, list2 = 3
    //     // node1 = 2, node2 = 3, ok = true, temp = [-1, 1, 1, 2], list1= 4, list2 = 3
    //     // node1 = 4, node2 = 3, ok = false, temp = [-1, 1, 1, 2, 3], list1 = 4, list2 = 4
    //     // node1 = 4, node2 = 4, ok = true, temp = [-1, 1, 1, 2, 3, 4], list1 = null, list2 = 4
    //     // node1 = null, node2 = 4, temp = [-1, 1, 1, 2, 3, 4, 4]
        
    //     while (list1 != null || list2 != null) {
    //         ListNode node1 = list1 == null ? null : list1;
    //         ListNode node2 = list2 == null ? null : list2;
    //         if (node1 == null || node2 == null) {
    //             temp.next = node1 == null ? node2 : node1;
    //             break;
    //         }

    //         if (node1.val <= node2.val) {
    //             list1 = list1.next;
    //             temp.next = node1;
    //         } else {
    //             list2 = list2.next;
    //             temp.next = node2;
    //         }
    //         temp = temp.next;
    //     }
    //     return result.next;

    //     // &&
    //     // while (list1 != null && list2 != null) {
    //     //     if (list1.val <= list2.val) {
    //     //         temp.next = list1;
    //     //         list1 = list1.next;
    //     //     } else {
    //     //         temp.next = list2;
    //     //         list2 = list2.next;
    //     //     }
    //     //     temp = temp.next;
    //     // }

    //     // temp.next = list1 == null ? list2 : list1;
    //     // return result.next;
    // }

    /**
     * 2. 递归
     * 与迭代思路相似，最终都要把不为null的部分直接拼接上去
     * - 迭代：有结果的引用，结果的next指针指向不为null的部分
     * - 递归：没有结果的引用，拥有的是不为null部分的头指针，需要在上一层持有结果队尾引用，将其next指针指向不为null的部分
     * @param list1
     * @param list2
     * @return
     */
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        // [1, 2, 4] [1, 3, 4], result = -1, temp = -1
        // list1 = 1, list2 = 1, mergeTwoLists(1, 3), prev = 2, 1.next = 2, return 1
        // list1 = 1, list2 = 3, mergeTwoLists(2, 3), prev = 3, 2.next = 3, return 2
        // list1 = 2, list2 = 3, mergeTwoLists(4A, 3), prev = 4A, 3.next = 4A, return 3
        // list1 = 4, list2 = 3, mergeTwoLists(4A, 4B), prev = 4B, 4A.next = 4B, return 4A
        // list1 = 4, list2 = 4, mergeTwoLists(null, 4B), prev = null, 4B.next = null, return 4B
        // list1 = null, list2 = 4B, return null;

        // [1, 2] [1, 3]

        // list1 = 1A, list2 = 1B, mergeTwoLists(1A, 3)
        // list1 = 1A, list2 = 3, mergeTwoLists(1A, null)
        // list1 = 1A, list2 = null, mergeTwoLists()

        // if (list1 == null || list2 == null) {
        //     return list1 == null ? null : list2;
        // }

        // ListNode prev;
        // if (list1.val <= list2.val) {
        //     prev = mergeTwoLists(list1, list2.next);
        //     list1.next = prev;
        //     return list1;
        // } else {
        //     prev = mergeTwoLists(list1.next, list2);
        //     list2.next = prev;
        //     return list2;
        // }

        // if (list1 == null) {
        //     return list1;
        // } else if (list2 == null) {
        //     return list2;
        // }

        if (list1 == null || list2 == null) {
            return list1 == null ? list2 : list1;
        }
        
        if (list1.val <= list2.val) {
            list1.next = mergeTwoLists(list1.next, list2);
            return list1;
        } else {
            list2.next = mergeTwoLists(list1, list2.next);
            return list2;
        }
    }
}
// @lc code=end

