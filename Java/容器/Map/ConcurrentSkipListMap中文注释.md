/*

/*

* This class implements a tree-like two-dimensionally linked skip list in which the index levels are represented in separate nodes from the base nodes holding data. There are two reasons for taking this approach instead of the usual array-based structure: 1) Array based implementations seem to encounter more complexity and overhead 2) We can use cheaper algorithms for the heavily-traversed index lists than can be used for the base lists. Here's a picture of some of the basics for a possible list with 2 levels of index:

*此类实现了一个类似树的二维链接跳过列表，其中索引级别在不同的节点中表示，这些节点与包含数据的基本节点不同。采用这种方法而不是通常的基于数组的结构有两个原因：1）基于数组的实现似乎会遇到更大的复杂性和开销2）我们可以对大量遍历的索引列表使用比基列表更便宜的算法。以下是一个可能包含两个索引级别的列表的一些基本信息：

*

*

* Head nodes Index nodes

*头部节点索引节点

* +-+ right +-+ +-+

*+-+右+-++-+

* |2|---------------->| |--------------------->| |->null

*| 2 |----------->| |---------------->| |->空

* +-+ +-+ +-+

* +-+ +-+ +-+

* | down | |

*|向下||

* v v v

*v v v v

* +-+ +-+ +-+ +-+ +-+ +-+

* +-+ +-+ +-+ +-+ +-+ +-+

* |1|----------->| |->| |------>| |----------->| |------>| |->null

*| 1 |------------->| |->| |-->| |------------->| |-->| |->空

* +-+ +-+ +-+ +-+ +-+ +-+

* +-+ +-+ +-+ +-+ +-+ +-+

* v | | | | |

*五| | | ||

* Nodes next v v v v v

*下一个节点

* +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+

* +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+

* | |->|A|->|B|->|C|->|D|->|E|->|F|->|G|->|H|->|I|->|J|->|K|->null

*| |->| A |->| B |->| C |->| D |->| E |->| F |->| G |->| H |->| I |->| J |->| K |->空

* +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+

* +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+ +-+

*

*

* The base lists use a variant of the HM linked ordered set algorithm. See Tim Harris, "A pragmatic implementation of non-blocking linked lists" http://www.cl.cam.ac.uk/~tlh20/publications.html and Maged Michael "High Performance Dynamic Lock-Free Hash Tables and List-Based Sets" http://www.research.ibm.com/people/m/michael/pubs.htm. The basic idea in these lists is to mark the "next" pointers of deleted nodes when deleting to avoid conflicts with concurrent insertions, and when traversing to keep track of triples (predecessor, node, successor) in order to detect when and how to unlink these deleted nodes.

*基表使用HM链接有序集算法的一个变体。参见Tim Harris，“非阻塞链表的实用实现”http://www.cl.cam.ac.uk/~tlh20/publications.html 和Maged Michael“高性能动态无锁哈希表和基于列表的集”http://www.research.ibm.com/people/m/michael/pubs.htm. 这些列表中的基本思想是在删除时标记已删除节点的“下一个”指针，以避免与并发插入发生冲突；在遍历时，标记已删除节点的“下一个”指针，以跟踪三元组（前置节点、节点、后继节点），以检测何时以及如何取消这些已删除节点的链接。

*

*

* Rather than using mark-bits to mark list deletions (which can be slow and space-intensive using AtomicMarkedReference), nodes use direct CAS'able next pointers. On deletion, instead of marking a pointer, they splice in another node that can be thought of as standing for a marked pointer (indicating this by using otherwise impossible field values). Using plain nodes acts roughly like "boxed" implementations of marked pointers, but uses new nodes only when nodes are deleted, not for every link. This requires less space and supports faster traversal. Even if marked references were better supported by JVMs, traversal using this technique might still be faster because any search need only read ahead one more node than otherwise required (to check for trailing marker) rather than unmasking mark bits or whatever on each read.

*节点不使用标记位来标记列表删除（使用AtomicMarkedReference可能会很慢并且占用大量空间），而是使用直接的CAS'able下一个指针。删除时，它们不是标记指针，而是拼接到另一个节点中，该节点可以被认为代表标记的指针（通过使用其他不可能的字段值来表示这一点）。使用普通节点的行为与标记指针的“装箱”实现大致相同，但仅在删除节点时才使用新节点，而不是针对每个链接。这需要更少的空间并支持更快的遍历。即使jvm更好地支持标记的引用，使用这种技术的遍历也可能更快，因为任何搜索只需提前读取一个节点，而不是每次读取时取消标记位或其他任何内容。

In addition to using deletion markers, the lists also use nullness of value fields to indicate deletion, in a style similar to typical lazy-deletion schemes. If a node's value is null, then it is considered logically deleted and ignored even though it is still reachable. This maintains proper control of concurrent replace vs delete operations -- an attempted replace must fail if a delete beat it by nulling field, and a delete must return the last non-null value held in the field. (Note: Null, rather than some special marker, is used for value fields here because it just so happens to mesh with the Map API requirement that method get returns null if there is no mapping, which allows nodes to remain concurrently readable even when deleted. Using any other marker value here would be messy at best.)

除了使用删除标记，列表还使用值字段的空值来表示删除，其样式类似于典型的延迟删除方案。如果一个节点的值为null，那么它被认为是逻辑删除和忽略的，即使它仍然是可访问的。这保持了对并发replace和delete操作的适当控制——如果delete通过置零字段击败它，那么尝试的替换必须失败，delete必须返回字段中最后一个非null值(注意：Null，而不是一些特殊的标记，用于这里的值字段，因为它恰好符合mapapi的要求，即如果没有映射，get方法将返回Null，这允许节点即使被删除也保持并发可读性。在这里使用任何其他标记值最多也会很混乱。）

*

*

Here's the sequence of events for a deletion of node n with predecessor b and successor f, initially:

以下是删除节点n和前一个b和后一个f的事件序列，最初是：

*

*

* +------+ +------+ +------+

* +------+ +------+ +------+

* ... | b |------>| n |----->| f | ...

* ... | b |------>| n |------>| f |。。。

* +------+ +------+ +------+

* +------+ +------+ +------+

*

*

* 1. CAS n's value field from non-null to null. From this point on, no public operations encountering the node consider this mapping to exist. However, other ongoing insertions and deletions might still modify n's next pointer.

*1.CAS n的值字段从非空变为空。从这一点开始，遇到节点的任何公共操作都不会认为此映射存在。但是，其他正在进行的插入和删除操作仍可能修改n的下一个指针。

*

*

* 2. CAS n's next pointer to point to a new marker node. From this point on, no other nodes can be appended to n. which avoids deletion errors in CAS-based linked lists.

*2.CAS n指向新标记节点的下一个指针。从这一点开始，没有其他节点可以附加到n。从而避免了基于CAS的链表中的删除错误。

*

*

* +------+ +------+ +------+ +------+

* +------+ +------+ +------+ +------+

* ... | b |------>| n |----->|marker|------>| f | ...

* ... | b |------>| n |------>|标记|------>| f |。。。

* +------+ +------+ +------+ +------+

* +------+ +------+ +------+ +------+

*

*

* 3. CAS b's next pointer over both n and its marker. From this point on, no new traversals will encounter n, and it can eventually be GCed.

*3.CAS b的下一个指针同时指向n及其标记。从这一点开始，没有新的遍历将遇到n，它最终可以被GCed。

* +------+ +------+

* +------+ +------+

* ... | b |----------------------------------->| f | ...

* ... | b |------------------------------->| f |。。。

* +------+ +------+

* +------+ +------+

*

*

A failure at step 1 leads to simple retry due to a lost race with another operation. Steps 2-3 can fail because some other thread noticed during a traversal a node with null value and helped out by marking and/or unlinking. This helping-out ensures that no thread can become stuck waiting for progress of the deleting thread. The use of marker nodes slightly complicates helping-out code because traversals must track consistent reads of up to four nodes (b, n, marker, f), not just (b, n, f), although the next field of a marker is immutable, and once a next field is CAS'ed to point to a marker, it never again changes, so this requires less care.

由于与另一个操作的竞争失败，步骤1中的失败将导致简单的重试。步骤2-3可能会失败，因为其他线程在遍历过程中注意到一个具有空值的节点，并通过标记和/或取消链接来帮助完成。这种帮助确保了不会有线程在等待删除线程的过程中被卡住。标记节点的使用使帮助代码变得稍微复杂，因为遍历必须跟踪最多四个节点（b，n，marker，f）的一致读取，而不仅仅是（b，n，f），尽管标记的下一个字段是不可变的，并且一旦下一个字段被选为指向标记，它就再也不会更改，所以这需要更少的注意。

*

*

Skip lists add indexing to this scheme, so that the base-level traversals start close to the locations being found, inserted or deleted -- usually base level traversals only traverse a few nodes. This doesn't change the basic algorithm except for the need to make sure base traversals start at predecessors (here, b) that are not (structurally) deleted, otherwise retrying after processing the deletion.

跳过列表将索引添加到此方案中，以便基本级别遍历开始时靠近要查找、插入或删除的位置—通常基本级别遍历只遍历少数节点。这不会改变基本算法，只是需要确保基本遍历从没有（结构上）删除的前置（这里是b）开始，否则在处理删除之后重试。

*

*

Index levels are maintained as lists with volatile next fields, using CAS to link and unlink. Races are allowed in index-list operations that can (rarely) fail to link in a new index node or delete one. (We can't do this of course for data nodes.) However, even when this happens, the index lists remain sorted, so correctly serve as indices. This can impact performance, but since skip lists are probabilistic anyway, the net result is that under contention, the effective "p" value may be lower than its nominal value. And race windows are kept small enough that in practice these failures are rare, even under a lot of contention.

索引级别保持为带有可变的next字段的列表，使用CAS链接和取消链接。索引列表操作中允许竞争，这些操作可能（很少）无法链接到新的索引节点或删除一个索引节点(当然，我们不能对数据节点这样做。）但是，即使发生这种情况，索引列表仍然保持排序，因此可以正确地用作索引。这可能会影响性能，但由于跳过列表无论如何都是概率的，因此最终结果是在争用情况下，有效的“p”值可能低于其标称值。而且竞争窗口保持得足够小，以至于在实践中，即使在很多争用情况下，这些失败也很少见。

*

*

The fact that retries (for both base and index lists) are relatively cheap due to indexing allows some minor simplifications of retry logic. Traversal restarts are performed after most "helping-out" CASes. This isn't always strictly necessary, but the implicit backoffs tend to help reduce other downstream failed CAS's enough to outweigh restart cost. This worsens the worst case, but seems to improve even highly contended cases.

由于索引的缘故，重试（对于基列表和索引列表）的成本相对较低，这一事实允许对重试逻辑进行一些小的简化。遍历重启是在大多数“帮助”案例之后执行的。这并不总是绝对必要的，但是隐含的退避往往有助于减少其他下游失败的CA，足以超过重启成本。这使最坏的情况更加恶化，但似乎改善了即使是高度竞争的情况。