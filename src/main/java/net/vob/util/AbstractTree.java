package net.vob.util;

import com.google.common.collect.Iterators;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vob.util.logging.LocaleUtils;

/**
 * This class provides a skeletal implementation of the {@code Tree} interface to
 * minimize the effort required to implement this interface.<p>
 * 
 * To implement an unmodifiable tree, the programmer needs only to extend this class and
 * provide implementations for {@link getValue()}, {@link parent()} and
 * {@link childLikeWalk()}; the child-like walk iterator should <i>not</i> have an
 * implementation of {@code remove} if the tree is to be unmodifiable.<p>
 * 
 * To implement a tree that can have elements added to it, the programmer must
 * additionally override {@link doSetValue(Object)}, {@link doAddChild(Object)} and
 * {@link doAddChild(AbstractTree)}; these are template helper methods for the actual
 * {@code setValue} and {@code add} methods inherited from {@code Tree}, and thus
 * should ideally remain as {@code protected} members in their implementations. By
 * default, these 3 methods simply throw an {@link UnsupportedOperationException}. See
 * the javadocs for each method for more information.<p>
 * 
 * To implement a tree that can have elements removed from it, the programmer must simply
 * override the {@link Iterator#remove() remove()} method in the child-like walk
 * iterator. See {@link childLikeWalk()} for more information.<p>
 * 
 * Any class that extends {@code AbstractTree} that permits mutability should also make
 * sure to call {@link incrementModHash()} as appropriate. This alters an internal
 * 'mod hash', which is used by the {@link Spliterator} of the tree to determine when
 * structural interference occurs during traversal; it also propagates the change up the
 * tree to its ancestors, so they too know when a change occurs. Note that not using
 * {@code incrementModHash()} will cause the {@code Spliterator} instances to be unable
 * to determine when a concurrent change is attempted.<p>
 * 
 * The iteration order of the {@linkplain breadthFirstWalk() breadth-first},
 * {@linkplain preOrderWalk() pre-order}, and {@linkplain postOrderWalk() post-order}
 * walks is defined by the implementation of the child-like walk iterator. Note that due
 * to the use of stacks/queues in the iterators, the breadth-first iterator iterates over
 * the children <i>in the order</i> they were returned by the child-like iterator;
 * conversely, the pre-order and post-order iterators iterate over the children
 * <i>in reverse order</i> as returned by the child-like iterator. Implementing classes
 * that do not want this type of behaviour must return custom iterators from the
 * respective methods.<p>
 * 
 * Finally, note that all methods (apart from {@code setValue(..)} and
 * {@code add(..)}) can be overridden, if the tree structure being implemented
 * admits a more efficient implementation.
 * 
 * @param <E> the type of values in this tree
 */
public abstract class AbstractTree<E> implements Tree<E, AbstractTree<? extends E>> {
    transient int modHash = super.hashCode();
    
    /**
     * Sets the value of the root node of the currentNode tree. By default, this 
     * method simply throws an {@link UnsupportedOperationException}.<p>
     * 
     * <b>Note</b>: implementations of this method are <i>expected</i> to invoke
     * {@link incrementModHash()} <i>on this tree</i>. Failure to do so will
     * result in the {@linkplain spliterator() spliterator} instances for this
     * tree and any ancestors being at least partially blind to any concurrent
     * structural modification.
     * 
     * @param value the new value of the root node
     * @throws UnsupportedOperationException if this implementation of
     * {@code AbstractTree} is unmodifiable
     */
    @Override
    public void setValue(E value) {
        throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.UnsupportedOperationException", "Tree", "setValue"));
    }
    
