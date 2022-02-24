import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/*
 * @lc app=leetcode.cn id=3 lang=java
 *
 * [3] 无重复字符的最长子串
 *
 * https://leetcode-cn.com/problems/longest-substring-without-repeating-characters/description/
 *
 * algorithms
 * Medium (38.47%)
 * Likes:    6914
 * Dislikes: 0
 * Total Accepted:    1.5M
 * Total Submissions: 3.9M
 * Testcase Example:  '"abcabcbb"'
 *
 * 给定一个字符串 s ，请你找出其中不含有重复字符的 最长子串 的长度。
 * 
 * 
 * 
 * 示例 1:
 * 
 * 输入: s = "abcabcbb"
 * 输出: 3 
 * 解释: 因为无重复字符的最长子串是 "abc"，所以其长度为 3。
 * 
 * 
 * 示例 2:
 * 
 * 输入: s = "bbbbb"
 * 输出: 1
 * 解释: 因为无重复字符的最长子串是 "b"，所以其长度为 1。
 * 
 * 
 * 示例 3:
 * 
 * 输入: s = "pwwkew"
 * 输出: 3
 * 解释: 因为无重复字符的最长子串是 "wke"，所以其长度为 3。
 * 请注意，你的答案必须是 子串 的长度，"pwke" 是一个子序列，不是子串。
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 0 <= s.length <= 5 * 10^4
 * s 由英文字母、数字、符号和空格组成
 * 
 * 
 */

// @lc code=start
// 滑动窗口思想：right一直往右挪动至底部，然后触发left的更新，以减少每一个迭代的容量
class Solution {
    public int lengthOfLongestSubstring(String s) {
        // 1. HashSet
        // int length = s.length(), maxLength = 0;
        // HashSet<Character> hashSet = new HashSet<>();
        // for (int left = 0, right = 0; left < length; left ++) {
        //     while (right < length && !hashSet.contains(s.charAt(right))) {
        //         hashSet.add(s.charAt(right));
        //         right++;
        //     }
        //     maxLength = Math.max(maxLength, right - left);
        //     if (right == length) {
        //         break;
        //     }
        //     hashSet.remove(s.charAt(left));
        // }
        // return maxLength;

        // 2. HashMap
        int length = s.length(), maxLength = 0;
        Map<Character, Integer> hashMap = new HashMap<>();
        for (int left = 0, right = 0; left < length;) {
            while (right < length && !hashMap.containsKey(s.charAt(right))) {
                hashMap.put(s.charAt(right), right);
                right++;
            }
            maxLength = Math.max(maxLength, right - left);
            if (right == length) {
                break;
            }
            int newLeft = hashMap.get(s.charAt(right)) + 1;
            for (int i = left; i < newLeft; i++) {
                hashMap.remove(s.charAt(i));
            }
            left = newLeft;
        }
        return maxLength;
    }
}
// @lc code=end

