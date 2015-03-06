import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.*;

public interface IServer extends Remote {
    public int getVersion(String path) throws RemoteException;
    public long getFileSize(String path) throws RemoteException;
    public File getFile(String path) throws RemoteException;
    public byte[] getFileContent(String path) throws RemoteException;
    public void writeToServer (String path, byte[] b) throws RemoteException;
    public int removeFile(String orig_path) throws RemoteException;
    public int createFile(String orig_path) throws RemoteException;
}
