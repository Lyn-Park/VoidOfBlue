package net.vob.util;

import java.util.Iterator;
import java.util.function.Function;

/**
 * The simplest type of {@code Tree}. An instance of this class contains the node
 * value, a reference to the parent {@code RecursiveArrayTree}, and an initially
 * empty array of {@code RecursiveArrayTree} child instances.<p>
 * 
 * The array of children grows or shrinks as needed to accommodate new child
 * references; note that this may slow down mutation operations. The tree also
 * does not implement any specialized search, walking, or folding algorithms;
 * faster implementations of these operations will require a more specialized type
 * of tree.<p>
 * 
 * Duplicate and {@code null} node values are permitted in this tree.
 * 
 * @param <E> the value type
 */
public class RecursiveArrayTree<E> extends AbstractTree<E> {
    private E value;
    private RecursiveArrayTree<E> parent = null;
    private RecursiveArrayTree<E>[] children;
    
    /**
     * Instantiates a new {@code RecursiveArrayTree} with the given value, an
     * initially empty array of children, and an initially {@code null} parent.
     * @param value the value of the tree node
     */
    public RecursiveArrayTree(E value) {
        this.value = value;
        this.children = new RecursiveArrayTree[0];
    }
    
    private void insertChild(int index, RecursiveArrayTree<E> child) {
        if (index < 0 || index > children.length)
            throw new IndexOutOfBoundsException();
        if (child == null)
            throw new NullPointerException();
        
        child.incrementModHash();
        incrementModHash();
        
        RecursiveArrayTree<E>[] newChildren = new RecursiveArrayTree[children.length + 1];
        
        System.arraycopy(children, 0, newChildren, 0, index);
        newChildren[index] = child;
        System.arraycopy(children, index, newChildren, index+1, children.length - index);
        
        children = newChildren;
        
        if (child.parent != null)
            child.parent.removeChild(child);
        
        child.parent = this;
    }
    
    private void replaceChild(int index, RecursiveArrayTree<E> child) {
        if (index < 0 || index >= children.length)
            throw new IndexOutOfBoundsException();
        if (child == null)
            throw new NullPointerException();
        
        children[index].incrementModHash();
        child.incrementModHash();
        
        children[index].parent = null;
        children[index] = child;
        
        if (child.parent != null)
            child.parent.removeChild(child);
        
        child.parent = this;
    }
    
    private void removeChild(int index) {
        if (index < 0 || index >= children.length)
            throw new IndexOutOfBoundsException();
        
        children[index].incrementModHash();
        children[index].parent = null;
        
        RecursiveArrayTree<E>[] newChildren = new RecursiveArrayTree[children.length - 1];
        
        System.arraycopy(children, 0, newChildren, 0, index);
        System.arraycopy(children, index+1, newChildren, index, newChildren.length - index);
        
        children = newChildren;
    }
    
    private void removeChild(RecursiveArrayTree<E> child) {
        if (child == null)
            throw new NullPointerException();
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
     * Sets the value of this tree node.
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
     * Creates a new {@code RecursiveArrayTree} instance using the given value, and
     * attaches it to this tree node as a child.
     * @param value {@inheritDoc}
     * @return
     */
    @Override
    public boolean add(E value) {
        return add(children.length, value);
    }
    
    @Override
    public boolean add(AbstractTree<? extends E> child) {
        return add(children.length, child);
    }
    
    public boolean add(int index, E value) {
        RecursiveArrayTree<E> child = new RecursiveArrayTree(value);
        insertChild(index, child);
        return true;
    }
    
    public boolean add(int index, AbstractTree<? extends E> child) {
        if (child == null)
            throw new NullPointerException();
        if (child == this)
            return false;
        
        Iterator<? extends AbstractTree<? extends E>> ancestors = ancestralWalk();
        while (ancestors.hasNext())
            if (ancestors.next() == child)
                return false;
        
        insertChild(index, (RecursiveArrayTree<E>)child);
        return true;
    }
    
    public RecursiveArrayTree<E> get(int index) {
        return children[index];
    }
    
    public boolean set(int index, AbstractTree<? extends E> child) {
        if (child == null)
            throw new NullPointerException();
        if (child == this)
            return false;
        
        Iterator<? extends AbstractTree<? extends E>> ancestors = ancestralWalk();
        while (ancestors.hasNext())
            if (ancestors.next() == child)
                return false;
        
        replaceChild(index, (RecursiveArrayTree<E>)child);
        return true;
    }
    
    public RecursiveArrayTree<E> remove(int index) {
        RecursiveArrayTree<E> child = children[index];
        removeChild(index);
        return child;
    }
    
    @Override
    public <R> RecursiveArrayTree<R> map(Function<? super E, ? extends R> mapper) {
        RecursiveArrayTree<R> root = new RecursiveArrayTree<>(mapper.apply(value));
        
        Iterator<RecursiveArrayTree<E>> it = childLikeWalk();
        while (it.hasNext())
            root.add(it.next().map(mapper));
        
        return root;
    }
    
    @Override
    public RecursiveArrayTree<E> parent() {
        return parent;
    }
    
    @Override
    public Iterator<RecursiveArrayTree<E>> childLikeWalk() {
        return new ChildLikeIterator<>(this);
    }
    
    private static class ChildLikeIterator<E> implements Iterator<RecursiveArrayTree<E>> {
        private final RecursiveArrayTree<E> parent;
        private RecursiveArrayTree<E> prev = null;
        private int index = 0;
        
        ChildLikeIterator(RecursiveArrayTree parent) {
            this.parent = parent;
        }
        
        @Override
        public boolean hasNext() {
            return index < parent.children.length;
        }
        
        @Override
        public RecursiveArrayTree<E> next() {
            return (prev = parent.children[index++]);
        }
        
        @Override
        public void remove() { 
            if (prev == null)
                throw new IllegalStateException();
            
            parent.removeChild(prev);
            prev = null;
        }
    }
}
