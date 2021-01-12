package net.vob.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

public class BinaryTree<E> extends AbstractTree<E> {
    private final E value;
    private BinaryTree<E> parent = null, left = null, right = null;

    private void removeChild(BinaryTree<E> child) {
        if (this.left == child) {
            child.incrementModHash();
            child.parent = null;
            this.left = null;
        } else if (this.right == child) {
            child.incrementModHash();
            child.parent = null;
            this.right = null;
        }
    }
    
    public BinaryTree(E value) {
        this.value = value;
    }
    
    @Override
    public E getValue() {
        return value;
    }

    @Override
    public BinaryTree<E> getParent() {
        return parent;
    }

    @Override
    public <R> BinaryTree<R> map(Function<? super E, R> mapper) {
        BinaryTree<R> out = new BinaryTree<>(mapper.apply(value));
        
        if (this.left != null) {
            out.left = this.left.<R>map(mapper);
            out.left.parent = out;
        }
        
        if (this.right != null) {
            out.right = this.right.<R>map(mapper);
            out.right.parent = out;
        }
        
        return out;
    }
    
    public BinaryTree<E> getLeft() {
        return left;
    }
    
    public BinaryTree<E> getRight() {
        return right;
    }
    
    public boolean setLeft(BinaryTree<E> left) {
        if (left != null) {
            Iterator<? extends AbstractTree<E>> ancestors = ancestralWalk();
            while (ancestors.hasNext())
                if (ancestors.next() == left)
                    return false;

            if (left.parent != null)
                left.parent.removeChild(left);

            left.parent = this;
        }
        
        this.left = left;
        incrementModHash();
        return true;
    }
    
    public boolean setRight(BinaryTree<E> right) {
        if (right != null) {
            Iterator<? extends AbstractTree<E>> ancestors = ancestralWalk();
            while (ancestors.hasNext())
                if (ancestors.next() == right)
                    return false;

            if (right.parent != null)
                right.parent.removeChild(right);

            right.parent = this;
        }
        
        this.right = right;
        incrementModHash();
        return true;
    }
    
    @Override
    public Iterator<BinaryTree<E>> childLikeWalk() {
        return new BinaryChildLikeIterator();
    }
    
    private class BinaryChildLikeIterator implements Iterator<BinaryTree<E>> {
        private byte status = 0;
        
        @Override
        public boolean hasNext() {
            switch (status) {
                case 0: return left != null || right != null;
                case 1: return right != null;
                default: return false;
            }
        }

        @Override
        public BinaryTree<E> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            
            status++;
            
            if (status == 1) {
                if (left == null) {
                    status++;
                    return right;
                }
                return left;
            }
            
            return right;
        }

        @Override
        public void remove() {
            switch (status) {
                case 1:
                    if (left == null)
                        throw new IllegalStateException();
                    
                    left.parent.removeChild(left);
                    left = null;
                    
                case 2:
                    if (right == null)
                        throw new IllegalStateException();
                    
                    right.parent.removeChild(right);
                    right = null;
                
                default:
                    throw new IllegalStateException();
            }
        }
    }
    
    public Iterator<BinaryTree<E>> inOrderWalk() {
        return new BinaryInOrderIterator<>(this);
    }
    
    private static class BinaryInOrderIterator<E> implements Iterator<BinaryTree<E>> {
        Deque<BinaryTree<E>> stack = new ArrayDeque<>();
        BinaryTree<E> prev = null;
        
        public BinaryInOrderIterator(BinaryTree<E> root) {
            stack.push(root);
            
            BinaryTree<E> tree = root;
            while ((tree = tree.getLeft()) != null)
                stack.push(tree);
        }
        
        @Override
        public boolean hasNext() {
            return !stack.isEmpty();
        }

        @Override
        public BinaryTree<E> next() {
            prev = stack.pop();
            
            BinaryTree<E> tree;
            if ((tree = prev.getRight()) != null) {
                stack.push(tree);
                
                while ((tree = tree.getLeft()) != null)
                    stack.push(tree);
            }
            
            return prev;
        }
    }
}
