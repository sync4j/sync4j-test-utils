package com.fathzer.sync4j.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.Folder;

/** A try to create a common test for file providers (except for non-writable providers).
 * @see AbstractNonWritableFileProviderTest
*/
public abstract class AbstractFileProviderTest {
    /** An annotation to declare there's no write support expected. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NoWriteSupport {
    }

    /**
     * The underlying file system.
     * <br>This interface is used to assert that the file provider and the underlying file system are in sync.
     */
    public interface UnderlyingFileSystem {
        /**
         * Create a file.
         * @param path the path of the file to create (relative to the root folder - e.g. "/folder/file.txt")
         * @throws IOException if an I/O error occurs
         */
        void createFile(String path) throws IOException;
        
        /**
         * Delete a file or a folder.
         * <br>If called on a folder, the implementor can assume that the folder is empty.
         * @param path the path of the file or folder to delete (relative to the root folder - e.g. "/folder/file.txt")
         * @throws IOException if an I/O error occurs
         */
        void delete(String path) throws IOException;
        
        /**
         * Create a folder.
         * @param path the path of the folder to create (relative to the root folder - e.g. "/folder")
         * @throws IOException if an I/O error occurs
         */
        void createFolder(String path) throws IOException;
        
        /**
         * Assert that the file exists and its content is equal to the given file.
         * @param path the path of the file to assert (relative to the root folder - e.g. "/folder/file.txt")
         * @param file the file to compare to
         * @throws IOException if an I/O error occurs
         */
        void assertUnderlyingFileEquals(String path, File file) throws IOException;
        
        /**
         * Assert that the folder exists.
         * @param path the path of the folder to assert (relative to the root folder - e.g. "/folder")
         * @throws IOException if an I/O error occurs
         */
        void assertUnderlyingFolderExist(String path) throws IOException;
    }

    /** The root folder.
     * <br>This folder is created in the setup method.
     */
    protected Folder root;
    /** The file provider.
     * <br>This provider is created in the setup method.
     */
    protected FileProvider provider;
    /** The underlying file system.
     * <br>This file system is created in the setup method by calling {@link #getUnderlyingFileSystem()}. 
     */
    protected UnderlyingFileSystem ufs;

    protected abstract FileProvider createFileProvider() throws IOException;

    /**
     * Returns the underlying file system on which the file provider is based.
     * @return the underlying file system, or null if the provider doesn't reflect an underlying file system (for instance for in-memory providers)
     */
    protected abstract UnderlyingFileSystem getUnderlyingFileSystem();

    @BeforeEach
    protected void setup() throws IOException {
        provider = createFileProvider();
        root = provider.get(FileProvider.ROOT_PATH).asFolder();
        ufs = getUnderlyingFileSystem();
    }

    @AfterEach
    void teardown() {
        root.getFileProvider().close();
    }

    protected static File createMockFile(String content) throws IOException {
        File result = Mockito.mock(File.class);
        Mockito.lenient().when(result.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        return result;
    }

    protected boolean isCreationTimeImmutable() throws IOException {
        return false;
    }

    protected Folder getAFolder() throws IOException {
        Entry entry = provider.get("/folder");
        if (entry.isFolder()) {
            return entry.asFolder();
        }
        return root.mkdir("folder");
    }

    protected File getAFile() throws IOException {
        Entry entry = provider.get("/folder/file.txt");
        if (entry.isFile()) {
            return entry.asFile();
        }
        return getAFolder().copy("file.txt", createMockFile("content"), null);
    }

    protected Entry getMissingEntry(String prefix) throws IOException {
        for (int i = 0; i < 100; i++) {
            Entry entry = provider.get(prefix + "/missing" + i);
            if (!entry.exists()) {
                return entry;
            }
        }
        throw new IOException("Not able to find a missing entry named /" + prefix + "/missingX where X is a < 100");
    }

    @Test
    protected void testRoot() throws IOException {
        assertTrue(root.exists(), "Root should exist");
        assertTrue(root.isFolder(), "Root should be a folder");
        assertFalse(root.isFile(), "Root should not be a file");
        assertEquals("", root.getName(), "Root should have no name");
        assertNull(root.getParent(), "Root should have no parent");
        assertThrows(IOException.class, () -> root.delete(), "Root should not be deleted");
    }

    @Test
    protected void testGet() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> provider.get("/folder//file.txt"), "Invalid path (double slash) should not be retrieved");
        assertThrows(IllegalArgumentException.class, () -> provider.get("folder/file.txt"), "Invalid path (no leading slash) should not be retrieved");

