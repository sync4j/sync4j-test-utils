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
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.Folder;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/** A try to create a common test for file providers (except for non-writable providers).
 * @see AbstractNonWritableFileProviderTest
*/
public abstract class AbstractFileProviderTest {

    /** Constructor */
    protected AbstractFileProviderTest() {
        // Do nothing
    }

    /** An annotation to declare there's no write support expected. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NoWriteSupport {
    }

    /** The root folder.
     * <br>This folder is created in the setup method.
     */
    @Nullable
    protected Folder root;

    /** The file provider.
     * <br>This provider is created in the setup method.
     */
    @Nullable
    protected FileProvider provider;

    /** The underlying file system.
     * <br>This file system is created in the setup method by calling {@link #getUnderlyingFileSystem()}. 
     */
    @Nullable
    protected UnderlyingFileSystem ufs;

    /**
     * Creates the file provider.
     * <br>This method is called in the setup method, during the @BeforeEach phase of the JUnit test.
     * @param testInfo the test info
     * @return the file provider, or null if the test should be skipped
     * @throws IOException if an I/O error occurs
     */
    @Nullable
    protected abstract FileProvider createFileProvider(@Nonnull TestInfo testInfo) throws IOException;

    /**
     * Cleans up the file provider created in {@link #createFileProvider(TestInfo)}. 
     * <br>This method is called during the @AfterEach phase of the JUnit test.
     * <br>By default, this method closes the file provider returned by {@link #createFileProvider(TestInfo)}.
     * <br>Subclasses can override this method to perform additional cleanup (for instance to clean up the underlying file system).
     * @throws IOException if an I/O error occurs
     */
    protected void cleanUpProvider() throws IOException {
        if (provider != null) {
            provider.close();
        }
    }

    /**
     * Returns the underlying file system on which the file provider is based.
     * @return the underlying file system, or null if the provider doesn't reflect an underlying file system (for instance for in-memory providers)
     */
    protected abstract UnderlyingFileSystem getUnderlyingFileSystem();

    /**
     * Creates the file provider, get its root folder and underlying file system.
     * <br>This method is called during the @BeforeEach phase of the JUnit test and calls {@link #createFileProvider(TestInfo)} and {@link #getUnderlyingFileSystem()}. 
     * @param testInfo the test info
     * @throws IOException if an I/O error occurs
     */
    @BeforeEach
    void setup(TestInfo testInfo) throws IOException {
        provider = createFileProvider(testInfo);
        assumeTrue(provider != null, "Provider not available");
        root = provider.get(FileProvider.ROOT_PATH).asFolder();
        ufs = getUnderlyingFileSystem();
    }

    @AfterEach
    /**
     * Cleans up the file provider created in {@link #createFileProvider(TestInfo)}.
     * <br>This method is called during the @AfterEach phase of the JUnit test.
     * <br>By default, this method calls {@link #cleanUpProvider()}.
     * @throws IOException if an I/O error occurs
     */
    void teardown() throws IOException {
        cleanUpProvider();
    }

    /**
     * Creates a mock file.
     * <br>By default, this method creates a mock file with the given content, {@link File#getSize() size} set to the length of the content, {@link File#getLastModifiedTime() last modified time} set to the current time, and {@link File#getCreationTime() creation time} set to the current time minus 1000.
     * <br>All stubs are lenient.
     * @param content the content of the file
     * @return the mock file
     * @throws IOException if an I/O error occurs
     */
    protected static File createMockFile(String content) throws IOException {
        File result = Mockito.mock(File.class);
        byte[] bytes = content.getBytes();
        Mockito.lenient().when(result.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        Mockito.lenient().when(result.getSize()).thenReturn((long) bytes.length);
        long now = System.currentTimeMillis();
        Mockito.lenient().when(result.getLastModifiedTime()).thenReturn(now);
        Mockito.lenient().when(result.getCreationTime()).thenReturn(now-1000);
        return result;
    }

    /**
     * Returns an existing folder.
     * <br>By default, this method returns the folder "/folder" using {@link #provider}'s methods. It creates it if it doesn't exist.
     * @return the folder
     * @throws IOException if an I/O error occurs
     */
    protected Folder getAFolder() throws IOException {
        Entry entry = provider.get("/folder");
        return entry.isFolder() ? entry.asFolder() : root.mkdir("folder");
    }

    /**
     * Returns an existing file.
     * <br>By default, this method returns the file "/folder/file.txt" using {@link #provider}'s methods. It creates it if it doesn't exist (including the folder).
     * @return the file
     * @throws IOException if an I/O error occurs
     */
    protected File getAFile() throws IOException {
        Entry entry = provider.get("/folder/file.txt");
        return entry.isFile() ? entry.asFile() : getAFolder().copy("file.txt", createMockFile("content"), null);
    }

    /**
     * Returns a missing entry.
     * <br>By default, this method returns a missing entry using {@link #provider}'s methods to find a missing under the root and named "<i>prefix</i>/missingX" where X is a number &lt; 100.
     * @param prefix the prefix of the entry (a folder path). Empty for root folder. The folder can exist or not and will not be created.
     * @return the entry
     * @throws IOException if an I/O error occurs (for instance if we can't find a missing entry)
     */
    protected Entry getMissingEntry(String prefix) throws IOException {
        for (int i = 0; i < 100; i++) {
            Entry entry = provider.get(prefix + "/missing" + i);
            if (!entry.exists()) {
                return entry;
            }
        }
        throw new IOException("Not able to find a missing entry named /" + prefix + "/missingX where X is a < 100");
    }

    /**
     * Tests the root folder.
     * @throws IOException if an I/O error occurs
     */
    @Test
    protected void testRoot() throws IOException {
        assertTrue(root.exists(), "Root should exist");
        assertTrue(root.isFolder(), "Root should be a folder");
        assertFalse(root.isFile(), "Root should not be a file");
        assertEquals("", root.getName(), "Root should have no name");
        assertNull(root.getParent(), "Root should have no parent");
        assertThrows(IOException.class, () -> root.delete(), "Root should not be deleted");
    }

    /**
     * Tests the {@link #provider}'s get method.
     * @throws IOException if an I/O error occurs
     */
    @Test
    protected void testGet() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> provider.get("/folder//file.txt"), "Invalid path (double slash) should not be retrieved");
        assertThrows(IllegalArgumentException.class, () -> provider.get("folder/file.txt"), "Invalid path (no leading slash) should not be retrieved");

