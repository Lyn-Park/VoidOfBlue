package net.vob.util;

import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A collection of static methods that operate on and/or return tree structures.
 * Generally speaking, all the methods contained here perform miscellaneous/utility
 * operations that would not be entirely appropriate to place inside an implementing
 * class of {@link Tree}.<p>
 * 
 * Note that all methods here will throw a {@link NullPointerException} if a 
 * parameter expecting a {@code Tree} was passed {@code null}.
 * 
 * @author Lyn-Park
 */
public final class Trees {
    private static <E> List<E> traverseTree(Iterator<? extends Tree<? extends E, ?>> it) {
        List<E> out = new ArrayList<>();
        while (it.hasNext())
            out.add(it.next().getValue());
        return out;
    }
    
    /**
     * Converts a generic tree to a {@link List} of the contained values. This method
     * traverses the tree in {@linkplain Tree#breadthFirstWalk() breadth-first order}.
     * 
     * @see toPreOrderList(Tree)
     * @see toPostOrderList(Tree)
     * @see toSet(Tree)
     * @param <E> the type of values the tree contains
     * @param tree the tree to traverse
     * @return a {@link List} containing all the values of the tree in
     * breadth-first order
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static <E> List<E> toBreadthFirstList(Tree<? extends E, ?> tree) {
        if (tree == null)
            throw new NullPointerException();
        
        return traverseTree(tree.breadthFirstWalk());
    }
    
    /**
     * Converts a generic tree to a {@link List} of the contained values. This method
     * traverses the tree in {@linkplain Tree#preOrderWalk() depth pre-order}.
     * 
     * @see toBreadthFirstList(Tree)
     * @see toPostOrderList(Tree)
     * @see toSet(Tree)
     * @param <E> the type of values the tree contains
     * @param tree the tree to traverse
     * @return a {@link List} containing all the values of the tree in
     * depth pre-order
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static <E> List<E> toPreOrderList(Tree<? extends E, ?> tree) {
        if (tree == null)
            throw new NullPointerException();
        
        return traverseTree(tree.preOrderWalk());
    }
    
    /**
     * Converts a generic tree to a {@link List} of the contained values. This method
     * traverses the tree in {@linkplain Tree#postOrderWalk() depth post-order}.
     * 
     * @see toBreadthFirstList(Tree)
     * @see toPreOrderList(Tree)
     * @see toSet(Tree)
     * @param <E> the type of values the tree contains
     * @param tree the tree to traverse
     * @return a {@link List} containing all the values of the tree in
     * depth post-order
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static <E> List<E> toPostOrderList(Tree<? extends E, ?> tree) {
        if (tree == null)
            throw new NullPointerException();
        
        return traverseTree(tree.postOrderWalk());
    }
    
    /**
     * Converts a generic tree to a {@link Set} of the contained values. All values in
     * the returned set are guaranteed to exist within the given tree (note that the set
     * may <i>not</i> have the same size as the tree, as duplicate values are discarded).
     * 
     * @see toBreadthFirstList(Tree)
     * @see toPreOrderList(Tree)
     * @see toPostOrderList(Tree)
     * @param <E> the type of values the tree contains
     * @param tree the tree to traverse
     * @return a {@link Set} containing all the values of the tree
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static <E> Set<E> toSet(Tree<? extends E, ?> tree) {
        if (tree == null)
            throw new NullPointerException();
        
        Set<E> out = new HashSet<>();
        Iterator<? extends Tree<? extends E, ?>> it = tree.breadthFirstWalk();
        while (it.hasNext())
            out.add(it.next().getValue());
        return out;
    }
    
    /**
     * Utility method, which creates an {@link UnmodifiableTree} with only 1 node (the
     * root node), and the value of the node is {@code value}.
     * @param <E> the type of value the tree contains
     * @param value the value of the single node
     * @return an {@link UnmodifiableTree} with a single node
     */
    public static <E> UnmodifiableTree<E> singletonTree(E value) {
        return new UnmodifiableTree<>(value);
    }
    
    /**
     * Converts the given tree to an {@link UnmodifiableTree}. The values and structure
     * of the given tree is copied over; modifications to the original tree do not affect
     * the new tree.
     * 
     * @param <E> the type of values the tree contains
     * @param tree the tree to convert to be unmodifiable
     * @return an {@link UnmodifiableTree} conversion of {@code tree}
     * @throws NullPointerException if {@code tree} is {@code null}
     */
    public static <E> UnmodifiableTree<E> unmodifiableTree(Tree<? extends E, ?> tree) {
        if (tree == null)
            throw new NullPointerException();
        
        return new UnmodifiableTree<>(tree);
    }
    
    /**
     * A basic implementation of {@link AbstractTree}, that does not permit any
     * modifications to its structure or stored values. All methods and operations that
     * attempt to modify the tree will throw an {@link UnsupportedOperationException}.<p>
     * 
     * Note that the constructors of this class are non-public. Any users that wish to
     * obtain an instance of this class must go through the static methods of 
     * {@link Trees}.
     * 
     * @param <E> the type of the values in the tree
     */
    public final static class UnmodifiableTree<E> extends AbstractTree<E> {
        private final E value;
        private final UnmodifiableTree[] children;
        private final UnmodifiableTree parent;

        UnmodifiableTree(E value) {
            this.value = value;
            this.children = null;
            this.parent = null;
        }
        
        UnmodifiableTree(Tree<? extends E, ?> wrapped) {
            this(wrapped, null);
        }
        
        private UnmodifiableTree(Tree<? extends E, ?> wrapped, UnmodifiableTree<E> parent) {
            this.value = wrapped.getValue();
            this.parent = parent;
            
            this.children = new UnmodifiableTree[wrapped.degree()];
            
            int i = 0;
            Iterator<? extends Tree<? extends E, ?>> it = wrapped.childLikeWalk();
            while (it.hasNext())
                children[i++] = new UnmodifiableTree<>(it.next(), this);
        }

        @Override
        public Iterator<UnmodifiableTree<E>> childLikeWalk() {
            return children == null ? Collections.emptyIterator() : Iterators.forArray(children);
        }

        @Override
        public E getValue() {
            return value;
        }

        @Override
        public UnmodifiableTree<E> getParent() {
            return parent;
        }

        @Override
        public <R> UnmodifiableTree<R> map(Function<? super E, R> mapper) {
            ArrayTree<R> tree = new ArrayTree<>(mapper.apply(value));
            
            Iterator<UnmodifiableTree<E>> it = childLikeWalk();
            while (it.hasNext())
                tree.add(new ArrayTree<>(it.next().map(mapper)));
            
            return new UnmodifiableTree<>(tree);
        }
    }
}