        // Check inconsistent path does not throw any exception and returns a non existing entry
        Entry file = getAFile();
        Entry inconsistentPathFile = provider.get(file.getPath() + "/toto.txt");
        assertFalse(inconsistentPathFile.exists());
    }

    @Test
    protected void testGetParent() throws IOException {
        // Check get parent on missing file does not throw IOException
        Entry missingFolder = getMissingEntry("");
        assertFalse(missingFolder.exists());
        Entry file = provider.get(missingFolder.getPath() + "/file.txt");
        assertDoesNotThrow(file::getParent, "Parent of a missing file should be retrieved");
        
        // Check get parent on existing file
        file = getAFile();
        assertTrue(file.exists());
        Entry parent = file.getParent();
        assertEquals(parent.getPath() + "/" + file.getName(), file.getPath());

        // Check parent on missing entry with a file as parent
        file = getMissingEntry(file.getPath());
        parent = file.getParent();
        assertTrue(parent.isFile());
    }

    @Test
    protected void testGetProvider() throws IOException {
        assertSame(provider, root.getFileProvider());
        Folder folder = getAFolder();
        assertSame(provider, folder.getFileProvider());
        File file = getAFile();
        assertSame(provider, file.getFileProvider());
        Entry entry = getMissingEntry("/folder");
        assertSame(provider, entry.getFileProvider());
    }

    @Test
    protected void testAsFileAndAsFolder() throws IOException {
        File file = getAFile();
        assertDoesNotThrow(file::asFile);
        assertThrows(IllegalStateException.class, file::asFolder);

        Folder folder = getAFolder();
        assertDoesNotThrow(folder::asFolder);
        assertThrows(IllegalStateException.class, folder::asFile);

        Entry entry = getMissingEntry("");
        assertThrows(IllegalStateException.class, entry::asFile);
        assertThrows(IllegalStateException.class, entry::asFolder);
    }

    @Test
    protected void testFolderList() throws IOException {
        Folder parent = root.mkdir(getMissingEntry(FileProvider.ROOT_PATH).getName());
        try {
            parent.copy("file1.txt", createMockFile("content1"), null);
            parent.copy("file2.txt", createMockFile("content2"), null);
            parent.mkdir("subfolder");

            Entry entry = provider.get(parent.getPath());
            Folder folder = entry.asFolder();
            List<Entry> children = folder.list();

            assertEquals(3, children.size(), "Should have 3 children");
        } finally {
            parent.delete();
        }
    }

    @Test
    protected void testFolderMkdir() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider is not writable");
        Folder parent = root.mkdir(getMissingEntry(FileProvider.ROOT_PATH).getName());
        try {
            Folder newFolder = parent.mkdir("child");
            assertEquals("child", newFolder.getName());
            assertTrue(newFolder.exists());
            assertTrue(newFolder.isFolder());

            // Verify the folder is created in the file provider
            Entry retrieved = provider.get(parent.getPath() + "/child");
            assertTrue(retrieved.exists());
            assertTrue(retrieved.isFolder());

            assertThrows(IOException.class, () -> parent.mkdir("child"), "Should throw IOException when folder already exists");

            // Check what happens if there is a file with the same name
            parent.copy("child.txt", createMockFile("content"), null);
            assertThrows(IOException.class, () -> parent.mkdir("child"), "Should throw IOException when a file with the same name exists");
            
            // Check illegal file names
            assertThrows(IllegalArgumentException.class, () -> parent.mkdir(""), "Should throw for empty name");
            assertThrows(IllegalArgumentException.class, () -> parent.mkdir("name/with/slash"), "Should throw for name with slash");
        } finally {
            parent.delete();
        }
    }
    
    @Test
    protected void testFolderCopy() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider is not writable");
        Folder dest = root.mkdir(getMissingEntry(FileProvider.ROOT_PATH).getName());
        try {
            String content = "test content";
            File sourceFile = createMockFile(content);
            Mockito.when(sourceFile.getCreationTime()).thenReturn(123456789L);
            Mockito.when(sourceFile.getLastModifiedTime()).thenReturn(167654321L);

            File copiedFile = dest.copy("copied.txt", sourceFile, null);
            assertEquals("copied.txt", copiedFile.getName());
            if (!isCreationTimeImmutable()) {
                assertEquals(sourceFile.getCreationTime(), copiedFile.getCreationTime());
            }
            assertEquals(sourceFile.getLastModifiedTime(), copiedFile.getLastModifiedTime());

            // Verify content
            try (InputStream is = copiedFile.getInputStream()) {
                byte[] readContent = is.readAllBytes();
                assertArrayEquals(content.getBytes(), readContent, "Copied content should match");
            }

            // Check progress listener + copying to an existing file
            AtomicLong progress = new AtomicLong();
            dest.copy("copied.txt", sourceFile, progress::set);
            assertEquals(sourceFile.getSize(), progress.get(), "Progress should match copied content size");

            // Check copying from a missing file
            File missingFile = createMockFile(content);
            Mockito.lenient().when(missingFile.getInputStream()).thenThrow(new IOException("Missing file"));
            assertThrows(IOException.class, () -> dest.copy("copied.txt", missingFile, null), "Should throw IOException when file does not exist");
            
            // Check illegal file names
            assertThrows(IllegalArgumentException.class, () -> dest.copy("", sourceFile, null), "Should throw for empty name");
            assertThrows(IllegalArgumentException.class, () -> dest.copy("name/with/slash", sourceFile, null), "Should throw for name with slash");
        } finally {
            dest.delete();
        }
    }
    
    @Test
    protected void testDeleteFile() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider is not writable");
        File entry = root.copy("test.txt", createMockFile("content"), null);
        try {
            assertTrue(entry.exists());

            // When
            entry.delete();

            // Then
            assertFalse(root.list().stream().anyMatch(e -> e.getName().equals("test.txt")), "File should be in root.list() after deletion");
            Entry afterDelete = provider.get("/test.txt");
            assertFalse(afterDelete.exists(), "File should not exist after deletion");
        } finally {
            entry.delete();
        }
    }

    @Test
    protected void testDeleteFolder() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider is not writable");
        // Given
        Folder parent = root.mkdir("parent");
        parent.copy("file1.txt", createMockFile("content1"), null);
        parent.mkdir("subfolder");

        assertTrue(parent.exists());

        // When
        parent.delete();

        // Then
        assertFalse(root.list().stream().anyMatch(e -> e.getName().equals("parent")),
                "Folder should be in root.list() after deletion");
        Entry afterDelete = provider.get("/parent");
        assertFalse(afterDelete.exists(), "Folder should not exist after deletion");

        Entry childAfterDelete = provider.get("/parent/file1.txt");
        assertFalse(childAfterDelete.exists(), "Child file should not exist after parent deletion");

        Entry subfolderAfterDelete = provider.get("/parent/subfolder");
        assertFalse(subfolderAfterDelete.exists(), "Subfolder should not exist after parent deletion");

        // Check that folder can be deleted twice
        assertDoesNotThrow(parent::delete);

        // That subfolder of a deleted folder can be deleted
        assertDoesNotThrow(subfolderAfterDelete::delete);

        // Check root folder can't be deleted
        assertThrows(IOException.class, () -> root.delete(), "Should throw IOException when root folder is deleted");
    }
    
    @Test
    protected void testPreload() throws IOException {
        Folder folder = getAFolder();
        if (provider.isFastListSupported()) {
            assertDoesNotThrow(folder::preload);
        } else {
            assertThrows(UnsupportedOperationException.class, folder::preload);
        }
    }

    /**
     * Tests that the file provider reflects the underlying file system.
     * <br>This test is skipped if the provider has no underlying file system.
     * <br>Please note that this test doesn't test that existing entries reflect the underlying file system changes, because this is not required by the sync4j API.
     * @throws IOException if an I/O error occurs
     */
    @Test
    protected void testReflectsUnderlyingFileSystem() throws IOException {
        assumeTrue(ufs != null, "Test skipped because provider has no underlying filesystem");

        // Test folder creation is reflected in entries returned by the provider
        ufs.createFolder("/new-folder");
        Folder folder = provider.get("/new-folder").asFolder();
        assertTrue(folder.exists());
        assertTrue(folder.list().isEmpty());
        assertTrue(provider.get(FileProvider.ROOT_PATH).asFolder().list().stream().map(Entry::getName).toList().contains("new-folder"));

        // Test file creation is reflected in entries returned by the provider
        ufs.createFile("/new-folder/file.txt");
        File file = provider.get("/new-folder/file.txt").asFile();
        assertTrue(file.exists());
        ufs.assertUnderlyingFileEquals("/new-folder/file.txt", file);
        assertEquals(List.of("file.txt"), provider.get("/new-folder").asFolder().list().stream().map(Entry::getName).toList());

        // Test non existing entry
        assertFalse(provider.get("/nonExisting").exists());

        // Test file deletion is reflected in entries returned by the provider
        ufs.delete("/new-folder/file.txt");
        assertFalse(provider.get("/new-folder/file.txt").exists());
        assertEquals(List.of(), provider.get("/new-folder").asFolder().list().stream().map(Entry::getName).toList());

        // Test folder deletion is reflected in entries returned by the provider
        ufs.delete("/new-folder");
        assertFalse(provider.get("/new-folder").exists());
        assertFalse(provider.get(FileProvider.ROOT_PATH).asFolder().list().stream().map(Entry::getName).toList().contains("new-folder"));
    }

    @Test
    void testChangesUnderlyingFileSystem() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider doesn't support write");
        assumeTrue(ufs != null, "Test skipped because provider has no underlying filesystem");


