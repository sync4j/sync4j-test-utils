package com.fathzer.sync4j.eventually;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import org.junit.jupiter.api.TestInfo;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.helper.PathUtils;
import com.fathzer.sync4j.memory.MemoryFileProvider;
import com.fathzer.sync4j.test.AbstractFileProviderTest;
import com.fathzer.sync4j.test.UnderlyingFileSystem;

class AbstractECFileProviderTestTest extends AbstractFileProviderTest {
    private MemoryFileProvider underlyingProvider = new MemoryFileProvider();

    @Override
    protected FileProvider createFileProvider(TestInfo testInfo) throws IOException {
        return new EventuallyConsistentProvider(underlyingProvider, 150);
    }

    @Override
    protected Duration getConsistencyTimeout() {
        return Duration.ofMillis(500);
    }

    @Override
    protected UnderlyingFileSystem getUnderlyingFileSystem() {
        return new MyUnderlyingFileSystem();
    }

    private class MyUnderlyingFileSystem implements UnderlyingFileSystem {

        @Override
        public void createFolder(String path) throws IOException {
            Entry entry = underlyingProvider.get(path);
            if (entry.exists()) {
                throw new IOException("Folder already exists: " + path);
            }
            entry.getParent().asFolder().mkdir(PathUtils.getName(path));
        }

        @Override
        public void createFile(String path) throws IOException {
            String parentPath = PathUtils.getParent(path);
            if (parentPath==null) {
                parentPath = FileProvider.ROOT_PATH;
            }
            Entry parent = underlyingProvider.get(parentPath);
            if (!parent.isFolder()) {
                throw new IOException("Parent folder does not exist: " + parentPath);
            }
            parent.asFolder().copy(PathUtils.getName(path), createMockFile("underlying content"), null);
        }

        @Override
        public void deleteFile(String path) throws IOException {
            underlyingProvider.get(path).delete();
        }

        @Override
        public void deleteFolder(String path) throws IOException {
            deleteFile(path);
        }

        @Override
        public void assertUnderlyingFileEquals(String path, File file) throws IOException {
            try (InputStream pIn = underlyingProvider.get(path).asFile().getInputStream(); InputStream fIn = file.getInputStream()) {
                assertArrayEquals(fIn.readAllBytes(), pIn.readAllBytes());
            }
        }

        @Override
        public boolean underlyingFolderExists(String path) throws IOException {
            return underlyingProvider.get(path).isFolder();
        }
    }       
}