    /**
     * Adds a child node to the root node of the current tree. The given value is the
     * value of the new child. An implementation of this method must <i>also</i>
     * handle setting the parent of the new child node. By default, this method
     * simply throws an {@link UnsupportedOperationException}.<p>
     * 
     * <b>Note</b>: implementations of this method are <i>expected</i> to invoke
     * {@link incrementModHash()} <i>on this tree immediately after the addition 
     * occurs</i>, if it does occur. Failure to do so will result in the
     * {@linkplain spliterator() spliterator} instances for this tree and any
     * ancestors being at least partially blind to any concurrent structural
     * modification.
     * 
     * @param value
     * @return
     * @throws UnsupportedOperationException if this implementation of
     * {@code AbstractTree} is unmodifiable
     */
    @Override
    public boolean add(E value) {
        throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.UnsupportedOperationException", "Tree", "add"));
    }
    
    /**
     * Adds an entire child tree to this node, also known as 'grafting'. This simply
     * treats the root node of the given tree as a child of this node. An
     * implementation of this method must <i>also</i> handle setting the parent of 
     * the child node, removing it from its current parent if necessary. By default,
     * this method simply throws an {@link UnsupportedOperationException}.<p>
     * 
     * Implementing classes should ensure that this method never causes a cyclic
     * reference by attempting to add an ancestor node as a child, as the data
     * structure will then cease to be a 'tree'.<p>
     * 
     * <b>Note</b>: implementations of this method are <i>expected</i> to invoke
     * {@link incrementModHash()} <i>on the added child tree immediately after the
     * addition occurs</i>, if it does occur. Failure to do so will result in the
     * {@linkplain spliterator() spliterator} instances for this tree, the child,
     * and any ancestors being at least partially blind to any concurrent structural
     * modification. The same goes for the previous parent of the given tree, if it
     * had one.
     * 
     * @param tree
     * @return 
     * @throws NullPointerException if {@code tree} is {@code null}
     * @throws UnsupportedOperationException if this implementation of
     * {@code AbstractTree} is unmodifiable
     */
    @Override
    public boolean add(AbstractTree<? extends E> tree) {
        throw new UnsupportedOperationException(LocaleUtils.format("global.Exception.UnsupportedOperationException", "Tree", "add"));
    }
    
    @Override
    public void addAll(Collection<? extends E> values) {
        Objects.requireNonNull(values);
        values.forEach((val) -> AbstractTree.this.add(val));
    }
    
    @Override
    public AbstractTree<? extends E> remove(Object value) {
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        AbstractTree<? extends E> tree;
        
        if (value == null) {
            while (it.hasNext()) {
                if ((tree = it.next()).getValue() == null) {
                    it.remove();
                    return tree;
                }
            }
        } else {
            while (it.hasNext()) {
                if (value.equals((tree = it.next()).getValue())) {
                    it.remove();
                    return tree;
                }
            }
        }
        
        return null;
    }
    
    @Override
    public void removeAll(Collection<?> values) {
        Objects.requireNonNull(values);
        values.forEach((val) -> {
            while(remove(val) != null) {}
        });
    }
    
    @Override
    public AbstractTree<? extends E> removeIf(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate);
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        AbstractTree<? extends E> tree;
        
        while (it.hasNext()) {
            tree = it.next();
            if (predicate.test(tree.getValue())) {
                if (tree != this)
                    it.remove();
                return tree;
            }
        }
        
