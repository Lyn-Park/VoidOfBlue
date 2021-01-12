package net.vob.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Trees are a non-empty collection of <i>nodes</i>, each with at maximum one
 * <i>parent</i> node and any number of <i>child</i> nodes. Any class that implements
 * this interface has complete control over how these nodes are implemented, what they
 * represent, how they can be traversed, any limitations on their usage, and whether
 * they are sorted.<p>
 * 
 * This interface provides a number of method signatures for basic traversal and
 * iteration over the elements of the tree; notably, traversing up/down the tree will
 * return another tree, with either the parent or the child node as the new tree's
 * root node.<p>
 * 
 * Trees may contain duplicate elements, as enforced by the
 * {@link Object#equals(Object) equals(..)} method. It is possible for users of this
 * class to prohibit duplicates, however.<p>
 * 
 * A tree can be traversed, in various ways. Such traversals are known as <i>walks</i>,
 * and each one has its own associated iterator that performs the walk. All trees must
 * support at least five types of walking - <i>breadth-first</i>, <i>pre-order</i>, and
 * <i>post-order</i> are the standard ones, and they should have their standard meaning
 * and methods of walking as described
 * <a href="https://en.wikipedia.org/wiki/Tree_traversal">here</a>. Trees must also
 * support the ability to iterate through only the direct children of this tree node,
 * as well  as the ability to iterate from child to parent repeatedly up the tree until
 * the root node is located. Both of these have been termed here the <i>child-like
 * walk</i> and the <i>ancestral walk</i>, respectively. Neither of these non-standard
 * walks are expected to visit all nodes in the tree.<p>
 * 
 * <b>Note:</b>
 * This interface was created and expanded upon based on 2 invariants regarding tree
 * structures:
 * <ul>
 *  <li>A collection of nodes is a tree <i>if-and-only-if</i> the collection has a
 * dedicated <i>root node</i>.</li>
 *  <li>All tree nodes have a single associated <i>value</i>.</li>
 * </ul>
 * These 2 invariants together mean that 'empty' trees do not exist, as they have no
 * nodes and thus no root node; the closest to an empty tree that can be built
 * contains precisely 1 value for the root node.
 * 
 * @param <E> the type of elements this {@code Tree} contains
 * @param <T> the type of the implementing class itself
 */
public interface Tree<E, T extends Tree<E, ? extends T>> {
    /**
     * Gets the size of the tree. This is defined as the total number of nodes that are,
     * either directly or indirectly, descendants of this node (including this node
     * itself).
     * 
     * @return the size of the tree
     */
    int size();
    
    /**
     * Checks if the tree contains the given object. In general this method should
     * search the entire tree for the given value, and return {@code true} if the value
     * is found at any point. More formally, this should return {@code true} if and
     * only if this tree contains at least one node {@code n} such that
     * {@code o==null ? n.getValue()==null : o.equals(n.getValue())}.
     * 
     * @param o the object to search for
     * @return {@code true} if the tree contains the value, {@code false} otherwise
     */
    boolean contains(Object o);
    
    /**
     * Checks if the given tree is present as part of the descendants of this node. In
     * general this method should only return {@code true} if all nodes in the given
     * tree match, in both relative position and value, against any sub-tree within this
     * tree.<p>
     * 
     * Implementations of this method should satisfy the following axioms:
     * <ul>
     *  <li><i>Reflexivity</i>: {@code tree.containsSubTree(tree)} is always 
     * {@code true}</li>
     *  <li><i>Transitivity</i>: If {@code treeA.containsSubTree(treeB)} and 
     * {@code treeB.containsSubTree(treeC)}, then {@code treeA.containsSubTree(treeC)}
     *  <li><i>Antisymmetry</i>: {@code treeA.containsSubTree(treeB)} and
     * {@code treeB.containsSubTree(treeA)}, if-and-only-if {@code treeA.equals(treeB)}</li>
     *  <li><i>Consistency</i>: Unless a structural modification occurs somewhere within
     * either tree or their descendants, all subsequent invocations of
     * {@code treeA.containsSubTree(treeB)} should return the same result.
     * </ul>
     * Thus, this method provides a <i>partial ordering</i> of tree objects.
     * 
     * @param tree the tree to search for
     * @return {@code true} if this tree contains the given tree, {@code false}
     * otherwise
     * @throws NullPointerException if the specified tree is null
     */
    boolean containsSubTree(T tree);
    
    /**
     * Checks if the given collection of values is present as part of the tree. This
     * should conform to the behaviour of {@link contains(Object)}, but may employ a 
     * different, more efficient search algorithm for bulk operations.
     * 
     * @param values the values to search for
     * @return {@code true} if this tree contains all the given values, {@code false}
     * otherwise
     * @throws NullPointerException if the specified collection is null
     */
    boolean containsAll(Collection<?> values);
    
    /**
     * Checks if this node is in fact a root node, i.e. that this tree is not part of
     * some larger parent tree.
     * 
     * @return {@code true} if this node has no parent, {@code false} otherwise
     */
    boolean isRoot();
    
    /**
     * Checks if this node is a leaf node, i.e. that this node has no children, and
     * thus the tree consists of only this node.
     * 
     * @return {@code true} if this node has no children, {@code false} otherwise
     */
    boolean isLeaf();
    
    /**
     * Gets the value contained in this node.
     * @return the value of this node
     */
    E getValue();
    
    /**
     * Sets the value contained in this node of this tree.
     * @param value the new value of this node
     */
    void setValue(E value);
    
    /**
     * Adds a child to this node. The given value is the value of the new child node.
     * An implementation of this method must <i>also</i> handle setting the parent
     * of the new child node.<p>
     * 
     * Trees that support this operation may place limitations on what elements may
     * be added to this tree. In particular, some trees will refuse to add {@code null}
     * elements, and others will impose restrictions on the type of elements that may
     * be added.<p>
     *
     * If a tree refuses to add a particular element for any exceptional reason (e.g.
     * attempting to add a duplicate value to a tree that disallows duplicates is
     * <i>not</i> exceptional, but attempting to add an invalid type of value <i>is</i>
     * exceptional), it <i>must</i> throw an exception (rather than returning
     * {@code false}). This preserves the invariant that a tree always contains the
     * specified element after this call returns.<p>
     * 
     * Trees that support this operation may add elements at any location within the
     * tree, and can even alter other parts of the tree's structure; the only
     * invariant this method must preserve is that the element will always be present
     * <i>somewhere</i> within the tree immediately after returning. For example,
     * sorted trees may insert the new value and then restructure the tree to abide by
     * the imposed ordering.<p>
     * 
     * Tree classes should clearly specify the behaviour of this method within its
     * documentation, including any limitations on the elements that can be added to the
     * tree, where such elements will be placed, and any other modifications it will
     * make to the tree structure.
     * 
     * @param value the new value of the child node
     * @return {@code true} if the value was successfully added as a child node
     * to the tree, {@code false} otherwise
     */
    boolean add(E value);
    
    /**
     * Adds an entire child tree to this node, also known as 'grafting'. An
     * implementation of this method must <i>also</i> handle setting the parent of 
     * the child node, removing it from its current parent if necessary.<p>
     * 
     * Trees that support this operation may place limitations on what subtrees may
     * be added to this tree. In particular, some trees will refuse to add {@code null}
     * elements, and others will impose restrictions on the type of elements that may
     * be added, or perhaps on the structure of the subtrees.<p>
     *
     * If a tree refuses to add a particular tree for any exceptional reason (e.g.
     * attempting to add a tree when it is already present is <i>not</i> exceptional,
     * but attempting to add an invalid type of tree <i>is</i> exceptional), it
     * <i>must</i> throw an exception (rather than returning {@code false}). This 
     * preserves the invariant that a tree always contains the specified tree as a 
     * descendent after this call returns.<p>
     * 
     * Trees that support this operation may add subtrees at any location within the
     * tree, and can even alter other parts of the tree's structure; the only
     * invariant this method must preserve is that the given tree will always be present
     * <i>somewhere</i> within the tree immediately after returning (i.e. that the
     * internal structure of the given subtree remains unchanged, even if the rest of
     * this tree is changed).<p>
     * 
     * On the surface, it may seem that this method and {@link add(Object) add(value)}
     * are identical save for the fact that this method takes a tree parameter;
     * however, the behaviour of this method can differ wildly even if the parameters
     * appear to be functionally similar. For instance, a sorted tree may take a value
     * parameter through {@link add(Object) add(value)}, and add it to a specific area
     * of the tree without issue. However, attempting to add a subtree to the sorted
     * tree may cause the imposed ordering to be violated and thus require a
     * restructuring of the entire tree, including the subtree; since this is likely to
     * violate the invariant of this method that the given subtree remains unchanged,
     * a sorted tree class may opt to not implement this method.<p>
     * 
     * Tree classes should clearly specify the behaviour of this method within its
     * documentation, including any limitations on the elements that can be added to the
     * tree, where subtrees will be placed, and any other modifications it will make to
     * the tree structure.<p>
     * 
     * Note that implementing classes should ensure that this method never causes
     * a cyclic reference by attempting to add an ancestor node as a child, as the
     * data structure will cease to be a 'tree' in that case. Such an attempt should be
     * handled as an exceptional reason for refusing to add the tree.
     * 
     * @param tree the child tree to add
     * @return {@code true} if the specified tree was successfully added as a child
     * to the tree, {@code false} otherwise
     */
    boolean add(T tree);
    
    /**
     * Adds all the given values to this node as new child nodes. This should conform to
     * the behaviour of {@link #add(Object)}, but may employ a different, more efficient
     * algorithm for bulk operations.
     * 
     * @param values the values to add as new child nodes
     * @throws NullPointerException if the specified collection is null
     */
    void addAll(Collection<? extends E> values);
    
    /**
     * Removes a single node from this tree, if a node is present with the given
     * value; this is also known as 'pruning'. More formally, removes a node 
     * {@code n} if
     * {@code (value==null ? n.getValue()==null : n.getValue().equals(value))}. If
     * multiple nodes with this value are present, then the removed node will depend on
     * the encounter order of the nodes. Returns the pruned tree, or {@code null} if
     * no node with this value was found.
     * 
     * @param value the value to search for
     * @return the pruned tree, with {@code value} as the value of the root node
     */
    T remove(Object value);
    
    /**
     * Removes a single node from this tree, if a node is present that has a value
     * satisfying the given predicate. More formally, removes a node {@code n} if
     * {@code predicate.test(n.getValue())}. If multiple nodes with a matching value are
     * present, then the removed node will depend on the encounter order of the nodes.
     * Returns the pruned tree, or {@code null} if no node matching the predicate was
     * found.
     * 
     * @param predicate the predicate to apply to each value
     * @return the pruned tree, with a value of the root node that satisfies
     * {@code predicate}
     * @throws NullPointerException if the specified predicate is null
     */
    T removeIf(Predicate<? super E> predicate);
    
    /**
     * Removes all nodes with any values contained in the given collection from the
     * tree. This should conform to the behaviour of {@link remove(Object)}, but may
     * employ a different, more efficient search algorithm for bulk operations.
     * 
     * @param values the values to add as new child nodes
     * @throws NullPointerException if the specified collection is null
     */
    void removeAll(Collection<?> values);
    
    /**
     * Gets the degree of this node. This is defined as the number of direct children
     * this node has. A leaf node necessarily has a degree of 0.
     * 
     * @see isLeaf()
     * @return the degree of this node
     */
    int degree();
    
    /**
     * Gets the depth of this node. This is defined as the number of edges connecting
     * the node up to the root node as given by {@link root()}. If this node has no
     * parent, then it necessarily has a depth of 0.
     * 
     * @see isRoot()
     * @return the depth of this node
     */
    int depth();
    
    /**
     * Gets the direct parent of this tree, or {@code null} if this tree does not have
     * a parent.
     * 
     * @return the parent of this tree
     */
    T getParent();
    
    /**
     * Gets the root of this tree. This is defined as the unique ancestor of this 
     * tree such that it has no parent of its own.
     * 
     * @return the root of this tree
     */
    T root();
    
    /**
     * Performs the given action for each node of the tree until all nodes have been
     * processed or the action throws an exception. The order of this processing is
     * implementation-dependent. Exceptions thrown by the action are relayed to the
     * caller.
     *
     * @param action The action to be performed for each node
     * @throws NullPointerException if the specified action is null
     */
    void forEach(Consumer<? super T> action);
    
    /**
     * Maps this tree on a per-node basis to an equivalent tree. Implementations
     * should ensure that the returned tree has the same structure as this tree;
     * additionally, for each node {@code n} in this tree, the equivalent node in
     * the returned tree should hold the value {@code mapper.apply(n.getValue())}.
     * 
     * @param <R> the type of values the mapper maps to
     * @param mapper the mapping function
     * @return a new {@code Tree} with the same structure as this tree and with
     * each node's value being the mapped value of the equivalent node in this tree
     */
    <R> Tree<R, ?> map(Function<? super E, R> mapper);
    
    /**
     * Returns a breadth-first iterator over the nodes of this tree. This is
     * defined as an iterator that first visits this node of the tree, then each
     * child of this node, and then each child of those children, etc.<p>
     * 
     * More formally, this iterator visits every node in the tree; the ordering is
     * imposed by the condition that, given 2 nodes {@code m, n} such that {@code m}
     * is visited at any time before {@code n}, then:
     * <pre>    {@code m.depth() <= n.depth()}    </pre>
     * This enforces a partial ordering, but the ordering of nodes within each level
     * is implementation-specific.
     * 
     * @return a breadth-first {@link Iterator} over the nodes of the tree
     */
    Iterator<? extends T> breadthFirstWalk();
    
    /**
     * Returns a pre-order iterator over the nodes of this tree. This is defined
     * as an iterator that performs a pre-order walk of all nodes in the tree.<p>
     * 
     * More formally, this iterator visits every node in the tree; the ordering is
     * imposed by the algorithm:
     * <ol>
     *  <li>Return this node</li>
     *  <li>Recursively perform this algorithm on each child of this node</li>
     * </ol>
     * 
     * @return a pre-order {@link Iterator} over the nodes of the tree
     */
    Iterator<? extends T> preOrderWalk();
    
    /**
     * Returns a post-order iterator over the nodes of this tree. This is defined
     * as an iterator that performs a post-order walk of all nodes in the tree.<p>
     * 
     * More formally, this iterator visits every node in the tree; the ordering is
     * imposed by the algorithm:
     * <ol>
     *  <li>Recursively perform this algorithm on each child of this node</li>
     *  <li>Return this node</li>
     * </ol>
     * 
     * @return a post-order {@link Iterator} over the nodes of the tree
     */
    Iterator<? extends T> postOrderWalk();
    
    /**
     * Returns an iterator over the direct children of this tree.<p>
     * 
     * This iterator should not visit all elements of the tree; specifically, it
     * should not visit this node nor any of its descendants beyond the direct
     * children.
     * 
     * @return an {@link Iterator} over the child nodes of this node of the tree
     */
    Iterator<? extends T> childLikeWalk();
    
    /**
     * Returns an iterator over the ancestors of this tree. This first call to
     * {@link Iterator#next()} returns the direct parent of this node, and
     * recursively moves further up the tree until it finds and returns the root
     * node of the parent tree.<p>
     * 
     * This iterator should not visit all elements of the tree; specifically, it
     * should not visit this node.
     * 
     * @return an {@link Iterator} over the ancestor nodes of this node of the
     * tree
     */
    Iterator<? extends T> ancestralWalk();
    
    /**
     * Creates and returns a spliterator over the elements of this tree.<p>
     * 
     * The requirements imposed on both the implementation of this method and the
     * returned spliterator are similar to {@link Collection#spliterator()}. This
     * extends to documenting the spliterator, its reported characteristics, and
     * its policy on binding and concurrent structural interference.
     * 
     * @return the spliterator over the elements of the tree
     */
    Spliterator<E> spliterator();
    
    /**
     * Indicates whether some object is 'equal' to this tree. This is defined to mean
     * that the root nodes and all descendant nodes are equal to each other, in both 
     * position and value.<p>
     * 
     * A {@code null} parameter, or a non-{@code Tree} parameter, should return
     * {@code false}. Otherwise, implementations of this method should satisfy the
     * following axioms:
     * <ul>
     *  <li><i>Reflexivity</i>: {@code tree.equals(tree)} is always {@code true}</li>
     *  <li><i>Symmetry</i>: If {@code treeA.equals(treeB)}, then
     * {@code treeB.equals(treeA)}</li>
     *  <li><i>Transitivity</i>: If {@code treeA.equals(treeB)} and
     * {@code treeB.equals(treeC)}, then {@code treeA.equals(treeC)}</li>
     *  <li><i>Consistency</i>: Unless a structural modification occurs somewhere within
     * either tree or their descendants, all subsequent invocations of
     * {@code treeA.equals(treeB)} should return the same result.
     * </ul>
     * 
     * @see hashCode()
     * @param o the reference object with which to compare
     * @return {@code true} if this tree is equal to the object argument,
     * {@code false} otherwise
     */
    @Override
    boolean equals(Object o);
    
    /**
     * Returns a hash code value for the tree. This method is supported for the benefit
     * of hash tables such as those provided by {@link java.util.HashMap}.<p>
     * 
     * The general contract of {@code hashCode()} is:
     * <ul>
     *  <li>Whenever it is invoked on the same tree more than once during an execution
     * of a Java application, {@code hashCode()} must consistently return the same
     * integer, provided no structural modifications are made to the tree or any of its
     * descendants. This integer need not remain consistent from one execution of an
     * application to another execution of the same application.</li>
     *  <li>If two tree are equal according to the {@link equals(Object)} method, then 
     * calling {@code hashCode()} on each of the two objects must produce the same
     * integer result.</li>
     *  <li>It is not required that if two trees are unequal according to 
     * {@link equals(Object)}, then calling {@code hashCode()} on each of the two 
     * trees must produce distinct integer results. However, the programmer should be 
     * aware that producing distinct integer results for unequal trees may improve the
     * performance of hash tables.</li>
     * </ul>
     * 
     * As much as is reasonably practical, {@code hashCode()} should return distinct
     * integers for distinct trees.
     * 
     * @see equals(Object)
     * @return a hash code value for this tree
     */
    @Override
    int hashCode();
}
