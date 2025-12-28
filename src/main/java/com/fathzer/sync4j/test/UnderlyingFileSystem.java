package com.fathzer.sync4j.test;

import java.io.IOException;

import com.fathzer.sync4j.File;

/**
 * The underlying file system.
 * <br>This interface is used to assert that the file provider and the underlying file system are in sync.
 */
public interface UnderlyingFileSystem {
    /**
     * Create a file.
     * @param path the path of the file to create (relative to the root folder - e.g. "/folder/file.txt")
     * @throws IOException if an I/O error occurs, typically if the file already exists or the parent folder does not exist
     */
    void createFile(String path) throws IOException;
    
    /**
     * Delete a file.
     * @param path the path of the file to delete (relative to the root folder - e.g. "/folder/file.txt")
     * @throws IOException if an I/O error occurs, typically if the file does not exist
     */
    void deleteFile(String path) throws IOException;
    
    /**
     * Delete a folder.
     * <br>If called on a folder, the implementor can assume that the folder is empty.
     * @param path the path of the folder to delete (relative to the root folder - e.g. "/folder/subfolder")
     * @throws IOException if an I/O error occurs, typically if the folder does not exist
     */
    void deleteFolder(String path) throws IOException;

    /**
     * Create a folder.
     * @param path the path of the folder to create (relative to the root folder - e.g. "/folder")
     * @throws IOException if an I/O error occurs, typically if the folder already exists or the parent folder does not exist
     */
    void createFolder(String path) throws IOException;
    
    /**
     * Assert that the file exists and its content is equal to the given file.
     * @param path the path of the file to assert (relative to the root folder - e.g. "/folder/file.txt")
     * @param file the file to compare to
     * @throws IOException if an I/O error occurs, typically if the file does not exist
     */
    void assertUnderlyingFileEquals(String path, File file) throws IOException;
    
    /**
     * Checks if the folder exists.
     * @param path the path of the folder to check (relative to the root folder - e.g. "/folder")
     * @return true if the folder exists, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean underlyingFolderExists(String path) throws IOException;
}