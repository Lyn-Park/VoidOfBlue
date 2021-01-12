package net.vob.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * The simplest type of {@code Tree}. An instance of this class contains the node
 * value, a reference to the parent {@code ArrayTree}, and an initially empty array 
 * of {@code ArrayTree} child instances.<p>
 * 
 * The array of children grows or shrinks as needed to accommodate new child
 * references, similar to a {@link java.util.Vector Vector} or
 * {@link java.util.ArrayList ArrayList}; note that this will cause mutation operations
 * to be slower. The tree also does not implement any specialized search, walking, or
 * folding algorithms; faster implementations of these operations will require a more
 * specialized type of tree.<p>
 * 
 * This tree type is <i>not</i> thread-safe; in the absence of external
 * synchronization, {@code ArrayTree} instances do not support concurrent access by
 * multiple threads.<p>
 * 
 * Duplicate and {@code null} node values are permitted in this tree.
 * 
 * @param <E> the value type
 */
public class ArrayTree<E> extends AbstractTree<E> {
    private E value;
    private ArrayTree<E> parent = null;
    private ArrayTree<E>[] children;
    
    /**
     * Instantiates a new {@code ArrayTree} with the given value, an initially
     * empty array of children, and an initially {@code null} parent. {@code ArrayTree}
     * instances impose no restrictions on the elements of the tree.
     * 
     * @param value the value of the tree node
     */
    public ArrayTree(E value) {
        this.value = value;
        this.children = new ArrayTree[0];
    }
    
    private void insertChild(int index, ArrayTree<E> child) {
        child.incrementModHash();
        incrementModHash();
        
        ArrayTree<E>[] newChildren = new ArrayTree[children.length + 1];
        
        System.arraycopy(children, 0, newChildren, 0, index);
        newChildren[index] = child;
        System.arraycopy(children, index, newChildren, index+1, children.length - index);
        
        children = newChildren;
        
        if (child.parent != null)
            child.parent.removeChild(child);
        
        child.parent = this;
    }
    
    private void replaceChild(int index, ArrayTree<E> child) {
        children[index].incrementModHash();
        child.incrementModHash();
        
        children[index].parent = null;
        children[index] = child;
        
        if (child.parent != null)
            child.parent.removeChild(child);
        
        child.parent = this;
    }
    
    private void removeChild(int index) {
        children[index].incrementModHash();
        children[index].parent = null;
        
        ArrayTree<E>[] newChildren = new ArrayTree[children.length - 1];
        
        System.arraycopy(children, 0, newChildren, 0, index);
        System.arraycopy(children, index+1, newChildren, index, newChildren.length - index);
        
        children = newChildren;
    }
    
    private void removeChild(ArrayTree<E> child) {
        if (child.parent != this)
            return;
        
        for (int i = 0; i < children.length; ++i) {
            if (children[i] == child) {
                removeChild(i);
                break;
            }
        }
    }
    
    @Override
    public int degree() {
        return children.length;
    }
    
    /**
     * Sets the value of this tree node. {@code ArrayTree} instances impose no 
     * restrictions on the elements of the tree.
     * @param value {@inheritDoc}
     */
    @Override
    public void setValue(E value) {
        this.value = value;
        incrementModHash();
    }
    
    /**
     * Gets the value of this tree node.
     * @return {@inheritDoc}
     */
    @Override
    public E getValue() {
        return this.value;
    }
    
    /**
     * Constructs a new tree with the given value, and attaches it to this tree as
     * a direct child. {@code ArrayTree} instances impose no restrictions on the
     * elements of the tree.
     * 
     * @param value {@inheritDoc}
     * @return always {@code true}
     */
    @Override
    public boolean add(E value) {
        ArrayTree<E> child = new ArrayTree(value);
        insertChild(children.length, child);
        return true;
    }
    
    /**
     * Attaches the given tree to this tree as a direct child. {@code ArrayTree}
     * instances impose no restrictions on the elements of the tree.
     * @param child {@inheritDoc}
     * @return always {@code true}, if an exception is not thrown
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index > numChildren}
     * @throws ClassCastException if {@code child} is not an {@code ArrayTree}
     * @throws IllegalArgumentException if {@code child} is {@code this}, or an
     * ancestor tree of {@code this}
     */
    @Override
    public boolean add(AbstractTree<E> child) {
        return add(children.length, child);
    }
    
    /**
     * Creates a new {@code ArrayTree} instance using the given value, and attaches
     * it to this tree node as a direct child. {@code ArrayTree} instances impose no
     * restrictions on the elements of the tree. The new child's index is 
     * {@code index}, and any children with indices {@code i >= index} are shifted so
     * their new indices are {@code i+1}.
     * 
     * @param index the index to insert the new child at
     * @param value the value of the new child
     * @return always {@code true}, if no exception is thrown
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index > numChildren}
     */
    public boolean add(int index, E value) {
        if (index < 0 || index > children.length)
            throw new IndexOutOfBoundsException();
        
        ArrayTree<E> child = new ArrayTree(value);
        insertChild(index, child);
        return true;
    }
    
