package com.fathzer.sync4j.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.Folder;

/** A try to create a common test for non-writable file providers.
 * @see AbstractNonWritableFileProviderTest
*/
public abstract class AbstractNonWritableFileProviderTest extends AbstractFileProviderTest {
    @Override
    protected Folder getAFolder() throws IOException {
        return searchFor(root, Entry::isFolder, "folder", "Unable to find a folder to perform tests").asFolder();
    }

    @Override
    protected File getAFile() throws IOException {
        return searchFor(root, Entry::isFile, "file", "Unable to find a file to perform tests").asFile();
    }

    @Override
    @Test
    protected void testWriteSupported() {
        assertFalse(provider.isWriteSupported());
    }

    @Override
    @Test
    protected void testFolderList() throws IOException {
        Folder folder = getAFolder();
        assertTrue(parentListContains(folder, folder.getName()));
        File file = getAFile();
        assertTrue(parentListContains(file, file.getName()));
        Entry missing = getMissingEntry(folder.getPath());
        assertFalse(parentListContains(missing, missing.getName()));
    }
    
    private boolean parentListContains(Entry entry, String name) throws IOException {
        return ((Folder)entry.getParent()).list().stream().map(Entry::getName).anyMatch(n->n.equals(name));
    }

    @Override
    @Test
    protected void testReadOnlyMode() throws IOException {
        assertTrue(provider.isReadOnly(), "Provider should be read-only");
        // All modifications should fail
        assertThrows(IOException.class, () -> root.mkdir("subfolder"));
        File mockedFile = createMockFile("content");
        assertThrows(IOException.class, () -> root.copy("copy.txt", mockedFile, null));
        File availableFile = searchFor(root, Entry::isFile, "file", "delete is throwing exception").asFile();
        assertThrows(IOException.class, availableFile::delete);
    }
    
    private Entry searchFor(Folder folder, Predicate<Entry> filter, String what, String why) throws IOException {
        Entry entry = searchFor(folder, filter);
        if (entry == null) {
            fail("No " + what + " found in the provider - No way to test " + why);
        }
        return entry;
    }

    private Entry searchFor(Folder folder, Predicate<Entry> filter) throws IOException {
        for (Entry entry : folder.list()) {
            if (filter.test(entry)) {
                return entry;
            } else {
                Entry fileInSubfolder = searchFor(entry.asFolder(), filter);
                if (fileInSubfolder != null) {
                    return fileInSubfolder;
                }
            }
        }
        return null;
    }
}
