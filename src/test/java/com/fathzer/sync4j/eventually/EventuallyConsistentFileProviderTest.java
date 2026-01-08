package com.fathzer.sync4j.eventually;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.Folder;
import com.fathzer.sync4j.HashAlgorithm;
import com.fathzer.sync4j.memory.MemoryFileProvider;
import com.fathzer.sync4j.test.AbstractFileProviderTest;

/**
 * Test class to demonstrate the EventuallyConsistentMemoryFileProvider behavior.
 */
class EventuallyConsistentFileProviderTest {
    private static final int CONSISTENCY_DELAY_MS = 150;

    private EventuallyConsistentProvider provider;

    @BeforeEach
    void setUp() {
        // Reset any static state if needed
        provider = new EventuallyConsistentProvider(new MemoryFileProvider(), CONSISTENCY_DELAY_MS);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private void waitForConsistency() throws InterruptedException {
        Thread.sleep(CONSISTENCY_DELAY_MS + 50);
    }

    @Test
    void testFolderCreationEventualConsistency() throws IOException, InterruptedException {
        // Get root folder
        Folder root = provider.get(MemoryFileProvider.ROOT_PATH).asFolder();
        
        // Create a folder
        Folder folder = root.mkdir("testFolder");
        
        // Immediately after creation, the folder should not be visible
        assertFalse(folder.exists(), "Folder should not exist immediately after creation");
        assertFalse(folder.isFolder(), "Folder should not be a folder immediately after creation");
        
        // The folder should not appear in parent's list
        assertEquals(0, root.list().size(), "Parent should not list the newly created folder");
        
        // Trying to use the folder should throw IOException
        assertThrows(IOException.class, folder::list, "Should throw IOException when listing non-visible folder");
        assertThrows(IOException.class, folder::delete, "Should throw IOException when deleting non-visible folder");
        
        // Wait for consistency delay to pass
        waitForConsistency();
        
        // After the delay, the folder should be visible
        assertTrue(folder.exists(), "Folder should exist after consistency delay");
        assertTrue(folder.isFolder(), "Folder should be a folder after consistency delay");
        
        // The folder should now appear in parent's list
        assertEquals(1, root.list().size(), "Parent should list the folder after consistency delay");
        
        // Operations should now work
        assertDoesNotThrow(() -> folder.list(), "Should not throw when listing visible folder");
    }
    
    @Test
    void testFileCreationEventualConsistency() throws IOException, InterruptedException {
        // Get root folder
        Folder root = provider.get(MemoryFileProvider.ROOT_PATH).asFolder();
        
        // Create a temporary file to copy
        File sourceFile = AbstractFileProviderTest.createMockFile("Hello");
        
        // Copy the file to the eventually consistent provider
        File file = root.copy("test.txt", sourceFile, null);
        
        // Immediately after creation, the file should not be visible
        assertFalse(file.exists(), "File should not exist immediately after creation");
        assertFalse(file.isFile(), "File should not be a file immediately after creation");
        
        // The file should not appear in parent's list
        assertEquals(0, root.list().size(), "Parent should not list the newly created file");
        
        // Trying to use the file should throw IOException
        assertThrows(IOException.class, file::getSize, "Should throw IOException when getting size of non-visible file");
        assertThrows(IOException.class, file::getInputStream, "Should throw IOException when getting input stream of non-visible file");
        assertThrows(IOException.class, file::delete, "Should throw IOException when deleting non-visible file");
        
        // Wait for consistency delay to pass
        waitForConsistency();
        
        // After the delay, the file should be visible
        Entry fileEntryAfter = provider.get("/test.txt");
        assertTrue(fileEntryAfter.exists(), "File should exist after consistency delay");
        assertTrue(fileEntryAfter.isFile(), "File should be a file after consistency delay");
        
        // The file should now appear in parent's list
        assertEquals(1, root.list().size(), "Parent should list the file after consistency delay");
        
        // Operations should now work
        File visibleFile = fileEntryAfter.asFile();
        assertDoesNotThrow(visibleFile::getSize, "Should not throw when getting size of visible file");
        assertEquals(5, visibleFile.getSize(), "File size should be correct");
    }
    
    @Test
    void testNestedFolderCreation() throws IOException, InterruptedException {
        // Get root folder
        Folder root = provider.get(MemoryFileProvider.ROOT_PATH).asFolder();
        
        // Create a folder
        Folder folder1 = root.mkdir("folder1");
        
        // Wait for it to become visible
        waitForConsistency();
        
        // Now create a subfolder
        Folder folder2 = folder1.mkdir("folder2");
        
        // Immediately after creation, folder2 should not be visible
        assertFalse(folder2.exists(), "Subfolder should not exist immediately after creation");
        
        // folder1's list should not include folder2
        assertEquals(0, folder1.list().size(), "Parent folder should not list the newly created subfolder");
        
        // Wait for consistency
        waitForConsistency();
        
        // Now folder2 should be visible
        assertTrue(folder2.exists(), "Subfolder should exist after consistency delay");
        assertEquals(1, folder1.list().size(), "Parent folder should list the subfolder after consistency delay");
    }

    @Test
    void testDelegateMethods() {
        final AtomicBoolean closed = new AtomicBoolean(false);
        FileProvider underlying = new MemoryFileProvider() {
            @Override
            public List<HashAlgorithm> getSupportedHash() {
                return List.of(HashAlgorithm.SHA1);
            }
            @Override
            public long getLastModifiedTimePrecision() {
                return 10;
            }
            @Override
            public long getCreationTimePrecision() {
                return 20;
            }
            @Override
            public void close() {
                closed.set(true);
            }
        };
        this.provider = new EventuallyConsistentProvider(underlying, CONSISTENCY_DELAY_MS);
        assertEquals(underlying.getSupportedHash(), provider.getSupportedHash());
        assertEquals(underlying.getLastModifiedTimePrecision(), provider.getLastModifiedTimePrecision());
        assertEquals(underlying.getCreationTimePrecision(), provider.getCreationTimePrecision());
        provider.setReadOnly(true);
        assertTrue(provider.isReadOnly());
        assertTrue(underlying.isReadOnly());
        provider.close();
        assertTrue(closed.get());
    }
}
