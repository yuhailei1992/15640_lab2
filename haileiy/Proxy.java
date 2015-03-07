/******************************************************************************
 * author: haileiy@andrew.cmu.edu
 * date:   2015 / 03 / 06
 *
 * This file enforces 100-char-line policy
 *
 * Perform the basic operations of a LRU cache. Provides open, close, read, write, lseek, unlink
 * functions.
 * Enforces open-close semantics.
 * Enforces LRU cache eviction policy.
 * Use RMI to communicate with server.
 * One-file granularity.
 *****************************************************************************/

import java.io.File;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;

class Proxy {
    /*
     * ip, port, proxyrootdir, proxycachesize are acquired from command line arguments
     * CHUNKSIZE is the size of chunks
     * cache is the LRU cache object
     * server is the RMI stub
     * version_map stores the versions of files at proxy
     * open_map records how many references a file has
     * latest_map tracks the latest master copy of a file
     * origin_map tells you what the corresponding original file is
     */

    /* static variables acquired from command line */
    public static String ip;
    public static int port;
    public static String proxyrootdir;
    public static long proxycachesize;
    public static int CHUNKSIZE;
    /* objects */
    public static LRUCache cache;
    public static IServer server;
    /* hashmaps */
    public static ConcurrentHashMap<String, Integer> version_map;
    public static ConcurrentHashMap<String, Integer> open_map;
    public static ConcurrentHashMap<String, String> latest_map;
    public static ConcurrentHashMap<String, String> origin_map;