//fail("Not implemented");
        // TODO
    }
    
    @Test
    protected void testIsFileAndSimilar() throws IOException {
        File file = getAFile();
        assertTrue(file.exists());
        assertTrue(file.isFile());
        assertFalse(file.isFolder());
        assertSame(file, file.asFile());
        assertThrows(IllegalStateException.class, file::asFolder);

        Folder folder = getAFolder();
        assertTrue(folder.exists());
        assertTrue(folder.isFolder());
        assertFalse(folder.isFile());
        assertSame(folder, folder.asFolder());
        assertThrows(IllegalStateException.class, folder::asFile);

        Entry nonExisting = getMissingEntry("");
        assertFalse(nonExisting.exists());
        assertFalse(nonExisting.isFolder());
        assertFalse(nonExisting.isFile());
        assertThrows(IllegalStateException.class, nonExisting::asFolder);
        assertThrows(IllegalStateException.class, nonExisting::asFile);
    }

    @Test
    protected void testWriteSupported() {
        assertTrue(provider.isWriteSupported());
    }

    /**
     * Test the read-only mode.
     */
    @Test
    protected void testReadOnlyMode() throws IOException {
        assertFalse(provider.isReadOnly(), "Provider should not be read-only by default");
        int initialSize = root.list().size();

        // Create a new file
        File testFile = root.copy("test.txt", createMockFile("content"), null);

        // When read-only is set
        provider.setReadOnly(true);
        assertEquals(initialSize + 1, root.list().size());
        // But file can be read and directory listed
        try (InputStream is = testFile.getInputStream()) {
            byte[] readContent = is.readAllBytes();
            assertEquals("content", new String(readContent, StandardCharsets.UTF_8));
        }

        // All modifications should fail
        assertTrue(provider.isReadOnly(), "Provider should be read-only");
        // All modifications should fail
        assertThrows(IOException.class, () -> root.mkdir("subfolder"));
        File mockedFile = createMockFile("content");
        assertThrows(IOException.class, () -> root.copy("copy.txt", mockedFile, null));
        assertThrows(IOException.class, testFile::delete);

        // When read-only is unset, all modifications should work
        provider.setReadOnly(false);
        assertFalse(provider.isReadOnly(), "Provider should not be read-only after unsetting");
        assertDoesNotThrow(() -> root.mkdir("subfolder"));
        assertDoesNotThrow(() -> root.copy("copy.txt", testFile, null));
        assertDoesNotThrow(testFile::delete);
    }
}
