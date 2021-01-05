package net.vob.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("ThrowableResultIgnored")
public class RecursiveArrayTreeTest {
    
    private static RecursiveArrayTree<Integer> testTree1, testTree2;
    
    public RecursiveArrayTreeTest() {
    }
    
    @BeforeAll
    public static void setUpClass() {
    }
    
    @AfterAll
    public static void tearDownClass() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }
    
    private void setTestInstances() {
        testTree1 = new RecursiveArrayTree<>(5);
        testTree1.add(2);
        testTree1.add(7);
        testTree1.get(0).add(9);
        
        testTree2 = new RecursiveArrayTree<>(3);
        testTree2.add(6);
        testTree2.add(1);
        testTree2.get(1).add(4);
    }

    /**
     * Test of getValue method, of class AbstractTree.
     */
    @Test
    public void testGetValue() {
        setTestInstances();
        
        System.out.println("getValue");
        assertEquals(5, (int)testTree1.getValue());
        assertEquals(3, (int)testTree2.getValue());
    }

    /**
     * Test of setValue method, of class AbstractTree.
     */
    @Test
    public void testSetValue() {
        setTestInstances();
        
        System.out.println("setValue");
        testTree1.setValue(8);
        testTree2.setValue(0);
        assertEquals(8, (int)testTree1.getValue());
        assertEquals(0, (int)testTree2.getValue());
    }

    /**
     * Test of add method, of class AbstractTree.
     */
    @Test
    public void testAdd_GenericType() {
        setTestInstances();
        
        System.out.println("add_GenericType");
        
        int unexpectedModHash = testTree1.modHash;
        
        assertTrue(testTree1.add(12));
        assertEquals(2, (int)testTree1.get(0).getValue());
        assertEquals(7, (int)testTree1.get(1).getValue());
        assertEquals(12, (int)testTree1.get(2).getValue());
        assertEquals(testTree1, testTree1.get(2).parent());
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        unexpectedModHash = testTree1.modHash;
        assertTrue(testTree1.get(1).add(16));
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        assertTrue(testTree2.add(11));
        assertEquals(6, (int)testTree2.get(0).getValue());
        assertEquals(1, (int)testTree2.get(1).getValue());
        assertEquals(11, (int)testTree2.get(2).getValue());
        assertEquals(testTree2, testTree2.get(2).parent());
    }

    /**
     * Test of add method, of class AbstractTree.
     */
    @Test
    public void testAdd_AbstractTree() {
        setTestInstances();
        
        System.out.println("add_AbstractTree");
        RecursiveArrayTree tree = new RecursiveArrayTree<>(-5);
        
        assertThrows(NullPointerException.class, () -> testTree2.add((AbstractTree)null));
        assertThrows(ClassCastException.class, () -> testTree1.add(new AbstractTreeTestClass()));
        
        int unexpectedModHash = testTree1.modHash;
        
        assertTrue(testTree1.add(tree));
        assertEquals(2, (int)testTree1.get(0).getValue());
        assertEquals(7, (int)testTree1.get(1).getValue());
        assertEquals(-5, (int)testTree1.get(2).getValue());
        assertEquals(testTree1, tree.parent());
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        unexpectedModHash = testTree1.modHash;
        assertTrue(testTree2.add(tree));
        assertEquals(6, (int)testTree2.get(0).getValue());
        assertEquals(1, (int)testTree2.get(1).getValue());
        assertEquals(-5, (int)testTree2.get(2).getValue());
        assertEquals(testTree2, tree.parent());
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        assertFalse(testTree1.add(testTree1));
        assertFalse(testTree2.get(1).add(testTree2));
    }
    
    /**
     * Test of add method, of class RecursiveArrayTree.
     */
    @Test
    public void testAdd_IndexGenericType() {
        setTestInstances();
        
        System.out.println("add_IndexGenericType");
        
        assertThrows(IndexOutOfBoundsException.class, () -> testTree1.add(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree2.add(5, 2));
        
        int unexpectedModHash = testTree1.modHash;
        
        assertTrue(testTree1.add(1, 17));
        assertEquals(2, (int)testTree1.get(0).getValue());
        assertEquals(17, (int)testTree1.get(1).getValue());
        assertEquals(7, (int)testTree1.get(2).getValue());
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        unexpectedModHash = testTree2.modHash;
        
        assertTrue(testTree2.add(2, -14));
        assertTrue(testTree2.add(0, -3));
        assertEquals(-3, (int)testTree2.get(0).getValue());
        assertEquals(6, (int)testTree2.get(1).getValue());
        assertEquals(1, (int)testTree2.get(2).getValue());
        assertEquals(-14, (int)testTree2.get(3).getValue());
        assertNotEquals(unexpectedModHash, testTree2.modHash);
    }
    
    /**
     * Test of add method, of class RecursiveArrayTree.
     */
    @Test
    public void testAdd_IndexAbstractTree() {
        setTestInstances();
        
        System.out.println("add_IndexAbstractTree");
        RecursiveArrayTree<Integer> tree = new RecursiveArrayTree<>(-7);
        
        assertThrows(NullPointerException.class, () -> testTree1.add(0, (AbstractTree)null));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree2.add(-1, tree));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree2.add(4, tree));
        
        int unexpectedModHash = testTree1.modHash;
        
        assertTrue(testTree1.add(1, tree));
        assertEquals(2, (int)testTree1.get(0).getValue());
        assertEquals(-7, (int)testTree1.get(1).getValue());
        assertEquals(7, (int)testTree1.get(2).getValue());
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        unexpectedModHash = testTree1.modHash;
        assertTrue(testTree2.add(0, tree));
        assertEquals(-7, (int)testTree2.get(0).getValue());
        assertEquals(6, (int)testTree2.get(1).getValue());
        assertEquals(1, (int)testTree2.get(2).getValue());
        assertFalse(testTree1.containsSubTree(tree));
        assertNotEquals(unexpectedModHash, testTree1.modHash);
        
        assertFalse(testTree1.add(1, testTree1));
        assertFalse(testTree2.get(2).add(0, testTree2));
    }
    
    /**
     * Test of get method, of class RecursiveArrayTree.
     */
    @Test
    public void testGet() {
        setTestInstances();
        
        System.out.println("get");
        
        assertThrows(IndexOutOfBoundsException.class, () -> testTree1.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree1.get(2));
        
        assertEquals(2, (int)testTree1.get(0).getValue());
        assertEquals(9, (int)testTree1.get(0).get(0).getValue());
        assertEquals(7, (int)testTree1.get(1).getValue());
        
        assertEquals(6, (int)testTree2.get(0).getValue());
        assertEquals(1, (int)testTree2.get(1).getValue());
        assertEquals(4, (int)testTree2.get(1).get(0).getValue());
    }
    
    /**
     * Test of set method, of class RecursiveArrayTree.
     */
    @Test
    public void testSet() {
        setTestInstances();
        
        System.out.println("set");
        
        assertThrows(NullPointerException.class, () -> testTree1.set(0, null));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree1.set(-1, new RecursiveArrayTree<>(1)));
        assertThrows(IndexOutOfBoundsException.class, () -> testTree1.set(2, new RecursiveArrayTree<>(1)));
        
        assertEquals(7, (int)testTree1.get(1).getValue());
        assertTrue(testTree1.set(1, new RecursiveArrayTree<>(-6)));
        assertEquals(-6, (int)testTree1.get(1).getValue());
        assertFalse(testTree1.set(0, testTree1));
        assertFalse(testTree1.get(0).set(0, testTree1));
    }

    /**
     * Test of remove method, of class AbstractTree.
     */
    @Test
    public void testRemove() {
        setTestInstances();
        
        System.out.println("remove");
        AbstractTree tree = testTree1.remove((Integer)7);
        assertNotNull(tree);
        assertEquals(7, tree.getValue());
        assertFalse(testTree1.contains(7));
        
        tree = testTree1.remove((Integer)6);
        assertNull(tree);
    }

    /**
     * Test of removeIf method, of class AbstractTree.
     */
    @Test
    public void testRemoveIf() {
        setTestInstances();
        
        System.out.println("removeIf");
        assertThrows(NullPointerException.class, () -> testTree1.removeIf(null));
        
        AbstractTree tree;
        Predicate<Integer> predicate = (val) -> val == 1;
        
        tree = testTree1.removeIf(predicate);
        assertNull(tree);
        tree = testTree2.removeIf(predicate);
        assertNotNull(tree);
        assertEquals(1, tree.getValue());
    }

    /**
     * Test of size method, of class AbstractTree.
     */
    @Test
    public void testSize() {
        setTestInstances();
        
        System.out.println("size");
        assertEquals(4, testTree1.size());
        testTree1.add(-2);
        testTree1.get(1).add(-9);
        assertEquals(6, testTree1.size());
    }

    /**
     * Test of contains method, of class AbstractTree.
     */
    @Test
    public void testContains() {
        setTestInstances();
        
        System.out.println("contains");
        assertTrue(testTree1.contains(5));
        assertTrue(testTree1.contains(9));
        assertFalse(testTree1.contains(0));
        assertTrue(testTree2.contains(6));
        assertTrue(testTree2.contains(4));
        assertFalse(testTree2.contains(8));
    }

    /**
     * Test of containsAll method, of class AbstractTree.
     */
    @Test
    public void testContainsAll() {
        setTestInstances();
        
        System.out.println("containsAll");
        assertThrows(NullPointerException.class, () -> testTree1.containsAll(null));
        assertTrue(testTree1.containsAll(new ArrayList<>()));
        
        assertTrue(testTree1.containsAll(Arrays.asList(5, 9, 7)));
        assertTrue(testTree1.containsAll(Arrays.asList(7, 2)));
        assertFalse(testTree1.containsAll(Arrays.asList(9, 3, 5)));
        assertTrue(testTree2.containsAll(Arrays.asList(6, 4)));
        assertTrue(testTree2.containsAll(Arrays.asList(3, 1, 4, 6)));
        assertFalse(testTree2.containsAll(Arrays.asList(4, 3, 0)));
    }

    /**
     * Test of containsSubTree method, of class AbstractTree.
     */
    @Test
    public void testContainsSubTree() {
        setTestInstances();
        
        System.out.println("containsSubTree");
        assertThrows(NullPointerException.class, () -> testTree1.containsSubTree(null));
        assertTrue(testTree1.containsSubTree(testTree1));
        
        RecursiveArrayTree tree = new RecursiveArrayTree<>(2);
        tree.add(9);
        assertTrue(testTree1.containsSubTree(tree));
        assertFalse(tree.containsSubTree(testTree1));
        assertFalse(testTree2.containsSubTree(tree));
        assertFalse(tree.containsSubTree(testTree2));
    }

    /**
     * Test of degree method, of class AbstractTree.
     */
    @Test
    public void testDegree() {
        setTestInstances();
        
        System.out.println("degree");
        
        assertEquals(2, testTree1.degree());
        assertEquals(1, testTree1.get(0).degree());
        assertEquals(0, testTree1.get(1).degree());
        assertEquals(2, testTree2.degree());
        assertEquals(0, testTree2.get(0).degree());
        assertEquals(1, testTree2.get(1).degree());
    }

    /**
     * Test of depth method, of class AbstractTree.
     */
    @Test
    public void testDepth() {
        setTestInstances();
        
        System.out.println("depth");
        
        assertEquals(0, testTree1.depth());
        assertEquals(1, testTree1.get(0).depth());
        assertEquals(1, testTree1.get(1).depth());
        assertEquals(2, testTree2.get(1).get(0).depth());
    }

    /**
     * Test of isLeaf method, of class AbstractTree.
     */
    @Test
    public void testIsLeaf() {
        setTestInstances();
        
        System.out.println("isLeaf");
        
        assertFalse(testTree1.isLeaf());
        assertFalse(testTree1.get(0).isLeaf());
        assertTrue(testTree1.get(1).isLeaf());
        assertFalse(testTree2.isLeaf());
        assertTrue(testTree2.get(0).isLeaf());
        assertFalse(testTree2.get(1).isLeaf());
    }

    /**
     * Test of isRoot method, of class AbstractTree.
     */
    @Test
    public void testIsRoot() {
        setTestInstances();
        
        System.out.println("isRoot");
        
        assertTrue(testTree1.isRoot());
        assertFalse(testTree1.get(0).isRoot());
        assertFalse(testTree1.get(1).isRoot());
        assertTrue(testTree2.isRoot());
        assertFalse(testTree2.get(0).isRoot());
        assertFalse(testTree2.get(1).isRoot());
    }

    /**
     * Test of root method, of class AbstractTree.
     */
    @Test
    public void testRoot() {
        setTestInstances();
        
        System.out.println("root");
        
        assertEquals(testTree1, testTree1.root());
        assertEquals(testTree1, testTree1.get(0).root());
        assertEquals(testTree1, testTree1.get(0).get(0).root());
        assertEquals(testTree2, testTree2.get(0).root());
        assertEquals(testTree2, testTree2.get(1).root());
    }

    /**
     * Test of forEach method, of class AbstractTree.
     */
    @Test
    public void testForEach() {
        setTestInstances();
        
        System.out.println("forEach");
        
        int[] expected = new int[1];
        testTree1.forEach((node) -> expected[0] += node.getValue());
        assertEquals(23, expected[0]);
        
        expected[0] = 1;
        testTree2.forEach((node) -> expected[0] *= node.getValue());
        assertEquals(72, expected[0]);
        
        expected[0] = 0;
        testTree2.forEach((node) -> { if (node.isLeaf()) expected[0] += node.getValue(); });
        assertEquals(10, expected[0]);
    }

    /**
     * Test of equals method, of class AbstractTree.
     */
    @Test
    public void testEquals() {
        setTestInstances();
        
        System.out.println("equals");
        
        RecursiveArrayTree tree = new RecursiveArrayTree<>(-4);
        assertTrue(tree.equals(tree));
        assertFalse(tree.equals(null));
        
        RecursiveArrayTree target = new RecursiveArrayTree<>(-4);
        assertTrue(tree.equals(target));
        assertTrue(target.equals(tree));
        
        target.add(1);
        assertFalse(tree.equals(target));
        assertFalse(target.equals(tree));
        
        tree.add(1);
        assertTrue(tree.equals(target));
        assertTrue(target.equals(tree));
        
        target.get(0).add(2);
        assertFalse(tree.equals(target));
        assertFalse(target.equals(tree));
        
        tree.get(0).add(2);
        assertTrue(tree.equals(target));
        assertTrue(target.equals(tree));
    }
    
    private final static class AbstractTreeTestClass extends AbstractTree<Integer> {

        @Override
        public Iterator<? extends AbstractTree<? extends Integer>> breadthFirstWalk() {
            return null;
        }

        @Override
        public Iterator<? extends AbstractTree<? extends Integer>> childLikeWalk() {
            return null;
        }

        @Override
        public Iterator<? extends AbstractTree<? extends Integer>> ancestralWalk() {
            return null;
        }

        @Override
        public Spliterator<Integer> spliterator() {
            return null;
        }

        @Override
        public Integer getValue() {
            return null;
        }

        @Override
        public AbstractTree<? extends Integer> parent() {
            return null;
        }
        
    }
}