    /**
     * remove file give the filename, without updating the cache
     * @param proxy_path
     * @return true on success, false on failure
     */
    public static boolean removeFileWithoutUpdatingCache(String proxy_path) {
        System.err.println("RemoveFile " + proxy_path);

        // check if the file exists or if is in use
        if (!Proxy.open_map.containsKey(proxy_path) || Proxy.open_map.get(proxy_path) > 0) {
            System.err.println("Error in RemoveFile: the file is not opened or is in use");
        } else { // the file can be removed
            try {
                open_map.remove(proxy_path);
                File f = new File(proxy_path);
                f.delete();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * return true if the file is being used by another client
     * return false if no client is using this file
     * @param proxy_path
     * @return true is the file is still in use, false vice versa
     */
    public static boolean isInUse(String proxy_path) {
        if (Proxy.open_map.containsKey(proxy_path) && Proxy.open_map.get(proxy_path) > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * stores some important properties of a file, such as if the file is directory, is readonly
     */
    public static class FileProperty {
        String filename;
        boolean isReadOnly;
        boolean isDirectory;

        public FileProperty () {
            filename = "";
            isReadOnly = false;
            isDirectory = false;
        }
    }

    /* RMI related, stole from a instructor on piazza */
    public static IServer getServerInstance(String ip, int port) {
        String url = String.format("//%s:%d/ServerService", ip, port);
        try {
            return (IServer) Naming.lookup (url);
        } catch (MalformedURLException e) {
            System.err.println("Bad URL" + e);
        } catch (RemoteException e) {
            System.err.println("Remote connection refused to url "+ url + " " + e);
        } catch (NotBoundException e) {
            System.err.println("Not bound " + e);
        }
        return null;
    }


    private static class FileHandler implements FileHandling {

        /*
         * fd_map records the path and fd tuple
         * path_map is reverse of fd_map
         * raf_map records path and randomaccessfile
         * prop_map records path and fileproperties
         * file_map records path and file
         * these hashmaps are private to a thread, so that a client can have full fd space
         */
        private ConcurrentHashMap<String, Integer> fd_map;
        private ConcurrentHashMap<Integer, String> path_map;
        private ConcurrentHashMap<String, RandomAccessFile> raf_map;
        private ConcurrentHashMap<String, FileProperty> prop_map;
        private ConcurrentHashMap<String, File> file_map;

        public FileHandler() {
            this.fd_map = new ConcurrentHashMap<String, Integer>();
            this.path_map = new ConcurrentHashMap<Integer, String>();
            this.raf_map = new ConcurrentHashMap<String, RandomAccessFile>();
            this.prop_map = new ConcurrentHashMap<String, Proxy.FileProperty>();
            this.file_map = new ConcurrentHashMap<String, File>();
        }

        /**
         * concatenate timestamp to make unique paths
         * @param old_path
         * @return
         */
        public synchronized String getUniquePath(String old_path) {
            // get timestamp
            java.util.Date date= new java.util.Date();
            Timestamp ts = new Timestamp(date.getTime());
            // append to original file path
            assert Proxy.origin_map.get(old_path) != null;
            String tsstring = ts.toString().replaceAll("\\s+", "at");
            return proxyrootdir + Proxy.origin_map.get(old_path) + "_at_" + tsstring;
        }

        /**
         * show all the hashmaps for debugging
         */
        public void showHM() {
            System.err.println("================BEGIN HASHMAPS===============");
            System.err.println("this.fd_map");
            System.err.println(this.fd_map.toString());
            System.err.println("this.path_map");
            System.err.println(this.path_map.toString());
            System.err.println("this.raf_map");
            System.err.println(raf_map.toString());
            System.err.println("this.prop_map");
            System.err.println(prop_map.toString());
            System.err.println("version_map");
            System.err.println(Proxy.version_map.toString());
            System.err.println("Proxy.open_map");
            System.err.println(Proxy.open_map.toString());
            System.err.println("Proxy.latest_map");
            System.err.println(Proxy.latest_map.toString());
            System.err.println("Proxy.origin_map");
            System.err.println(Proxy.origin_map.toString());
            System.err.println("===============END   HASHMAPS================");
        }

        /**
         * get the first unused file descriptor
         * @return file descriptor
         */
        private synchronized int getNewFd() {
            // fd starts from 3
            for (int i = 3; ; i++) {
                if (!this.path_map.containsKey(i)) {
                    return i;
                }
            }
        }

        /**
         * get the version of local file
         * @param the path of the file
         * @return the version number at proxy. -1 if the file doesn't exist
         */
        public synchronized int getProxyVersion(String orig_path) {
            if (Proxy.version_map.containsKey(orig_path)) {
                return Proxy.version_map.get(orig_path);
            } else {
                return -1;
            }
        }

        /**
         * forkPrivateCopy is used to create private copies for clients to write
         * in forkPrivateCopy, we make a copy of this file, update hashmaps
         * @param the path of the file to be forked
         * @return new file path
         */
        public synchronized String forkPrivateCopy (String src_path) {
            String new_file_path = getUniquePath(src_path);
            System.err.println("forkPrivateCopy: " + src_path + " copied to " + new_file_path);
            // get size
            long size = 0;
            try {
                File tmp = new File(src_path);
                size = tmp.length();
            } catch (Exception e) {
                e.printStackTrace();
            }
            // check if the cache can hold a new file
            int rv = Proxy.cache.insert(new_file_path, size);
            if (rv == 0) {
                System.err.println("forkPrivateCopy: Enough space. Just insert");
            } else if (rv == -1) {
                System.err.println("forkPrivateCopy: No enough space");
                return null;
            } else if (rv == -2) {//duplicate
                // do nothing
            }
            // copy file
            try {
                File src = new File(src_path);
                File dst = new File(new_file_path);
                Files.copy(src.toPath(), dst.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
            // update the origin_map
            Proxy.origin_map.put(new_file_path, Proxy.origin_map.get(src_path));
            return new_file_path;
        }

        /**
         * this function will get the initial file from server
         * also, it will update the versionmap
         * @param orig_path is the original path of a file
         * @param serverversion is the version at the server
         * @param fsize is the size of the file
         * @return 0 on success, -1 on failure
         */
        public synchronized int getFileFromServer(String orig_path, int serverversion, int fsize) {
            try {
                // get the complete path at the proxy
                String proxy_path = proxyrootdir + orig_path;
                // try to insert into cache
                int rv = Proxy.cache.insert(proxy_path, (int)fsize);
                if (rv == 0) {
                    System.err.println("Enough space. Just insert");
                } else if (rv == -1) {
                    System.err.println("No enough space");
                    return -1;
                }
                RandomAccessFile raf = new RandomAccessFile(proxy_path, "rw");
                // read in chunks
                long cnt = 0;
                while (cnt < fsize) {
                    long bytearraysize = Math.min(CHUNKSIZE, fsize - cnt);
                    byte[] b = Proxy.server.readInChunk(orig_path, cnt, bytearraysize);
                    raf.write(b);
                    cnt += b.length;
                }
                raf.close();
                Proxy.version_map.put(orig_path, serverversion);
                // update hashmaps
                Proxy.latest_map.put(orig_path, proxy_path);
                Proxy.origin_map.put(proxy_path, orig_path);
                Proxy.open_map.put(proxy_path, 0);

            } catch (Exception e) {
                System.err.println("Error in getFileFromServer");
                e.printStackTrace();
            }
            return 0;
        }

        /**
         * get a copy from server when there is already a stale copy at proxy
         * @param orig_path is the original path of a file
         * @param serverversion is the version at the server
         * @param fsize is the size of the file
         * @return 0 on success, -1 on failure
         */
        public synchronized String getFileFromServer2(String orig_path, int serverversion,
                int filesize) {
            String local_path = getUniquePath(proxyrootdir + orig_path);

            try {
                // try to insert into cache
                int rv = Proxy.cache.insert(local_path, (int)filesize);
                if (rv == 0) {
                    System.err.println("getFileFromServer2:Enough space. Just insert");
                } else if (rv == -1) {
                    System.err.println("getFileFromServer2:No enough space");
                    return null;
                }

                RandomAccessFile raf = new RandomAccessFile(local_path, "rw");
                // read in chunks
                long cnt = 0;
                while (cnt < filesize) {
                    long bytearraysize = Math.min(CHUNKSIZE, filesize - cnt);
                    byte[] b = Proxy.server.readInChunk(orig_path, cnt, bytearraysize);
                    raf.write(b);
                    cnt += b.length;
                }
                raf.close();

                Proxy.version_map.put(orig_path, serverversion);
                // update hashmaps
                System.err.println("getFileFromServer2:: " + orig_path + " becomes " + local_path);
                Proxy.latest_map.put(orig_path, local_path);
                Proxy.origin_map.put(local_path, orig_path);
                Proxy.open_map.put(local_path, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return local_path;
        }

        /**
         * create a folder. this function is used to handle nested subdirectory
         * @param folderpath: the path of folder
         * @return void
         */
        public synchronized void createFolder (String folderpath) {
            File file = new File(proxyrootdir + folderpath);
            if (!file.exists()) {
                if (file.mkdir()) {
                    System.out.println("Directory is created!");
                } else {
                    System.out.println("Failed to create directory!");
                }
            }
        }

        /**
         * simplify the file's path, eliminate ".."
         * @param initial path
         * @return canonical path
         */
        public synchronized String simplifyPath(String path) {
            if (path == null) return "/";
            String[] tokens = path.split("/");
            // use a stack to eliminate .. and .
            java.util.LinkedList<String> stk = new java.util.LinkedList<String>();
            for (int i = 0; i < tokens.length; ++i) {
                if (tokens[i].length() == 0 || tokens[i].equals(".")) {
                    continue;
                }
                else if (tokens[i].equals("..")) {
                    if (!stk.isEmpty()) {
                        stk.removeLast();
                    }
                }
                else {
                    stk.add(tokens[i]);
                }
            }
            if (stk.isEmpty()) {
                return "/";
            }
            StringBuilder s = new StringBuilder();
            while (!stk.isEmpty()) {
                s.append("/");
                s.append(stk.remove());
            }
            return s.toString().substring(1, s.toString().length());
        }

        /**
         * open returns fd on success, or errors on failure
         * if the file is a directory, we add the entry in this.file_map
         * if the file is a file, we add the entry in this.raf_map
         */
        public synchronized int open(String orig_path, OpenOption o) {
            /******************************************************************
             * stage 1: check pathname, create subdirectories on demand
             *****************************************************************/
            // simplify path
            orig_path = simplifyPath(orig_path);
            // check if this file already opened.
            if (this.fd_map.containsKey(proxyrootdir + orig_path)) {
                System.err.println("Proxy::open. Already have this file in HashMap");
                return this.fd_map.get(proxyrootdir + orig_path);
            }
            // create directories if needed
            if (orig_path.contains("/")) {
                // find the last position of '/'
                int pos = 0;
                for (int i = 0; i < orig_path.length(); i++) {
                    if (orig_path.charAt(i) == '/') {
                        pos = i;
                    }
                }
                String subdirpath = orig_path.substring(0, pos);
                createFolder(subdirpath);
            }
            /******************************************************************
             * stage 2: get the latest version from server
             *****************************************************************/
            // get the latest path
            String proxy_path = proxyrootdir + orig_path;
            if (Proxy.latest_map.containsKey(orig_path)) {
                proxy_path = Proxy.latest_map.get(orig_path);
            }
            System.err.println(">>>>>>>>>>>>>>>>>>>Proxy:open:: The latest path is " + proxy_path);
            File localfile = null;
            try {
                localfile = new File(proxy_path);
                // if the file exists at proxy, check if it is the latest version
                int[] info = server.getFileInfo(orig_path);
                int server_version = info[0];
                int filesize = info[1];
                if (localfile.exists()) {
                    // compare version
                    int proxy_version = getProxyVersion(orig_path);
                    if (server_version == -1) {
                        System.err.println("Open:File exists at proxy, but not server.");
                        return Errors.ENOENT;
                    } else {
                        if (server_version == proxy_version) {
                            System.err.println("Open:The proxy has latest ver: " + proxy_version);
                            if (o == OpenOption.READ) {
                                // READONLY: DO NOTHING
                            } else {
                                // WRITE: fork a new copy to write on
                                System.err.println("Open:write, need to fork a local copy");
                                proxy_path = forkPrivateCopy(proxy_path);
                                System.err.println("Open:forked file. new file is " + proxy_path);
                            }
                        } else {// the copy is stale
                            System.err.println("Open:The proxy has a stale version" + proxy_version
                                               + ", must fetch " + server_version + "from server");
                            // try to delete the old copy
                            String stale_master_copy = latest_map.get(orig_path);
                            if (!Proxy.isInUse(stale_master_copy)) {
                                Proxy.removeFileWithoutUpdatingCache(stale_master_copy);
                                Proxy.cache.removeNode(stale_master_copy);
                            }
                            // fetch from server
                            proxy_path = getFileFromServer2(orig_path, server_version, filesize);
                            if (proxy_path == null) {
                                return Errors.ENOMEM;
                            }
                            origin_map.put(proxy_path, orig_path);
                            Proxy.version_map.put(orig_path, server_version);
                            if (o == OpenOption.READ) {
                                // then just read on this copy
                                System.err.println("Open::should read on this file: " + proxy_path);
                            } else {
                                // write on a private copy
                                proxy_path = forkPrivateCopy(proxy_path);
                                System.err.println("Open::should write on this: " + proxy_path);
                            }
                        }
                    }
                } else {// the file doesn't exist
                    if (server_version == -1) {// doesn't exist at server or proxy
                        // initialize the Proxy.latest_map and Proxy.origin_map
                        Proxy.origin_map.put(proxy_path, orig_path);
                        Proxy.latest_map.put(orig_path, proxy_path);
                        System.err.println("Open::No such file at server, must create and upload");
                    } else { // exists at server
                        System.err.println("Open::Fetching from server...");
                        if (getFileFromServer(orig_path, server_version, filesize) == -1)
                            return Errors.ENOMEM;
                        Proxy.version_map.put(orig_path, server_version);
                        System.err.println("Open::Now we get ver " + getProxyVersion(orig_path));
                        if (o == OpenOption.READ) {
                            // do nothing
                        } else {
                            // write on private copy
                            proxy_path = forkPrivateCopy(proxy_path);
                            System.err.println("Open::should write on this file: " + proxy_path);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            /******************************************************************
             * stage 3: general process
             * now, the proxy_path should be the file that is actually used
             *****************************************************************/
            System.err.println("Proxy::Open. path is " + proxy_path);
            // 1, check corner cases, and create the file on demand

            File f = null;
            try {
                f = new File(proxy_path);
                // 1, check if the file exists. if so, and if option is CREATE_NEW, return Error
                if (o == OpenOption.CREATE_NEW) {
                    if (f.exists()) {
                        System.err.println("Proxy::open. ERROR: CREATE_NEW + file already exists");
                        return Errors.EEXIST;
                    } else {// should create the file at server and proxy
                        f.createNewFile();
                        Proxy.server.createServerFile(orig_path);
                    }
                }
                // 2, directories can only be opened readonly
                if (f.isDirectory() && (o != OpenOption.READ)) {
                    System.err.println("Open. ERROR: Trying to open a directory for writing");
                    return Errors.EISDIR;
                }
                // 3, for READ and WRITE, the file must have exist
                if (!f.exists()) {
                    if (o == OpenOption.READ || o == OpenOption.WRITE) {
                        System.err.println("Proxy::open. ERROR: READ WRITE + file not exist");
                        return Errors.ENOENT;
                    }
                    f.createNewFile();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 2, check if the fd is already in hashmap
            if (this.fd_map.containsKey(proxy_path)) {
                System.err.println("Proxy::open. Already have this file in HashMap");
                return this.fd_map.get(proxy_path);
            }
            /* 3, enter general process
             * if the file is a directory, just put it into this.file_map
             * if not, create a randomaccessfile and put it into this.raf_map
             */
            if (f.isDirectory()) {
                this.file_map.put(proxy_path, f);
            } else {
                RandomAccessFile raf = null;
                String option = "rw";
                if (o == OpenOption.READ) {
                    option = "r";
                }
                System.err.println("Proxy::open. option is " + option);
                try {
                    raf = new RandomAccessFile(proxy_path, option);
                } catch (Exception e) {
                    System.err.println("Proxy::open. openFile failed...");
                    e.printStackTrace();
                }
                // save the file
                this.raf_map.put(proxy_path, raf);
            }

            int fd = getNewFd();
            // create file property
            FileProperty prop = new FileProperty();
            prop.filename = proxy_path;
            if (o == OpenOption.READ) {
                prop.isReadOnly = true;
            }
            if (f.isDirectory()) {
                prop.isDirectory = true;
            }
            // update hashmaps
            this.fd_map.put(proxy_path, fd);
            this.path_map.put(fd, proxy_path);
            assert this.fd_map.keySet().size() == this.path_map.keySet().size();
            this.prop_map.put(proxy_path, prop);
            // update open_map, increment # of references
            if (Proxy.open_map.containsKey(proxy_path)) {
                Proxy.open_map.put(proxy_path, Proxy.open_map.get(proxy_path)+1);
            } else {
                Proxy.open_map.put(proxy_path, 1);
            }
            System.err.println("Proxy::open. The fd is " + fd);
            Proxy.cache.showCache();
            return fd;
        }

        /**
         * return 0 on success, return Errors.EBADF on failure
         * move the file to head of LRU
         * @param fd: filedescriptor
         * @return canonical return values
         */
        public synchronized int close( int fd ) {
            System.err.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<Proxy::close. fd is " + fd);
            assert this.fd_map.keySet().size() == this.path_map.keySet().size();

            if (this.path_map.containsKey(fd)) { // if the file is already opened
                String proxy_path = this.path_map.get(fd);
                Proxy.cache.get(proxy_path); // move to the head of LRU
                String orig_path = Proxy.origin_map.get(proxy_path);
                if (orig_path == null) orig_path = proxy_path;
                // decrement the reference
                Proxy.open_map.put(proxy_path, Proxy.open_map.get(proxy_path)-1);
                System.err.println("Proxy::close:: the path is " + proxy_path);
                // try to close this file
                if (this.prop_map.get(proxy_path).isDirectory) { // directory, remove from file_map
                    File f = this.file_map.get(proxy_path);
                    this.file_map.remove(f);
                } else { // if it is a file, remove from raf_map
                    RandomAccessFile raf = this.raf_map.get(proxy_path);
                    try {
                        raf.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    FileProperty filep = this.prop_map.get(proxy_path);
                    if (filep.isReadOnly) {//READ
                        // do nothing
                    } else {//WRITE
                        // should upload to server
                        System.err.println("Close:: file modified, need to update in server");
                        try {
                            RandomAccessFile raf_upload = new RandomAccessFile(proxy_path, "r");
                            long filesize = raf_upload.length();
                            server.writeInChunkPrep(orig_path);
                            long cnt = 0;
                            while (cnt < filesize) {
                                long bytearraysize = Math.min(CHUNKSIZE, filesize - cnt);
                                byte[] b = new byte[(int)bytearraysize];
                                raf_upload.read(b);
                                Proxy.server.writeInChunk(orig_path, cnt, b);
                                cnt += bytearraysize;
                            }
                            raf_upload.close();

                            // remove the private copy
                            File f = new File(proxy_path);
                            f.delete();
                            Proxy.removeFileWithoutUpdatingCache(proxy_path);
                            Proxy.cache.removeNode(proxy_path);
                        } catch (Exception e) {
                            System.err.println("Error in close");
                            e.printStackTrace();
                        }
                    }
                }
                // update hashmaps. remove records of this file
                this.fd_map.remove(proxy_path);
                this.path_map.remove(fd);
                this.prop_map.remove(proxy_path);
                this.raf_map.remove(proxy_path);
                System.err.print("****************Close: end");
                Proxy.cache.showCache();
                return 0;
            } else {
                System.err.println("Proxy::close. File hasn't been opened yet. The fd is " + fd);
                return Errors.EBADF;
            }
        }

        /**
         * write a byte array into file. return actual bytes wrote on success
         * @param fd is the filedescriptor
         * @return canonical return value
         */
        public synchronized long write( int fd, byte[] buf) {
            System.err.println("Proxy::write. fd is " + fd);
            // check fd range
            if (fd < 3) {
                System.err.println("Proxy::write. Invalid filedescriptor");
                return Errors.EINVAL;
            }

            if (this.path_map.containsKey(fd)) { // if the file is already opened
                String proxy_path = this.path_map.get(fd);
                // you cannot write to a readonly file or a directory
                if (this.prop_map.get(proxy_path).isReadOnly) {
                    System.err.println("Proxy::write. Trying to write to a readonly file");
                    return Errors.EBADF;
                }
                if (this.prop_map.get(proxy_path).isDirectory) {
                    System.err.println("Proxy::write. Trying to write to a directory");
                    return Errors.EBADF;
                }
                // write to the file
                RandomAccessFile raf = this.raf_map.get(proxy_path);
                System.err.println("Write: proxy_path is " + proxy_path);
                assert raf != null;
                // remember the current position
                long prev_pos = 0;
                try {
                    prev_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    raf.write(buf);
                } catch (Exception e) {
                    e.printStackTrace();
                    return Errors.ENOMEM;
                }
                // check the current position
                long curr_pos = 0;
                try {
                    curr_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return curr_pos - prev_pos;
            } else {
                System.err.println("Proxy:: write. File not opened yet");
                return Errors.EBADF;
            }
        }

        /**
         * read a buf from file. return num of bytes read on success
         * @param fd is the filedescriptor
         * @return canonical return value
         */
        public synchronized long read( int fd, byte[] buf ) {
            System.err.println("Proxy::read. " + "fd is " + fd);
            if (this.path_map.containsKey(fd)) {
                String proxy_path = this.path_map.get(fd);
                // you cannot read a directory
                if (this.prop_map.get(proxy_path).isDirectory) {
                    System.err.println("Proxy:: read. Error! you cannot read a directory");
                    return Errors.EISDIR;
                }

                RandomAccessFile raf = this.raf_map.get(proxy_path);
                long readcnt = 0;
                // remember the previous position
                long prev_pos = 0;
                try {
                    prev_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // read
                try {
                    readcnt = raf.read(buf);
                } catch (Exception e) {
                    e.printStackTrace();
                    return Errors.ENOMEM;
                }
                // get the pos after read
                long curr_pos = 0;
                try {
                    curr_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return curr_pos - prev_pos;
            } else {
                System.err.println("Proxy::read. File not opened yet");
                return Errors.EBADF;
            }
        }

        /**
         * move the pointer to desired position
         * @param fd is the filedescriptor
         * @param pos the position to seek
         * @param o is the lseekoption
         * @return canonical return value
         */
        public synchronized long lseek( int fd, long pos, LseekOption o ) {
            System.err.println("Proxy:: lseek. fd is " + fd + ", pos is " + pos);
            RandomAccessFile raf = null;
            if (this.path_map.containsKey(fd)) {
                String proxy_path = this.path_map.get(fd);
                raf = this.raf_map.get(proxy_path);
            } else {
                System.err.println("Proxy:: lseek. file not opened yet");
                return Errors.EBADF;
            }
            long newpos = 0;
            // try to seek
            try {
                if (o == LseekOption.FROM_CURRENT) {
                    newpos = pos + raf.getFilePointer();
                }
                if (o == LseekOption.FROM_END) {
                    newpos = raf.length() - pos;
                }
                if (o == LseekOption.FROM_START) {
                    newpos = pos;
                }
                raf.seek(newpos);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.err.println("Proxy::lseek. new pos is " + newpos);
            return newpos;
        }

        /**
         * unlink: directly propagate to the server
         * @param fd is the filedescriptor
         * @return canonical return value
         */
        public synchronized int unlink( String orig_path ) {
            System.err.println("Proxy::Unlink. path is " + orig_path);
            try {
                // propagate to server
                if (Proxy.server.removeServerFile(orig_path) == -1) {//fail
                    return Errors.ENOENT;
                } else {
                    return 0;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return 0;
        }

        public void clientdone() {
            return;
        }
    }

    private static class FileHandlingFactory implements FileHandlingMaking {
        public FileHandling newclient() {
            return new FileHandler();
        }
    }

    /**
     * args: ip, port, proxycachedirectory, proxycachesize
     */
    public static void main(String[] args) throws Exception {
        Proxy.CHUNKSIZE = 1000000;// set to 1M

        // set static variables
        Proxy.ip = args[0];
        Proxy.port = Integer.parseInt(args[1]);
        Proxy.proxyrootdir = args[2];
        if (Proxy.proxyrootdir.charAt(Proxy.proxyrootdir.length()-1) != '/') {
            Proxy.proxyrootdir += '/';
        }
        Proxy.proxycachesize = Long.parseLong(args[3]);
        System.err.println("Args are " + args[0] + " " + args[1] + " " + args[2] + " " + args[3]);

        Proxy.cache = new LRUCache(proxycachesize);

        // initialize the versionmap at proxy
        Proxy.version_map = new ConcurrentHashMap<String, Integer>();
        Proxy.latest_map = new ConcurrentHashMap<String, String>();
        Proxy.origin_map = new ConcurrentHashMap<String, String>();
        Proxy.open_map = new ConcurrentHashMap<String, Integer>();

        try {
            Proxy.server = getServerInstance(ip, port);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        (new RPCreceiver(new FileHandlingFactory())).run();
    }
}