    /**
     * Attaches the given tree to this tree node as a child. {@code ArrayTree}
     * instances impose no restrictions on the elements of the tree. The new child's
     * index is {@code index}, and any children with indices {@code i >= index} are
     * shifted so their new indices are {@code i+1}.<p>
     * 
     * Note the return value is invariant, and the tree parameter is an
     * {@link AbstractTree} rather than an {@code ArrayTree}; this is so that this
     * method conforms to the signature of {@link add(AbstractTree)}.
     * 
     * @param index the index to insert the new child at
     * @param child the new child
     * @return always {@code true}, if no exception is thrown
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index > numChildren}
     * @throws ClassCastException if {@code child} is not an {@code ArrayTree}
     * @throws IllegalArgumentException if {@code child} is {@code this}, or an
     * ancestor tree of {@code this}
     */
    public boolean add(int index, AbstractTree<E> child) {
        if (child == null)
            throw new NullPointerException();
        if (index < 0 || index > children.length)
            throw new IndexOutOfBoundsException();
        if (!(child instanceof ArrayTree))
            throw new ClassCastException();
        if (child == this)
            throw new IllegalArgumentException();
        
        Iterator<? extends AbstractTree<E>> ancestors = ancestralWalk();
        while (ancestors.hasNext())
            if (ancestors.next() == child)
                throw new IllegalArgumentException();
        
        insertChild(index, (ArrayTree<E>)child);
        return true;
    }
    
    /**
     * Gets the child of this tree at the given index.
     * @param index the index of the child to get
     * @return the child tree
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index >= numChildren}
     */
    public ArrayTree<E> get(int index) {
        if (index < 0 || index >= children.length)
            throw new IndexOutOfBoundsException();
        
        return children[index];
    }
    
    /**
     * Sets the child of this tree at the given index. This replaces whatever child
     * was at that index. {@code ArrayTree} instances impose no restrictions on the
     * elements of the tree.<p>
     * 
     * Note the return value is invariant, and the tree parameter is an
     * {@link AbstractTree} rather than an {@code ArrayTree}; this is so that this
     * method conforms to the signature of {@link add(AbstractTree)}.
     * 
     * @param index the index of the child to set
     * @param child the new child
     * @return always {@code true}, if no exception is thrown
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index >= numChildren}
     * @throws ClassCastException if {@code child} is not an {@code ArrayTree}
     * @throws IllegalArgumentException if {@code child} is {@code this}, or an
     * ancestor tree of {@code this}
     */
    public boolean set(int index, AbstractTree<E> child) {
        if (child == null)
            throw new NullPointerException();
        if (index < 0 || index >= children.length)
            throw new IndexOutOfBoundsException();
        if (!(child instanceof ArrayTree))
            throw new ClassCastException();
        if (child == this)
            throw new IllegalArgumentException();
        
        Iterator<? extends AbstractTree<E>> ancestors = ancestralWalk();
        while (ancestors.hasNext())
            if (ancestors.next() == child)
                throw new IllegalArgumentException();
        
        replaceChild(index, (ArrayTree<E>)child);
        return true;
    }
    
    /**
     * Removes the child at the given index. Returns the removed child tree.
     * @param index the index of the child to remove
     * @return the remove child
     * @throws IndexOutOfBoundsException if {@code index < 0} or
     * {@code index >= numChildren}
     */
    public ArrayTree<E> remove(int index) {
        if (index < 0 || index >= children.length)
            throw new IndexOutOfBoundsException();
        
        ArrayTree<E> child = children[index];
        removeChild(index);
        return child;
    }
    
    @Override
    public <R> ArrayTree<R> map(Function<? super E, R> mapper) {
        ArrayTree<R> root = new ArrayTree<>(mapper.apply(value));
        
        Iterator<ArrayTree<E>> it = childLikeWalk();
        while (it.hasNext())
            root.add(it.next().map(mapper));
        
        return root;
    }
    
    @Override
    public ArrayTree<E> getParent() {
        return parent;
    }
    
    /**
     * Returns an iterator over the direct children of this tree.<p>
     * 
     * The {@code ArrayTree} implementation of this method simply iterates over the
     * internal array of children of the tree. It also supports removal of child trees
     * via the {@link Iterator#remove() remove()} method.
     * 
     * @return 
     */
    @Override
    public Iterator<ArrayTree<E>> childLikeWalk() {
        return new ChildLikeIterator<>(this);
    }
    
    private static class ChildLikeIterator<E> implements Iterator<ArrayTree<E>> {
        private final ArrayTree<E> parent;
        private ArrayTree<E> prev = null;
        private int index = 0;
        
        ChildLikeIterator(ArrayTree parent) {
            this.parent = parent;
        }
        
        @Override
        public boolean hasNext() {
            return index < parent.children.length;
        }
        
        @Override
        public ArrayTree<E> next() {
            return (prev = parent.children[index++]);
        }
        
        @Override
        public void remove() { 
            if (prev == null)
                throw new IllegalStateException();
            
            parent.removeChild(prev);
            index--;
            prev = null;
        }
    }
}
