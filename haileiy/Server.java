import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;

public class Server extends UnicastRemoteObject implements IServer, Serializable {
    private static final long serialVersionUID = 1L;
    // static variables
    public static String serverrootdir;
    public static int serverport;
    public static ConcurrentHashMap<String, Integer> versionMap;
    // constructor
    public Server() throws RemoteException {
        versionMap = new ConcurrentHashMap<String, Integer>();
    }

    /******************************************************************************
     * the following functions are for RMI
     *****************************************************************************/
    /**
     * remove a file at server
     * @param orig_path the original form of the file's path
     * @return 0 on success, -1 on failure
     */
    public synchronized int removeServerFile(String orig_path) throws RemoteException {
        System.err.println("Server::unlink");
        // update version map
        if (versionMap.containsKey(orig_path)) {
            versionMap.remove(orig_path);
        }
        // delete the file
        String server_path = serverrootdir + orig_path;
        File f = null;
        try {
            f = new File(server_path);
            if (f.exists()) {
                f.delete();
                return 0;
            } else {
                System.err.println("Error:Proxy:unlink. file doesn't exist at all");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * get version number of a file. -1 represents non-existency
     * @param orig_path the original form of the file's path
     * @return 0 on success, -1 on failure
     */
    public int[] getFileInfo(String orig_path) throws RemoteException {
        int ver = 0;//version
        int size = 0;//file size
        try {
            File f = new File(serverrootdir + orig_path);
            size = (int)f.length();
            if (versionMap.containsKey(orig_path)) {
                ver = versionMap.get(orig_path);
            } else {
                ver = -1;
            }
        } catch (Exception e) {
            System.err.println("Error in getVersion");
        }
        System.err.println("Server::getVersion for file " + orig_path + " is " + ver);
        // put the version and size in an array
        int[] ret = new int[2];
        ret[0] = ver;
        ret[1] = size;
        return ret;
    }

    /**
     * do some preparation works for writing in chunks
     * @param orig_path the original form of the file's path
     * @return void
     */

    public synchronized void writeInChunkPrep(String orig_path) throws RemoteException {
        System.err.println("Server::writeToServerPrep");
        String server_path = serverrootdir + orig_path;
        // update version
        if (versionMap.containsKey(orig_path)) {
            versionMap.put(orig_path, versionMap.get(orig_path) + 1);
            System.err.println("The new version num is " + versionMap.get(orig_path));
        } else {
            versionMap.put(orig_path, 1);
        }
        // delete old, stale file
        File file = new File(server_path);
        if (file.exists()) {
            System.err.println("The file already exists, need to delete it first, then write");
            System.err.println("File is " + server_path + "of ver " + versionMap.get(orig_path));
            if(file.delete()) {
                System.err.println(file.getName() + " is deleted!");
            } else {
                System.err.println("Delete operation failed.");
            }
        }
    }

    /**
     * write in small chunks.
     * @param orig_path the original form of the file's path
     * @return 0 on success, -1 on failure
     */
    public void writeInChunk(String orig_path, long start_offset, byte[] b) throws RemoteException {
        System.err.println("Server::writeInChunk");

        String server_path = serverrootdir + orig_path;
        try {
            RandomAccessFile raf = new RandomAccessFile(server_path, "rw");
            // locate to the position, and write
            raf.seek(start_offset);
            raf.write(b);
            raf.close();
        } catch (Exception e) {
            System.err.println("Error while writing to servercache in chunks");
        }
    }

    /**
     * read in small chunks.
     * @param orig_path the original form of the file's path
     * @return the byte array read from file
     */
    public byte[] readInChunk(String path, long start_offset, long size) throws RemoteException {
        System.err.println("Server::readInChunk");
        String server_path = serverrootdir + path;
        byte[] b = new byte[(int)size];
        RandomAccessFile raf = null;
        try {
            // locate to the position, and read a chunk
            raf = new RandomAccessFile(server_path, "r");
            raf.seek(start_offset);
            raf.read(b);
            raf.close();
            return b;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return b;
    }


    /**
     * return 0 on success, -1 on failure
     * create a new file at server
     * @param orig_path the original form of the file's path
     * @return 0 on success, -1 on failure
     */
    public int createServerFile(String orig_path) throws RemoteException {
        System.err.println("Server:createFile " + orig_path);
        String server_path = serverrootdir + orig_path;
        File file = new File(server_path);
        try {
            file.createNewFile();
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }


    /******************************************************************************
     * the following functions are for server to use locally
     *****************************************************************************/
    /**
     * this function is used when server initializes
     * it recursively adds all the files to the versionmap, and initialize their version to 0
     * @param folder: the folder to list
     */
    public static void listFilesForFolder (final File folder, String superfolder) {
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                String folderpath = "";
                if (superfolder.length() > 0) {
                    folderpath = superfolder + "/" + fileEntry.getName();
                } else {
                    folderpath = fileEntry.getName();
                }
                listFilesForFolder(fileEntry, folderpath);
            } else {
                String filename = "";
                if (superfolder.length() > 0) {
                    filename = superfolder + "/" + fileEntry.getName();
                } else {
                    filename = fileEntry.getName();
                }
                System.err.println(filename);
                versionMap.put(filename, 0);
            }
        }
    }

    /**
     * two args
     * @param args
     */
    public static void main(String [] args) {
        System.err.println("Cache server has started");
        // get args
        serverport = Integer.parseInt(args[0]);
        serverrootdir = args[1];
        if (serverrootdir.charAt(serverrootdir.length()-1) != '/') {
            serverrootdir += '/';
        }
        // RMI registry
        try {
            //create the RMI registry if it doesn't exist.
            LocateRegistry.createRegistry(serverport);
        }
        catch(RemoteException e) {
            System.err.println("Failed to create the RMI registry " + e);
        }
        // create server object, and initialize versionmap
        Server server = null;
        try {
            server = new Server();
            final File folder = new File(serverrootdir);
            listFilesForFolder(folder, "");
        }
        catch(RemoteException e) {
            System.err.println("Failed to create server " + e);
            System.exit(1);
        }

        try {
            Naming.rebind(String.format("//127.0.0.1:%d/ServerService", serverport), server);
        } catch (RemoteException e) {
            System.err.println(e);
        } catch (MalformedURLException e) {
            System.err.println(e);
        }
    }
}
