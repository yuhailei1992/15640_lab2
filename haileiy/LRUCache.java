/**
 * @author haileiy@andrew.cmu.edu
 * @date 2015 / 03 / 07
 * The LRU cache traces the current size of cache directory
 * evicts the least recently used file when cache is going to be full
 */
import java.util.*;

public class LRUCache {

    private int currsize; // current cache size
    private long capacity; // maximum size
    private HashMap<String, Entry> hm; // helps to find the node quickly
    private Entry head; // dummy nodes make life easier
    private Entry tail;
    /**
     * constructor, set cache size limit
     */
    public LRUCache(long capacity) {
        // create dummies
        head = new Entry();
        tail = new Entry();
        // link the dummies
        head.next = tail;
        tail.prev = head;
        // set variables
        this.capacity = capacity;
        this.currsize = 0;
        hm = new HashMap<String, Entry>();
    }

    /**
     * when get file by its path, move it to the head
     * return the path on success, return null on failure
     */
    public String get(String path) {
        System.err.println("Cache: get " + path);
        Entry node = hm.get(path);
        if (node != null) {
            move_to_head(node);
            return path;
        } else {
            System.err.println("Cache: get failed");
            return null;
        }
    }

    /**
     * @param path is the path of the file, filesize is the file's size
     * return true on success
     * return false if there is no space in the cache
     */
    public synchronized int insert(String path, long filesize) {
        System.err.println("Cache: insert " + path + " with size of " + filesize);
        // do not insert duplicate
        if (hm.containsKey(path)) {
            System.err.println("This node is already in cache");
            return -2;
        }
        // evict if needed
        while (this.currsize + filesize > this.capacity) {
            System.err.println("Cache: full.need to evict something");
            Entry p = tail.prev;
            for (; p != head; p = p.prev) {
                if (Proxy.isInUse(p.path)) {
                    System.err.println("Cache::Cannot evict file " + p.path);
                } else {
                    break;
                }
            }
            if (p == head) {
                System.err.println("Cache: cannot evict anything");
                return -1;
            } else {
                removeNode(p.path);
                Proxy.removeFileWithoutUpdatingCache(p.path);
            }
        }

        System.err.println("Cache:: now we have enough space");
        this.currsize += filesize;
        // create new node
        Entry node = new Entry(path, filesize);
        // insert the node to the head
        set_new_head(node);
        hm.put(path, node);
        //showCache();
        return 0;
    }

    /**
     * @param path
     * @return true on success, false on failure
     */
    public synchronized boolean removeNode(String path) {
        Entry node = hm.get(path);
        if (node == tail || node == head) {
            System.err.println("Cannot remove head or tail!!!!!!!");
            return false;
        } else {
            System.err.println("Cache:remove node " + path);
            node.prev.next = node.next;
            node.next.prev = node.prev;
            currsize -= node.size;
            hm.remove(path);
            return true;
        }
    }

    /**
     * set the node as the new head
     * @param node
     * @return void
     */
    public synchronized void set_new_head(Entry node) {
        node.next = head.next;
        node.next.prev = node;
        head.next = node;
        node.prev = head;
    }

    /**
     * move the node to head of linkedlist
     * @param node
     * @return void
     */
    public synchronized void move_to_head (Entry node) {
        if (head.next == tail) return;
        else {
            // remove the node, and insert it to head
            node.prev.next = node.next;
            node.next.prev = node.prev;
            set_new_head(node);
        }
    }

    /**
     * show cache in a friendly style
     */
    public synchronized void showCache() {
        Entry node = head.next;

        System.err.println("====================Cache start====================");
        int i = 0;
        long totalsize = 0;
        while (node != tail) {
            System.err.print("#" + i + node.path + "ref # " + Proxy.open_map.get(node.path) + "\t");
            totalsize += node.size;
            node = node.next;
            i++;
        }
        assert totalsize == this.currsize; // ensure no leak
        System.err.println();
        System.err.println("********************Cache end ********************");
    }

}

class Entry {
    String path;
    long size;
    Entry prev;
    Entry next;

    public Entry(String path, long size) {
        this.path = path;
        this.size = size;
    }

    public Entry() {

    }
}


