import java.rmi.Remote;
import java.rmi.RemoteException;
import java.io.*;

public interface IServer extends Remote {
    public int[] getVersion(String path) throws RemoteException;
    public int removeServerFile(String orig_path) throws RemoteException;
    public int createServerFile(String orig_path) throws RemoteException;
    public byte[] readInChunk(String path, long start_offset, long readsize) throws RemoteException;
    public void writeInChunk(String path, long start_offset, byte[] b) throws RemoteException;
    public void writeInChunkPrep(String path) throws RemoteException;
}