        // Check inconsistent path does not throw any exception and returns a non existing entry
        Entry file = getAFile();
        Entry inconsistentPathFile = provider.get(file.getPath() + "/toto.txt");
        assertFalse(inconsistentPathFile.exists());

        // Check non existing parent is really not existing
        Entry nonExistingParentFile = getMissingEntry("/folder");
        String path = nonExistingParentFile.getPath()+"/file.txt";
        file = provider.get(path);
        assertFalse(file.exists());
        Entry parent = file.getParent();
        assertFalse(parent.exists());
        assertFalse(parent.isFolder());
        assertFalse(parent.isFile());
    }

    /**
     * Tests the {@link #provider}'s getParent method.
     * @throws IOException if an I/O error occurs
     */
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

    /**
     * Tests the {@link #provider}'s getFileProvider method.
     * @throws IOException if an I/O error occurs
     */
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

    /**
     * Tests folder listing ({@link Folder#list()}).
     * @throws IOException if an I/O error occurs
     */
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

    /**
     * Tests folder creation ({@link Folder#mkdir(String)}).
     * @throws IOException if an I/O error occurs
     */
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
    
    /**
     * Tests file copy ({@link Folder#copy(String, File, ProgressListener)}).
     * @throws IOException if an I/O error occurs
     */
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
            assertTrue(Math.abs(sourceFile.getCreationTime() - copiedFile.getCreationTime()) <= provider.getCreationTimePrecision(), "Creation time should match but found " + sourceFile.getCreationTime() + " for src and " + copiedFile.getCreationTime() + " for dest with a precision of " + provider.getCreationTimePrecision());
            assertTrue(Math.abs(sourceFile.getLastModifiedTime() - copiedFile.getLastModifiedTime()) <= provider.getLastModifiedTimePrecision(), "Last modified time should match but found " + sourceFile.getLastModifiedTime() + " for src and " + copiedFile.getLastModifiedTime() + " for dest with a precision of " + provider.getLastModifiedTimePrecision());

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
    
    /**
     * Tests file deletion.
     * @throws IOException if an I/O error occurs
     */
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
            assertDoesNotThrow(afterDelete::delete);
        } finally {
            // Check that folder can be deleted twice
            entry.delete();
        }
    }

    /**
     * Tests folder deletion.
     * @throws IOException if an I/O error occurs
     */
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
    
    /**
     * Tests that {@link Folder#preload()} exception throwing is consistent with {@link FileProvider#isFastListSupported()}
     * @throws IOException if an I/O error occurs
     */
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
        ufs.deleteFile("/new-folder/file.txt");
        assertFalse(provider.get("/new-folder/file.txt").exists());
        assertEquals(List.of(), provider.get("/new-folder").asFolder().list().stream().map(Entry::getName).toList());

        // Test folder deletion is reflected in entries returned by the provider
        ufs.deleteFolder("/new-folder");
        assertFalse(provider.get("/new-folder").exists());
        assertFalse(provider.get(FileProvider.ROOT_PATH).asFolder().list().stream().map(Entry::getName).toList().contains("new-folder"));
    }

    /**
     * Tests that the provider can change the underlying file system.
     * <br>This test is skipped if the provider doesn't support write or has no underlying file system.
     * @throws IOException if an I/O error occurs
     * @see #getUnderlyingFileSystem()
     */
    @Test
    protected void testChangesUnderlyingFileSystem() throws IOException {
        assumeTrue(provider.isWriteSupported(), "Test skipped because provider doesn't support write");
        assumeTrue(ufs != null, "Test skipped because provider has no underlying filesystem");

        File mockFile = createMockFile("content");
        Folder folder = root.mkdir("folder");
        assertTrue(ufs.underlyingFolderExists("/folder"));

        folder.copy("file.txt", mockFile, null);
        ufs.assertUnderlyingFileEquals("/folder/file.txt", mockFile);
        
        folder.delete();
        assertFalse(ufs.underlyingFolderExists("/folder"));
    }
    
    /**
     * Tests isFile, isFolder, exists, asFile, asFolder.
     * @throws IOException if an I/O error occurs
     */
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
    
    /**
     * Test that the provider supports write (if not {@link AbstractNonWritableFileProviderTest} should be used instead of this class).
     */
    @Test
    protected void testWriteSupported() {
        assertTrue(provider.isWriteSupported());
    }

    /**
     * Test the read-only mode.
     * @throws IOException if an I/O error occurs
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
