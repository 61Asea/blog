import java.util.PriorityQueue;

/*
 * @lc app=leetcode.cn id=23 lang=java
 *
 * [23] 合并K个升序链表
 *
 * https://leetcode-cn.com/problems/merge-k-sorted-lists/description/
 *
 * algorithms
 * Hard (56.54%)
 * Likes:    1792
 * Dislikes: 0
 * Total Accepted:    407.2K
 * Total Submissions: 720.1K
 * Testcase Example:  '[[1,4,5],[1,3,4],[2,6]]'
 *
 * 给你一个链表数组，每个链表都已经按升序排列。
 * 
 * 请你将所有链表合并到一个升序链表中，返回合并后的链表。
 * 
 * 
 * 
 * 示例 1：
 * 
 * 输入：lists = [[1,4,5],[1,3,4],[2,6]]
 * 输出：[1,1,2,3,4,4,5,6]
 * 解释：链表数组如下：
 * [
 * ⁠ 1->4->5,
 * ⁠ 1->3->4,
 * ⁠ 2->6
 * ]
 * 将它们合并到一个有序链表中得到。
 * 1->1->2->3->4->4->5->6
 * 
 * 
 * 示例 2：
 * 
 * 输入：lists = []
 * 输出：[]
 * 
 * 
 * 示例 3：
 * 
 * 输入：lists = [[]]
 * 输出：[]
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * k == lists.length
 * 0 <= k <= 10^4
 * 0 <= lists[i].length <= 500
 * -10^4 <= lists[i][j] <= 10^4
 * lists[i] 按 升序 排列
 * lists[i].length 的总和不超过 10^4
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
     * 迭代
     * @param list1
     * @param list2
     * @return
     */
    private ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(-1), ptr = dummy;
        while (list1 != null && list2 != null) {
            if (list1.val > list2.val) {
                ptr.next = list2;
                list2 = list2.next;
            } else {
                ptr.next = list1;
                list1 = list1.next;
            }
            ptr = ptr.next;
        }
        ptr.next = list1 == null ? list2 : list1;
        return dummy.next;
    }

    /**
     * 递归
     * @param list1
     * @param list2
     * @return
     */
    // private ListNode mergeTwoLists(ListNode list1, ListNode list2) {
    //     if (list1 == null || list2 == null) {
    //         return list1 == null ? list2 : list1;
    //     }

    //     if (list1.val < list2.val) {
    //         list1.next = mergeTwoLists(list1.next, list2);
    //         return list1;
    //     } else {
    //         list2.next = mergeTwoLists(list1, list2.next);
    //         return list2;
    //     }
    // }

    /**
     * 1. 暴力枚举法
     * @param lists
     * @return
     */
    // public ListNode mergeKLists(ListNode[] lists) {
    //     ListNode res = null;
    //     for (ListNode list : lists) {
    //         res = mergeTwoLists(list, res);
    //     }
    //     return res;
    // }

    /**
     * 2. 迭代分治法
     * @param lists
     * @return
     */
    // public ListNode mergeKLists(ListNode[] lists) {
    //     if (lists == null || lists.length == 0) {
    //         return null;
    //     }

    //     while (lists.length > 1) {
    //         int iterateCount = (int) Math.ceil((double) lists.length / 2);
    //         ListNode[] listsTemp = new ListNode[iterateCount];
    //         for (int i = 0; i < iterateCount; i++) {
    //             int r = lists.length - i - 1;
    //             listsTemp[i] = mergeTwoLists(lists[i], i == r ? null : lists[r]);
    //         }
    //         lists = listsTemp;
    //     }
    //     return lists[0];
    // }

    /**
     * 3. 递归分治法（官方题解）
     * @param lists
     * @return
     */
    // public ListNode merge(ListNode[] lists, int l, int r) {
    //     if (l == r) {
    //         return lists[l];
    //     }

    //     if (l > r) {
    //         return null;
    //     }

    //     int mid = (l + r) >> 1;
    //     return mergeTwoLists(merge(lists, l, mid), merge(lists, mid + 1, r));
    // }

    // public ListNode mergeKLists(ListNode[] lists) {
    //     return merge(lists, 0, lists.length - 1);
    // }

    class Status implements Comparable<Status> {
        int val;
        ListNode ptr;

        Status(int val, ListNode ptr) {
            this.val = val;
            this.ptr = ptr;
        }

        @Override
        public int compareTo(Solution.Status o) {
            return this.val - o.val;
        }

    }

    PriorityQueue<Status> queue = new PriorityQueue<>();

    /**
     * 4. 最小堆
     * @param lists
     * @return
     */
    public ListNode mergeKLists(ListNode[] lists) {
        for (ListNode node : lists) {
            if (node != null) {
                queue.offer(new Status(node.val, node));
            }
        }

        ListNode dummy = new ListNode(-1);
        ListNode tail = dummy;
        while (!queue.isEmpty()) {
            Status f = queue.poll();
            tail.next = f.ptr;
            tail = tail.next;
            if (f.ptr.next != null) {
                queue.offer(new Status(f.ptr.next.val, f.ptr.next));
            }
        }
        return dummy.next;
    }
}
// @lc code=end

