/******************************************************************************
 * author: haileiy@andrew.cmu.edu
 * date:   2015 / 02 / 25
 * 
 * HashMaps:
 * fd_map : 
 * path_map
 * raf_map
 * prop_map
 * file_map
 * copy_map
 * version_map
 * 
 *****************************************************************************/

import java.io.*;
import java.util.*;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.net.URI;

class Proxy {
    /* version_map keeps <orig_path, version> pairs */
    static HashMap<String, Integer> version_map;
    
    /* static variables acquired from command line */
    public static String ip;
    public static int port;
    public static String proxyrootdir;
    public static long proxycachesize;
    public static LRUCache cache;
    public static int locallabel;

    /* inner class. records if the file is readonly or is a directory */
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

    /* RMI related */
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

        HashMap<String, Integer> fd_map;
        HashMap<Integer, String> path_map;
        HashMap<String, RandomAccessFile> raf_map;
        HashMap<String, FileProperty> prop_map;
        HashMap<String, File> file_map;
        HashMap<String, String> copy_map;// store the copy relationship between files
        HashMap<String, String> latest_map;
        HashMap<String, String> origin_map;
        IServer server;
        
        
        // constructor
        public FileHandler() {
            // initialize the hashmaps
            fd_map = new HashMap<String, Integer>();
            path_map = new HashMap<Integer, String>();
            raf_map = new HashMap<String, RandomAccessFile>();
            prop_map = new HashMap<String, FileProperty>();
            file_map = new HashMap<String, File>();
            copy_map = new HashMap<String, String>();
            latest_map = new HashMap<String, String>();
            origin_map = new HashMap<String, String>();
            
            cache = new LRUCache(proxycachesize);
            
            try {
                server = getServerInstance(ip, port);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * return the first valid file descriptor
         */
        private synchronized int getNewFd(String path) {
            for (int i = 3; ; i++) {
                if (!path_map.containsKey(i)) {
                    return i;
                }
            }
        }

        public synchronized void copyFileUsingJava7Files(File source, File dest)
        		throws IOException {
        	Files.copy(source.toPath(), dest.toPath());
        }
        
        /**
         * return the latest local label, and increment it
         */
        
        public synchronized String getLocalPath(String old_path) {
        	locallabel++;
        	return old_path + "_copy_" + Integer.toString(Proxy.locallabel);
        }
        
        public void showHM() {
        	System.err.println("===============================");
        	System.err.println("fd_map");
        	System.err.println(fd_map.toString());
        	System.err.println("path_map");
        	System.err.println(path_map.toString());
        	System.err.println("raf_map");
        	System.err.println(raf_map.toString());
        	System.err.println("prop_map");
        	System.err.println(prop_map.toString());
        	System.err.println("version_map");
        	System.err.println(Proxy.version_map.toString());
        	System.err.println("copy_map");
        	System.err.println(copy_map.toString());
        	System.err.println("latest_map");
        	System.err.println(latest_map.toString());
        	System.err.println("origin_map");
        	System.err.println(origin_map.toString());
        	System.err.println("===============================");
        }
        /**
         * get the version of local file
         */
        public synchronized int getProxyVersion(String path) {
            if (Proxy.version_map.containsKey(path)) {
                return Proxy.version_map.get(path);
            } else {
                return -1;
            }
        }

        /** 
         * forkFile is used to create private copies for clients to write
         * in forkFile, we make a copy of this file, update, update
         * origin_map, 
         */
        public synchronized String forkFile (String src_path) {
        	String localpath = getLocalPath(src_path);
        	System.err.println("forkFile: orig path is " + src_path + "new path is " + localpath);
        	try {
        		File src = new File(src_path);
        		File dst = new File(localpath);
        		Files.copy(src.toPath(), dst.toPath());
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        	origin_map.put(localpath, origin_map.get(src_path));
        	return localpath;
        }
        /**
         * this function will get the file from server, then write it to local directory
         * also, it will update the versionmap
         */
        public int getFileFromServer(String orig_path) {
            System.err.println("Proxy::getFileFromServer");
            try {
                byte[] b = server.getFileContent(orig_path);
                // get the complete path at the proxy
                String proxy_path = proxyrootdir + orig_path;
                // write the byte array to the file
                FileOutputStream fos = new FileOutputStream(proxy_path);

                fos.write(b);
                fos.close();
                
                Proxy.version_map.put(orig_path, server.getVersion(orig_path));
                // cache
                latest_map.put(orig_path, proxy_path);
                origin_map.put(proxy_path, orig_path);
                cache.set(proxy_path, b.length);
            } catch (Exception e) {
                System.err.println("Error in getFileFromServer");
                e.printStackTrace();
            }
            return 0;
        }
        
        /**
         * get a copy from server
         */
        public String getFileFromServer2(String orig_path) {
        	System.err.println("Proxy::getFileFromServer2");
        	String local_path = getLocalPath(proxyrootdir + orig_path);
        	try {
                byte[] b = server.getFileContent(orig_path);
                // get the complete path at the proxy
                String proxy_path = proxyrootdir + orig_path;
                // write the byte array to the file
                FileOutputStream fos = new FileOutputStream(local_path);

                fos.write(b);
                fos.close();
                
                Proxy.version_map.put(orig_path, server.getVersion(orig_path));
                cache.set(proxy_path, b.length);
                // update hashmaps
                System.err.println("getFileFromServer2:: origin and new path are " + orig_path + " " + local_path);
                latest_map.put(orig_path, local_path);
                origin_map.put(local_path, orig_path);
            } catch (Exception e) {
                System.err.println("Error in getFileFromServer");
                e.printStackTrace();
            }
        	return local_path;
        }

        /**
         * create a folder
         */
        public static void createFolder (String folderpath) {
            File file = new File(proxyrootdir + folderpath);
            System.err.println("Createfolder:: " + proxyrootdir + folderpath);
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
         */
        public String simplifyPath(String path) {
            if (path == null) return "/";
            String[] tokens = path.split("/");
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
         * if the file is a directory, we add the entry in file_map
         * if the file is a file, we add the entry in raf_map
         */
        public int open(String orig_path, OpenOption o) {
        	/******************************************************************
        	 * stage 1: check pathname, create subdirectories on demand
        	 *****************************************************************/
        	System.err.println("Very first orig_path is " + orig_path);
        	orig_path = simplifyPath(orig_path);
        	System.err.println("simplified orig_path is " + orig_path);
            if (orig_path.contains("/")) {
            	// find the last position of '/'
            	int pos = 0;
            	for (int i = 0; i < orig_path.length(); i++) {
            		if (orig_path.charAt(i) == '/') {
            			pos = i;
            		}
            	}
            	String subdirpath = orig_path.substring(0, pos);
            	// creat the folder specified in the path
            	createFolder(subdirpath);
            }
            /******************************************************************
        	 * stage 2: get the latest version from server
        	 *****************************************************************/
            // get the latest path
            String curr_path = "";
            String proxy_path = proxyrootdir + orig_path;
            if (latest_map.containsKey(orig_path)) {
            	proxy_path = latest_map.get(orig_path);
            }
            System.err.println("The latest path is " + proxy_path);
            File localfile = null;
            try {
                localfile = new File(proxy_path);
                // if the file exists at proxy, check if it is the latest version
                if (localfile.exists()) {
                	// compare version
                    int server_version = server.getVersion(orig_path);
                    int proxy_version = getProxyVersion(orig_path);
                    if (server_version == -1) {
                        System.err.println("Open::File exists at proxy, but not server. This is weird. ");
                        // create one and upload
                    } else {
                        if (server_version == proxy_version) {
                            System.err.println("Open::The proxy has a up-to-date version: version " + proxy_version);
                            if (o == OpenOption.READ) {
                            	// READONLY: DO NOTHING
                            } else {
                            	// WRITE: fork a new copy
                            	System.err.println("Open:: write, need to fork a local copy");
                            	proxy_path = forkFile(proxy_path);
                            	System.err.println("forked file. the new file path is " + proxy_path);
                            }
                        } else {// not the latest
                            System.err.println("Open::The proxy has a stale version" + proxy_version + ", must fetch " + server_version + "from server");
                            // fetch from server
                            proxy_path = getFileFromServer2(orig_path);
                            Proxy.version_map.put(orig_path, server_version);
                            latest_map.put(orig_path, proxy_path);
                            if (o == OpenOption.READ) {
                            	
                            	// then just read on this copy
                            	System.err.println("Open::should read on this file: " + proxy_path);
                            } else {
                            	proxy_path = forkFile(proxy_path);
                            	System.err.println("Open::should write on this file: " + proxy_path);
                            }
                        }
                    }
                } else {// the file doesn't exist
                    int server_version = server.getVersion(orig_path);
                    if (server_version == -1) {
                    	origin_map.put(proxy_path, orig_path);
                    	latest_map.put(orig_path, proxy_path);
                        System.err.println("Open::No such file at server, must create one and upload it");
                    } else {
                        System.err.println("Open::File exists at server, but no local copy, fetching from server...");
                        getFileFromServer(orig_path);
                        Proxy.version_map.put(orig_path, server_version);
                        System.err.println("Open::Now we have file of version " + getProxyVersion(orig_path));
                        if (o == OpenOption.READ) {
                        	// do nothing
                        } else {
                        	proxy_path = forkFile(proxy_path);
                        	System.err.println("Open::should write on this file: " + proxy_path);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            /******************************************************************
        	 * stage 3: create private copies
        	 *****************************************************************/

            
            /******************************************************************
        	 * stage 4: general process
        	 * now, the proxy_path should be the file that is actually used
        	 *****************************************************************/
            System.err.println("Proxy::Open. path is " + proxy_path);
            // 1, check corner cases, and create the file on demand

            File f = null;
            try {
                f = new File(proxy_path);
                // 1, check if the file already exists. if so, and if option is CREATE_NEW, return Error
                if (o == OpenOption.CREATE_NEW && f.exists()) {
                    System.err.println("Proxy::open. ERROR: CREATE_NEW + file already exists");
                    return Errors.EEXIST;
                }
                // 2, directories can only be opened readonly
                if (f.isDirectory() && (o != OpenOption.READ)) {
                    System.err.println("Proxy::open. ERROR: Trying to open a directory for writing");
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
            if (fd_map.containsKey(proxy_path)) {
                System.err.println("Proxy::open. Already have this file in HashMap");
                return fd_map.get(proxy_path);
            }
            /* 3, enter general process
             * if the file is a directory, just put it into file_map
             * if not, create a randomaccessfile and put it into raf_map
             */
            if (f.isDirectory()) {
                file_map.put(proxy_path, f);
            } else {
                RandomAccessFile raf = null;
                String option = "rw";
                if (o == OpenOption.READ) {
                    option = "r";
                }
                System.err.println("Proxy::open. option is " + option);
                try {
                    raf = new RandomAccessFile(proxy_path, option);
                } catch (IOException e) {
                    System.err.println("Proxy::open. openFile failed...");
                    e.printStackTrace();
                }
                // save the file
                raf_map.put(proxy_path, raf);
            }

            int fd = getNewFd(proxy_path);
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
            fd_map.put(proxy_path, fd);
            path_map.put(fd, proxy_path);
            prop_map.put(proxy_path, prop);

            System.err.println("Proxy::open. The fd is " + fd);
            showHM();
            return fd;
        }

        /**
         * return 0 on success, return Errors.EBADF on failure
         * TODO: do i need to check if this is my file? How?
         */
        public int close( int fd ) {
            System.err.println("Proxy::close. fd is " + fd);

            if (path_map.containsKey(fd)) {
            	
                String proxy_path = path_map.get(fd);
                String orig_path = origin_map.get(proxy_path);
                //String proxy_path = proxyrootdir + orig_path;
                System.err.println("Close:: the path is " + proxy_path);
                // try to close this file
                if (prop_map.get(proxy_path).isDirectory) {
                    File f = file_map.get(proxy_path);
                    file_map.remove(f);
                } else {
                    RandomAccessFile raf = raf_map.get(proxy_path);
                    try {
                        raf.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // update propagate

                    try {
                        int server_version = server.getVersion(origin_map.get(proxy_path));
                        int proxy_version = getProxyVersion(origin_map.get(proxy_path));
                        if (proxy_version > server_version) { // this is tricky
                            // need to update
                            System.err.println("Close::The file at proxy is modified, need to update in server");
                            File uploadfile = new File(proxy_path);
                            System.err.println("The file is " + uploadfile + "of version " + proxy_version);
                            //System.err.println("Delete the proxy's write private copy");
                            
                            byte[] uploadb = new byte[(int) uploadfile.length()];
                            FileInputStream fileInputStream = new FileInputStream(uploadfile);
                            fileInputStream.read(uploadb);
                            if (orig_path == null) orig_path = proxy_path;
                            server.writeToServer(orig_path, uploadb);
                            //System.err.println(Arrays.toString(uploadb));
                            latest_map.put(orig_path, proxy_path);
                        }
                    } catch (Exception e) {
                        System.err.println("Error in close");
                        e.printStackTrace();
                    }
                }
                // update hashmaps
                fd_map.remove(proxy_path);
                path_map.remove(fd);
                prop_map.remove(proxy_path);
                //origin_map.remove(proxy_path);
                raf_map.remove(proxy_path);
                showHM();
                return 0;
            } else {
                System.err.println("Proxy::close. File hasn't been opened yet");
                return Errors.EBADF;
            }
        }

        /**
         * write a byte array into file. return actual bytes wrote on success
         * return EBADF on failure
         */
        public long write( int fd, byte[] buf) {
            System.err.println("Proxy::write. fd is " + fd);

            // check fd range
            if (fd < 3) {
                System.err.println("Proxy::write. Invalid filedescriptor");
                return Errors.EINVAL;
            }

            if (path_map.containsKey(fd)) {

                // you cannot write to a readonly file or a directory
                if (prop_map.get(path_map.get(fd)).isReadOnly) {
                    System.err.println("Proxy::write. Trying to write to a readonly file");
                    return Errors.EBADF;
                }

                if (prop_map.get(path_map.get(fd)).isDirectory) {
                    System.err.println("Proxy::write. Trying to write to a directory");
                    return Errors.EBADF;
                }

                // update versionmap
                String proxy_path = path_map.get(fd);
                String orig_path = origin_map.get(proxy_path);
                if (Proxy.version_map.containsKey(orig_path)) {
                    Proxy.version_map.put(orig_path, Proxy.version_map.get(orig_path) + 1);
                    System.err.println("Proxy::write. The version has been updated to " + Proxy.version_map.get(orig_path));
                } else {
                    Proxy.version_map.put(orig_path, 1);
                    System.err.println("Proxy::write. The version is 1");
                }
                RandomAccessFile raf = raf_map.get(proxy_path);
                // remember the current position
                long prev_pos = 0;
                try {
                    prev_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //long write_cnt = 0;
                try {
                    raf.write(buf);
                } catch (IOException e) {
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
         * return
         */
        public long read( int fd, byte[] buf ) {
            System.err.println("Proxy::read. " + "fd is " + fd);
            if (path_map.containsKey(fd)) {
                String orig_path = path_map.get(fd);
                String proxy_path = proxyrootdir + orig_path;
                // you cannot read a directory
                if (prop_map.get(orig_path).isDirectory) {
                    System.err.println("Proxy:: read. Error! you cannot read a directory");
                    return Errors.EISDIR;
                }

                RandomAccessFile raf = raf_map.get(orig_path);
                long readcnt = 0;

                long prev_pos = 0;
                try {
                    prev_pos = raf.getFilePointer();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    readcnt = raf.read(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    return Errors.ENOMEM;
                }

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
         */
        public long lseek( int fd, long pos, LseekOption o ) {
            System.err.println("Proxy:: lseek. fd is " + fd + ", pos is " + pos);
            RandomAccessFile raf = null;
            if (path_map.containsKey(fd)) {
                String orig_path = path_map.get(fd);
                raf = raf_map.get(orig_path);
            } else {
                System.err.println("Proxy:: lseek. file not opened yet");
                return Errors.EBADF;
            }
            long newpos = 0;
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
         * TODO need to close the file first?
         */
        public int unlink( String orig_path ) {
            System.err.println("Proxy::Unlink. path is " + orig_path);
            String proxy_path = proxyrootdir + orig_path;
            File f = null;
            try {
                f = new File(proxy_path);
                if (f.exists()) {
                    f.delete();
                    return 0;
                } else {
                    System.err.println("Proxy::unlink. file doesn't exist at all");
                    return Errors.ENOENT;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return Errors.ENOENT;
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
    public static void main(String[] args) throws IOException {
        // set static variables 
        Proxy.ip = args[0];
        Proxy.port = Integer.parseInt(args[1]);
        Proxy.proxyrootdir = args[2];
        if (Proxy.proxyrootdir.charAt(Proxy.proxyrootdir.length()-1) != '/') {
            Proxy.proxyrootdir += '/';
        }
        Proxy.proxycachesize = Long.parseLong(args[3]);
        Proxy.locallabel = 0;
        // initialize the versionmap at proxy
        Proxy.version_map = new HashMap<String, Integer>();
        (new RPCreceiver(new FileHandlingFactory())).run();
    }
}
