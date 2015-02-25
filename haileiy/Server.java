import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.HashMap;
import java.io.*;
import java.util.*;
//You should investigate when to use UnicastRemoteObject vs Serializable. This is really important!
public class Server extends UnicastRemoteObject implements IServer, Serializable {

    public static String serverpath;
    public static int serverport;

    public Server() throws RemoteException {
        versionMap = new HashMap<String, Integer>();
    }

    public String sayHello() throws RemoteException {
        return "Hello :)";
    }

    public int getVersion(String orig_path) throws RemoteException {
        System.err.println("Server::getVersion");
        int ver = 0;
        try {
            if (versionMap.containsKey(orig_path)) {
                ver = versionMap.get(orig_path);
            } else {
                ver = -1;
            }
        } catch (Exception e) {
            System.err.println("Error in getVersion");
        }
        return ver;
    }

    public File getFile(String orig_path) throws RemoteException {
        System.err.println("Server::getFile");
        // return the file
        try {
            String localpath = serverpath + orig_path;
            File file = new File(localpath);
            return file;
        } catch (Exception e) {
            System.err.println("Error in getFile");
            return null;
        }
    }

    /**
     * this function will write a byte array to a file within the server's cache directory
     */
    public void writeToServer (String orig_path, byte[] b) throws RemoteException {
        System.err.println("Server::writeToServer");

        // update versionmap first

        if (versionMap.containsKey(orig_path)) {
            versionMap.put(orig_path, versionMap.get(orig_path) + 1);
            System.err.println("The new version num is " + versionMap.get(orig_path));
        } else {
            versionMap.put(orig_path, 1);
        }

        // if the file already exists, remove it and write

        // then, write the byte array to file
        String localpath = serverpath + orig_path;
        File file = new File(localpath);

        // check if the file already exists
        if (file.exists()) {
            System.err.println("The file already exists, need to delete it first, then write");
            System.err.println("The file is " + localpath + "of version " + versionMap.get(orig_path));
            if(file.delete()) {
                System.err.println(file.getName() + " is deleted!");
            } else {
                System.err.println("Delete operation failed.");
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(localpath);
            fos.write(b);
            fos.close();
        } catch (Exception e) {
            System.err.println("Error while writing to servercache");
        }
    }

    /**
     * this function will get the content of a file provided its path
     */
    public byte[] getFileContent(String orig_path) throws RemoteException {
        System.err.println("Server::getFileContent");
        String localpath = serverpath + orig_path;

        File file = new File(localpath);

        byte[] b = new byte[(int) file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(b);

        } catch (FileNotFoundException e) {
            System.err.println("File Not Found.");
            e.printStackTrace();
        }
        catch (IOException e1) {
            System.err.println("Error Reading The File.");
            e1.printStackTrace();
        }
        return b;
    }

    public static HashMap<String, Integer> versionMap;

    public static void main(String [] args) {
        System.err.println("Cache server has started");
        serverport = Integer.parseInt(args[0]);
        serverpath = args[1];
        if (serverpath.charAt(serverpath.length()-1) != '/') {
            serverpath += '/';
        }

        try {
            //create the RMI registry if it doesn't exist.
            LocateRegistry.createRegistry(serverport);
        }
        catch(RemoteException e) {
            System.err.println("Failed to create the RMI registry " + e);
        }

        Server server = null;
        try {
            server = new Server();
            final File folder = new File(serverpath);
            for (final File fileEntry : folder.listFiles()) {
                System.err.println(fileEntry.getName());
                versionMap.put(fileEntry.getName(), 0);// initialize the version to 0
            }
        }
        catch(RemoteException e) {
            //You should handle errors properly.
            System.err.println("Failed to create server " + e);
            System.exit(1);
        }

        try {
            Naming.rebind(String.format("//127.0.0.1:%d/ServerService", serverport), server);
        } catch (RemoteException e) {
            System.err.println(e); //you probably want to do some decent logging here
        } catch (MalformedURLException e) {
            System.err.println(e); //same here
        }
    }
}