        return null;
    }
    
    @Override
    public int size() {
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        int size = 0;
        
        while (it.hasNext()) {
            it.next();
            size++;
        }
        
        return size;
    }
    
    @Override
    public boolean contains(Object o) {
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        
        if (o == null) {
            while (it.hasNext())
                if (it.next().getValue() == null)
                    return true;
        } else {
            while (it.hasNext())
                if (o.equals(it.next().getValue()))
                    return true;
        }
        
        return false;
    }
    
    @Override
    public boolean containsAll(Collection<?> values) {
        Objects.requireNonNull(values);
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        Set<?> set = new HashSet<>(values);
        
        while (it.hasNext() && !set.isEmpty()) {
            E val = it.next().getValue();
            if (values.contains(val))
                set.remove(val);
        }
        
        return set.isEmpty();
    }
    
    @Override
    public boolean containsSubTree(AbstractTree<? extends E> tree) {
        Objects.requireNonNull(tree);
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        
        while (true) {
            AbstractTree<? extends E> subtree = searchForNextNode(it, tree.getValue());
            if (subtree == null)
                return false;
            
            Iterator<? extends AbstractTree<? extends E>> subtreeIt = subtree.breadthFirstWalk();
            Iterator<? extends AbstractTree<? extends E>> queryIt = tree.breadthFirstWalk();
            
            while (subtreeIt.hasNext()) {
                if (!queryIt.hasNext() || 
                    !Objects.equals(subtreeIt.next().getValue(), queryIt.next().getValue()))
                    break;
                
                if (!subtreeIt.hasNext() && !queryIt.hasNext())
                    return true;
            }
        }
    }
    
    @Override
    public int degree() {
        Iterator<? extends AbstractTree<? extends E>> it = childLikeWalk();
        int degree = 0;
        while (it.hasNext()) {
            it.next();
            degree++;
        }
        return degree;
    }
    
    @Override
    public int depth()  {
        Iterator<? extends AbstractTree<? extends E>> it = ancestralWalk();
        int depth = 0;
        while (it.hasNext()) {
            it.next();
            depth++;
        }
        return depth;
    }
    
    @Override
    public boolean isLeaf() {
        Iterator<? extends AbstractTree<? extends E>> it = childLikeWalk();
        return !it.hasNext();
    }
    
    @Override
    public boolean isRoot() {
        Iterator<? extends AbstractTree<? extends E>> it = ancestralWalk();
        return !it.hasNext();
    }
    
    @Override
    public AbstractTree<? extends E> root() {
        Iterator<? extends AbstractTree<? extends E>> it = ancestralWalk();
        AbstractTree<? extends E> tree = this;
        while (it.hasNext())
            tree = it.next();
        return tree;
    }
    
    @Override
    public void forEach(Consumer<? super AbstractTree<? extends E>> action) {
        Objects.requireNonNull(action);
        Iterator<? extends AbstractTree<? extends E>> it = breadthFirstWalk();
        
        while (it.hasNext())
            action.accept(it.next());
    }
    
    /**
     * Folds the values in the tree according to the given
     * <a href="package-summary.html#Associativity">associative</a> accumulator and
     * selector functions, and returns the result (if there is a result). This
     * operation is also known as a
     * <a href="package-summary.html#Reduction">reduction</a>.<p>
     * 
     * The order of the tree nodes visited by the folding operation is given by
     * {@link childLikeWalk()} and uses a queue; therefore, the visitation order is
     * similar to {@link breadthFirstWalk()}. Each node is passed through the given 
     * {@code selector} to determine if it is to be folded; if {@code selector}
     * returns {@code true}, then the node is folded into the result using
     * {@code result = accumulator.apply(nodeValue, result)} <i>and</i> the
     * children of that node are also queued up for selection and folding; if
     * {@code selector} returns {@code false}, then the node and any descendants of
     * that node are not folded into the result.<p>
     * 
     * Note that the root node of this tree is also passed to {@code selector}. If
     * the root node is not selected, then the returned {@link Optional} will be
     * empty. Otherwise, the value of the root node is used as the initial 'result'
     * value. This method is thus similar to the
     * {@link java.util.stream.Stream#reduce(java.util.function.BinaryOperator) reduce(accumulator)}
     * method in the {@link java.util.stream.Stream Stream} class, with an added
     * predicate method for filtering out certain tree nodes.<p>
     * 
     * The given {@code accumulator} must be
     * <a href="package-summary.html#Associativity">associative</a>; failure by
     * users to ensure this may lead to unexpected results.
     * 
     * @param selector the {@link Predicate} selector for tree nodes; nodes are only
     * folded into the result, and their direct descendants queued up for further
     * processing, if this method returns {@code true} for that node
     * @param accumulator the {@link BinaryOperator} accumulator function; the
     * first argument is always the result of the previous call to this function, and
     * the second argument is always the value of the current node being processed
     * @return an {@link Optional} containing the value of the result of the folding,
     * or an empty {@code Optional} if the root node, and thus the entire tree, fails
     * the {@code selector} predicate
     */
    public Optional<? extends E> fold(Predicate<? super AbstractTree<? extends E>> selector, BinaryOperator<E> accumulator) {
        Queue<AbstractTree<? extends E>> queue = new ArrayDeque<>();
        queue.add(this);
        E result = null;
        
        while (!queue.isEmpty()) {
            AbstractTree<? extends E> tree = queue.poll();
            
            if (selector.test(tree)) {
                if (result == null)
                    result = tree.getValue();
                else
                    result = accumulator.apply(result, tree.getValue());
                
                Iterator<? extends AbstractTree<? extends E>> it = tree.childLikeWalk();
                while (it.hasNext())
                    queue.add(it.next());
            }
        }
        
        if (result == null)
            return Optional.empty();
        else
            return Optional.of(result);
    }
    
    /**
     * Folds the values in the tree according to the given identity value,
     * <a href="package-summary.html#Associativity">associative</a> accumulator and
     * selector functions, and returns the result. This operation is also known as a
     * <a href="package-summary.html#Reduction">reduction</a>.<p>
     * 
     * The order of the tree nodes visited by the folding operation is given by
     * {@link childLikeWalk()} and uses a queue; therefore, the visitation order is
     * similar to {@link breadthFirstWalk()}. Each node is passed through the given 
     * {@code selector} to determine if it is to be folded; if {@code selector}
     * returns {@code true}, then the node is folded into the result using
     * {@code result = accumulator.apply(nodeValue, result)} <i>and</i> the
     * children of that node are also queued up for selection and folding; if
     * {@code selector} returns {@code false}, then the node and any descendants of
     * that node are not folded into the result.<p>
     * 
     * Note that the root node of this tree is also passed to {@code selector}. If
     * the root node is not selected, then {@code identity} is returned. This method
     * is thus similar to the
     * {@link java.util.stream.Stream#reduce(Object, java.util.function.BinaryOperator) reduce(identity, accumulator)}
     * method in the {@link java.util.stream.Stream Stream} class, with an added
     * predicate method for filtering out certain tree nodes.<p>
     * 
     * The given {@code accumulator} must be
     * <a href="package-summary.html#Associativity">associative</a>; failure by
     * users to ensure this may lead to unexpected results.
     * 
     * @param <U> the type of result to return
     * @param identity the initial value to use as the result
     * @param selector the {@link Predicate} selector for tree nodes; nodes are only
     * folded into the result, and their direct descendants queued up for further
     * processing, if this method returns {@code true} for that node
     * @param accumulator the {@link BiFunction} accumulator function; the first
     * argument is always the result of the previous call to this function, and the
     * second argument is always the value of the current node being processed
     * @return the result of the fold operation
     */
    public <U> U fold(U identity, Predicate<? super AbstractTree<? extends E>> selector, BiFunction<U, ? super E, U> accumulator) {
        Queue<AbstractTree<? extends E>> queue = new ArrayDeque<>();
        queue.add(this);
        U result = identity;
        
        while (!queue.isEmpty()) {
            AbstractTree<? extends E> tree = queue.poll();
            
            if (selector.test(tree)) {
                result = accumulator.apply(result, tree.getValue());
                
                Iterator<? extends AbstractTree<? extends E>> it = tree.childLikeWalk();
                while (it.hasNext())
                    queue.add(it.next());
            }
        }
        
        return result;
    }
    
    /**
     * Folds the value of the root node of this tree with the values of the ancestors
     * of this tree according to the given
     * <a href="package-summary.html#Associativity">associative</a> accumulator, and
     * returns the result.<p>
     * 
     * The order of the tree nodes visited by the folding operation is given by
     * {@link ancestralWalk()}; therefore, the visitation order occurs from the root
     * node up through it's ancestors in reverse.<p>
     * 
     *  If the root node of this tree has no ancestors, then the returned result will
     * simply be the value of this node. This method is thus similar to the
     * {@link java.util.stream.Stream#reduce(Object, java.util.function.BinaryOperator) reduce(identity, accumulator)}
     * method in the {@link java.util.stream.Stream Stream} class, with the value of
     * this node as the identity.<p>
     * 
     * The given {@code accumulator} must be
     * <a href="package-summary.html#Associativity">associative</a>; failure by
     * users to ensure this may lead to unexpected results.
     * 
     * @param accumulator the {@link BinaryOperator} accumulator function; the
     * first argument is always the result of the previous call to this function, and
     * the second argument is always the value of the current node being processed
     * @return the result of the fold operation
     */
    public E foldAncestors(BinaryOperator<E> accumulator) {
        Iterator<? extends AbstractTree<? extends E>> it = ancestralWalk();
        E result = getValue();
        
        while (it.hasNext())
            result = accumulator.apply(result, it.next().getValue());
        
        return result;
    }
    
    private AbstractTree<? extends E> searchForNextNode(Iterator<? extends AbstractTree<? extends E>> it, Object o) {
        AbstractTree<? extends E> tree;
        
        if (o == null) {
            while (it.hasNext())
                if ((tree = it.next()).getValue() == null)
                    return tree;
        } else {
            while (it.hasNext())
                if (o.equals((tree = it.next()).getValue()))
                    return tree;
        }
        
        return null;
    }
    
    /**
     * Increments the mod hash of this tree and all it's ancestors.<p>
     * 
     * The mod hash is a type of hash value, initially derived from the 
     * {@code super.hashCode()} of this tree, and is then incremented for both this
     * tree and all ancestor trees through this method. The mod hash is used for the
     * {@link Spliterator} to determine if any structural interference has occurred
     * during traversal. Any trees that implement their own mutating methods without
     * overriding {@link spliterator()} are <i>expected</i> to invoke this method;
     * failure to do so will result in the {@linkplain spliterator() spliterator}
     * instances of the involved trees and their ancestors being at least partially
     * blind to any concurrent structural interference.
     */
    protected final void incrementModHash() {
        modHash++;
        
        Iterator<? extends AbstractTree<? extends E>> it = ancestralWalk();
        while (it.hasNext()) {
            AbstractTree<? extends E> tree = it.next();
            tree.modHash++;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof AbstractTree))
            return false;
        
        Tree t = (Tree)o;
        
        return Objects.equals(getValue(), t.getValue()) &&
               Iterators.elementsEqual(childLikeWalk(), t.childLikeWalk());
    }
    
    @Override
    public int hashCode() {
        int hashCode = 3;
        hashCode = 7 * hashCode + getValue().hashCode();
        
        Iterator<? extends AbstractTree<? extends E>> it = childLikeWalk();
        while (it.hasNext())
            hashCode = 7 * hashCode + it.next().hashCode();
        
        return hashCode;
    }
    
    /**
     * {@inheritDoc}<p>
     * 
     * If this tree is to be unmodifiable, then the returned iterator from this
     * method should <i>not</i> have an implementation of
     * {@link Iterator#remove()}, as all other methods of removal from the tree
     * ultimately invoke this method.<p>
     * 
     * Conversely, trees that are modifiable should implement
     * {@link Iterator#remove()}; in this case, the iterator is <i>expected</i> to
     * invoke {@link incrementModHash()} <i>on the removed child tree before it is
     * removed</i>. Not calling it will result in the
     * {@linkplain spliterator() spliterator} instances for the child tree and all
     * ancestors being at least partially blind to any concurrent structural
     * modification, and calling it <i>after</i> removal will not propagate the
     * incrementation up the tree, again causing the spliterators to be blind to
     * the modification.<p>
     * 
     * The {@link Iterator#remove()} implementation should also ensure that the 
     * removed child tree does not point to this tree as it's parent upon returning
     * from it.
     * 
     * @return {@inheritDoc}
     */
    @Override
    public abstract Iterator<? extends AbstractTree<? extends E>> childLikeWalk();
    
    /**
     * Creates and returns a spliterator over the elements within this tree.<p>
     * 
     * This method returns a spliterator that reports {@link Spliterator#SIZED}
     * and {@link Spliterator#SUBSIZED} as characteristics. The spliterator is not 
     * <i>late-binding</i>.<p>
     * 
     * For detecting structural interference during traversal, the spliterator makes
     * use of an internal 'mod hash' within the tree instance. This mod hash changes
     * depending on the number of modifications made to the tree over the course of
     * its lifetime; the spliterator uses this to check for any interference. If 
     * interference was detected, then the spliterator throws a
     * {@link ConcurrentModificationException}. Note that implementing classes, if
     * they override/define their own mutating methods and don't override this
     * method, are <i>expected</i> to call {@link incrementModHash()} to alter the
     * mod hash; failure of implementing classes to do so will result in the
     * spliterator being at least partially blind to structural interference during
     * traversal.
     * 
     * @return the spliterator over the elements of the tree
     */
    @Override
    public Spliterator<E> spliterator() {
        return new TreeSpliterator(this);
    }
    
    private class TreeSpliterator implements Spliterator<E> {
        Set<AbstractTree<? extends E>> trees;
        long est = Long.MAX_VALUE;
        
        TreeSpliterator(AbstractTree<? extends E> root) {
            this.trees = new HashSet<>();
            this.trees.add(root);
            
            this.est = root.size();
        }
        
        private TreeSpliterator(Set<AbstractTree<? extends E>> trees) {
            this.trees = trees;
            this.est = trees.stream().mapToInt((tree) -> tree.size()).sum();
        }

        @Override
        public int characteristics() {
            return Spliterator.SIZED | Spliterator.SUBSIZED;
        }
        
        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            trees.stream().forEachOrdered((tree) -> {
                int expectedModHash = tree.modHash;
                
                Iterator<? extends AbstractTree<? extends E>> it = tree.breadthFirstWalk();
                while (it.hasNext())
                    action.accept(it.next().getValue());
                
                if (tree.modHash != expectedModHash)
                    throw new ConcurrentModificationException();
            });
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            Iterator<? extends AbstractTree<? extends E>> it = trees.iterator();
            if (!it.hasNext())
                return false;
            
            AbstractTree<? extends E> current = it.next();
            it.remove();
            
            int expectedModHash = current.modHash;
            
            action.accept(current.getValue());
            Iterator<? extends AbstractTree<? extends E>> cit = current.childLikeWalk();
            while (cit.hasNext())
                trees.add(cit.next());
            
            if (current.modHash != expectedModHash)
                throw new ConcurrentModificationException();
            
            return true;
        }

        @Override
        public Spliterator<E> trySplit() {
            if (trees.size() <= 1)
                return null;
            
            Set<AbstractTree<? extends E>> set1 = new HashSet<>(), set2 = new HashSet<>();
            splitTrees(trees, set1, set2);
            
            trees = set1;
            return new TreeSpliterator(set2);
        }

        @Override
        public long estimateSize() {
            return est;
        }
        
        private void splitTrees(Set<AbstractTree<? extends E>> tree, Set<AbstractTree<? extends E>> out1, Set<AbstractTree<? extends E>> out2) {
            Iterator<AbstractTree<? extends E>> it = tree.iterator();
            boolean turnout = false;
            
            while (it.hasNext()) {
                if (turnout)
                    out1.add(it.next());
                else
                    out2.add(it.next());
                turnout ^= true;
            }
        }
    }
    
    @Override
    public Iterator<? extends AbstractTree<? extends E>> ancestralWalk() {
        return new AncestralIterator(this);
    }
    
    private class AncestralIterator implements Iterator<AbstractTree<? extends E>> {
        private AbstractTree<? extends E> current;
        
        AncestralIterator(AbstractTree<? extends E> current) {
            this.current = current;
        }
        
        @Override
        public boolean hasNext() {
            return current.parent() != null;
        }

        @Override
        public AbstractTree<? extends E> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            
            return (current = current.parent());
        }
    }
    
    @Override
    public Iterator<? extends AbstractTree<? extends E>> breadthFirstWalk() {
        return new BreadthFirstIterator(this);
    }
    
    private class BreadthFirstIterator implements Iterator<AbstractTree<? extends E>> {
        Queue<AbstractTree<? extends E>> queue = new ArrayDeque<>();
        AbstractTree<? extends E> current = null;
        
        BreadthFirstIterator(AbstractTree<? extends E> root) {
            queue.add(root);
        }
        
        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public AbstractTree<? extends E> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            
            current = queue.poll();
            
            Iterator<? extends AbstractTree<? extends E>> it = current.childLikeWalk();
            while (it.hasNext())
                queue.add(it.next());
            
            return current;
        }

        @Override
        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            
            Iterator<? extends AbstractTree<? extends E>> siblings = current.parent().childLikeWalk();
            while (siblings.hasNext()) {
                if (siblings.next() == current) {
                    siblings.remove();
                    break;
                }
            }
            
            Iterator<? extends AbstractTree<? extends E>> children = current.childLikeWalk();
            while (children.hasNext())
                queue.remove(children.next());
            
            current = null;
        }
    }
    
    @Override
    public Iterator<AbstractTree<? extends E>> preOrderWalk() {
        return new PreOrderIterator(this);
    }
    
    private class PreOrderIterator implements Iterator<AbstractTree<? extends E>> {
        Stack<AbstractTree<? extends E>> stack = new Stack<>();
        AbstractTree<? extends E> current = null;
        
        PreOrderIterator(AbstractTree<? extends E> root) {
            stack.push(root);
        }
        
        @Override
        public boolean hasNext() {
            return !stack.empty();
        }

        @Override
        public AbstractTree<? extends E> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            
            current = stack.pop();
            
            Iterator<? extends AbstractTree<? extends E>> it = current.childLikeWalk();
            while (it.hasNext())
                stack.push(it.next());
            
            return current;
        }

        @Override
        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            
            Iterator<? extends AbstractTree<? extends E>> siblings = current.parent().childLikeWalk();
            while (siblings.hasNext()) {
                if (siblings.next() == current) {
                    siblings.remove();
                    break;
                }
            }
            
            Iterator<? extends AbstractTree<? extends E>> children = current.childLikeWalk();
            while (children.hasNext())
                stack.remove(children.next());
            
            current = null;
        }
    }
    
    @Override
    public Iterator<AbstractTree<? extends E>> postOrderWalk() {
        return new PostOrderIterator(this);
    }
    
    private class PostOrderIterator implements Iterator<AbstractTree<? extends E>> {
        Stack<AbstractTree<? extends E>> stack = new Stack<>();
        Stack<Integer> degrees = new Stack<>();
        AbstractTree<? extends E> current = null;
        
        PostOrderIterator(AbstractTree<? extends E> root) {
            stack.push(root);
            degrees.push(1);
            addDescendantsToStack();
        }
        
        @Override
        public boolean hasNext() {
            return !stack.empty();
        }

        @Override
        public AbstractTree<? extends E> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            
            current = stack.pop();
            int degree = degrees.pop() - 1;
            if (degree != 0 && !stack.empty()) {
                degrees.push(degree);
                addDescendantsToStack();
            }
            
            return current;
        }

        @Override
        public void remove() {
            if (current == null)
                throw new IllegalStateException();
            
            Iterator<? extends AbstractTree<? extends E>> siblings = current.parent().childLikeWalk();
            while (siblings.hasNext()) {
                if (siblings.next() == current) {
                    siblings.remove();
                    break;
                }
            }
            
            current = null;
        }
        
        private void addDescendantsToStack() {
            while (!stack.peek().isLeaf()) {
                int degree = 0;
                
                Iterator<? extends AbstractTree<? extends E>> children = stack.peek().childLikeWalk();
                while (children.hasNext()) {
                    stack.push(children.next());
                    degree++;
                }
                
                degrees.push(degree);
            }
        }
    }
}
