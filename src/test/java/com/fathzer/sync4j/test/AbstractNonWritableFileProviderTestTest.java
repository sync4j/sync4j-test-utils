package com.fathzer.sync4j.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.TestInfo;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.Folder;
import com.fathzer.sync4j.memory.MemoryFileProvider;

/** A test class just to check that AbstractNonWritableFileProviderTest is correct */
class AbstractNonWritableFileProviderTestTest extends AbstractNonWritableFileProviderTest {
    private static final ReadOnlyProvider PROVIDER;
    private static final UnderlyingFileSystem UFS = new MyUnderlyingFileSystem();

    private static class MyUnderlyingFileSystem implements UnderlyingFileSystem {
        @Override
        public void createFile(String path) throws IOException {
            Entry entry = PROVIDER.get(path);
            if (!entry.isFile()) {
                PROVIDER.forceReadOnly(false);
                try {
                    Folder folder = mkdirs(entry.getParent().getPath());
                    folder.copy(entry.getName(), createMockFile("content"), null);
                } finally {
                    PROVIDER.forceReadOnly(true);
                }
            }
        }

        @Override
        public void deleteFile(String path) throws IOException {
        	delete(path);
        }

        @Override
        public void deleteFolder(String path) throws IOException {
        	delete(path);
        }
        
        private void delete(String path) throws IOException {
            Entry entry = PROVIDER.get(path);
            if (!entry.exists()) {
                throw new IOException(path + " does not exist");
            }
            PROVIDER.forceReadOnly(false);
            try {
                entry.delete();
            } finally {
                PROVIDER.forceReadOnly(true);
            }
        }

        @Override
        public void createFolder(String path) throws IOException {
            Entry entry = PROVIDER.get(path);
            if (!entry.isFolder()) {
                PROVIDER.forceReadOnly(false);
                try {
                    mkdirs(path);
                } finally {
                    PROVIDER.forceReadOnly(true);
                }
            }
        }

        private Folder mkdirs(String path) throws IOException {
            Entry entry = PROVIDER.get(path);
            if (entry.isFolder()) {
                return entry.asFolder();
            } else if (entry.isFile()) {
                throw new IOException(path + " already exists and is a file");
            } else {
                String[] segments = path.substring(1).split("/");
                Folder folder = PROVIDER.get(FileProvider.ROOT_PATH).asFolder();
                for (String segment : segments) {
                    folder = folder.mkdir(segment);
                }
                return folder;
            }
        }

        @Override
        public void assertUnderlyingFileEquals(String path, File file) throws IOException {
            Entry entry = PROVIDER.get(path);
            if (entry.isFile()) {
                try (InputStream is = entry.asFile().getInputStream(); InputStream fis = file.getInputStream()) {
                    assertArrayEquals(is.readAllBytes(), fis.readAllBytes());
                }
            } else {
                throw new IOException(path + " is not a file");
            }
        }

        @Override
        public boolean underlyingFolderExists(String path) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected UnderlyingFileSystem getUnderlyingFileSystem() {
        return UFS;
    }

    static {
        try {
            PROVIDER = new ReadOnlyProvider();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class ReadOnlyProvider extends MemoryFileProvider {
        private boolean forcedReadOnly;
        private ReadOnlyProvider() throws IOException {
            super();
            Folder folder = this.get(ROOT_PATH).asFolder().mkdir("folder");
            folder.copy("file.txt", createMockFile("content"), null);
            setReadOnly(true);
            forcedReadOnly = true;
        }

        @Override
        public boolean isWriteSupported() {
            return false;
        }

        @Override
        public boolean isReadOnly() {
            return forcedReadOnly;
        }

        private void forceReadOnly(boolean readOnly) {
            this.forcedReadOnly = readOnly;
        }
    }

    @Override
    @SuppressWarnings("java:S2924")
    protected FileProvider createFileProvider(TestInfo testInfo) {
        return PROVIDER;
    }
}
