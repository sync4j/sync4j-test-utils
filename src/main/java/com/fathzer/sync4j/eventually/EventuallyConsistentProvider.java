package com.fathzer.sync4j.eventually;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import com.fathzer.sync4j.Entry;
import com.fathzer.sync4j.File;
import com.fathzer.sync4j.Folder;
import com.fathzer.sync4j.HashAlgorithm;
import com.fathzer.sync4j.util.IOLambda.IORunnable;
import com.fathzer.sync4j.FileProvider;

import jakarta.annotation.Nonnull;

/**
 * A FileProvider that simulates an eventually consistent file system.
 * <p>
 * This FileProvider is based on an underlying FileProvider. When a file or folder is created, its creation is not immediately reflected in its behavior
 * or, for example, in the parent's list method and some entry's operations will throw FileNotFoundException for a configurable number of milliseconds before
 * the system becomes consistent.
 * </p>
 * <p>
 * This provider wraps all entries returned by the underlying FileProvider to intercept operations and simulate the eventual consistency behavior.
 * </p>
 * <p>
 * It is thread safe and could be used to test algorithms, based on file providers, in the context of an eventually consistent file system (like a cloud storage).
 * </p>
 * <p>
 * <b>Warning:</b> This provider is should never be used in production. It has a memory leaks (the creationTimes map is not automatically cleaned up when an entry become consistent) and is not optimized for performance.
 * </p>
 */
public class EventuallyConsistentProvider implements FileProvider {
    private static final Timer TIMER = new Timer("EventuallyConsistentProvider", true);
    
    private final long consistencyDelayMs;
    private final FileProvider provider;
    final Map<String, Long> creationTimes = new ConcurrentHashMap<>();
    
    /**
     * Creates a new eventually consistent file provider with the specified delay.
     * @param provider the underlying file provider
     * @param consistencyDelayMs the delay in milliseconds before a newly created entry becomes visible
     */
    public EventuallyConsistentProvider(FileProvider provider, long consistencyDelayMs) {
        this.provider = provider;
        this.consistencyDelayMs = consistencyDelayMs;
    }
    
    @Override
    @Nonnull
    public Entry get(@Nonnull String path) throws IOException {
        Entry entry = provider.get(path);
        return wrapEntry(entry);
    }
    
    @Override
    public void setReadOnly(boolean readOnly) {
        provider.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isReadOnly() {
        return provider.isReadOnly();
    }

    private void checkReadOnly() throws IOException {
        if (provider.isReadOnly()) {
            throw new IOException("Cannot modify entry in read-only provider");
        }
    }

    @Override
    public void close() {
        provider.close();
    }

    @Override
    public List<HashAlgorithm> getSupportedHash() {
        return provider.getSupportedHash();
    }

    @Override
    public long getLastModifiedTimePrecision() {
        return provider.getLastModifiedTimePrecision();
    }

    @Override
    public long getCreationTimePrecision() {
        return provider.getCreationTimePrecision();
    }
    
    /**
     * Wraps an entry to add eventual consistency behavior.
     * 
     * @param entry the entry to wrap
     * @return the wrapped entry
     */
    private Entry wrapEntry(Entry entry) throws IOException {
        if (entry.isFile()) {
            return new EventuallyConsistentFile((File) entry);
        } else if (entry.isFolder()) {
            return new EventuallyConsistentFolder((Folder) entry);
        } else {
            return new MissingEntry(entry);
        }
    }
    
    /**
     * Checks if an entry is still in the inconsistent state.
     * 
     * @param path the path of the entry
     * @return true if the entry should still appear as non-existent
     */
    private boolean isInconsistent(String path) {
        Long creationTime = creationTimes.get(path);
        if (creationTime == null) {
            return false;
        }
        
        long elapsed = System.currentTimeMillis() - creationTime;
        if (elapsed >= consistencyDelayMs) {
            // Consistency achieved, remove from tracking
            creationTimes.remove(path);
            return false;
        }
        
        return true;
    }
    
    private class MissingEntry implements Entry {
        private final Entry delegate;
        
        MissingEntry(Entry delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public boolean isFile() {
            return false;
        }
        
        @Override
        public boolean isFolder() {
            return false;
        }
        
        @Override
        public String getPath() throws IOException {
            return delegate.getPath();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Entry getParent() throws IOException {
            return delegate.getParent();
        }

        @Override
        public void delete() throws IOException {
            checkReadOnly();
            TIMER.schedule(toTask(delegate::delete), consistencyDelayMs);
        }

        @Override
        public FileProvider getFileProvider() {
            return EventuallyConsistentProvider.this;
        }
    }

    private TimerTask toTask(IORunnable runnable) {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    /**
     * Wrapper for File that simulates eventual consistency.
     */
    private class EventuallyConsistentFile implements File {
        private final File delegate;
        private final String path;
        
        EventuallyConsistentFile(File delegate) throws IOException {
            this.delegate = delegate;
            this.path = delegate.getPath();
        }
        
        @Override
        public boolean isFile() {
            return !isInconsistent(path) && delegate.isFile();
        }
        
        @Override
        public boolean isFolder() {
            return false;
        }
        
        @Override
        public boolean exists() {
            return !isInconsistent(path) && delegate.exists();
        }
        
        @Override
        @Nonnull
        public String getName() {
            return delegate.getName();
        }
        
        @Override
        public Entry getParent() throws IOException {
            Entry parent = delegate.getParent();
            if (parent == null) {
                return null;
            }
            return wrapEntry(parent);
        }
        
        @Override
        public void delete() throws IOException {
            checkReadOnly();
            TIMER.schedule(toTask(delegate::delete), consistencyDelayMs);
        }
        
        @Override
        @Nonnull
        public FileProvider getFileProvider() {
            return EventuallyConsistentProvider.this;
        }
        
        @Override
        public long getSize() throws IOException {
            checkConsistency();
            return delegate.getSize();
        }
        
        @Override
        public long getCreationTime() throws IOException {
            checkConsistency();
            return delegate.getCreationTime();
        }
        
        @Override
        public long getLastModifiedTime() throws IOException {
            checkConsistency();
            return delegate.getLastModifiedTime();
        }
        
        @Override
        @Nonnull
        public String getHash(@Nonnull HashAlgorithm hashAlgorithm) throws IOException {
            checkConsistency();
            return delegate.getHash(hashAlgorithm);
        }
        
        @Override
        @Nonnull
        public InputStream getInputStream() throws IOException {
            checkConsistency();
            return delegate.getInputStream();
        }

        private void checkConsistency() throws FileNotFoundException {
            if (isInconsistent(path)) {
                throw new FileNotFoundException("File does not exist: " + path);
            }
        }
    }
    
    /**
     * Wrapper for Folder that simulates eventual consistency.
     */
    private class EventuallyConsistentFolder implements Folder {
        private final Folder delegate;
        private final String path;
        
        EventuallyConsistentFolder(Folder delegate) throws IOException {
            this.delegate = delegate;
            this.path = delegate.getPath();
        }
        
        @Override
        public boolean isFile() {
            return false;
        }
        
        @Override
        public boolean isFolder() {
            return !isInconsistent(path) && delegate.isFolder();
        }
        
        @Override
        public boolean exists() {
            return !isInconsistent(path) && delegate.exists();
        }
        
        @Override
        @Nonnull
        public String getName() {
            return delegate.getName();
        }
        
        @Override
        public Entry getParent() throws IOException {
            Entry parent = delegate.getParent();
            if (parent == null) {
                return null;
            }
            return wrapEntry(parent);
        }

        private void checkConsistency() throws IOException {
            if (isInconsistent(path)) {
                throw new IOException("Folder does not exist: " + path);
            }
        }
        
        @Override
        public void delete() throws IOException {
            checkReadOnly();
            if (delegate.getPath().equals(FileProvider.ROOT_PATH)) {
                throw new IOException("Cannot delete root folder");
            }
            TIMER.schedule(toTask(delegate::delete), consistencyDelayMs);
        }
        
        @Override
        @Nonnull
        public FileProvider getFileProvider() {
            return EventuallyConsistentProvider.this;
        }
        
        @Override
        @Nonnull
        public List<Entry> list() throws IOException {
            checkConsistency();
            
            List<Entry> entries = delegate.list();
            // Filter out entries that are still inconsistent and wrap the visible ones
            return entries.stream()
                    .filter(entry -> {
                        try {
                            String entryPath = entry.getPath();
                            return !isInconsistent(entryPath);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(entry -> {
                        try {
                            return wrapEntry(entry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
        
        @Override
        @Nonnull
        public File copy(@Nonnull String fileName, @Nonnull File content, LongConsumer progressListener) throws IOException {
            checkConsistency();
            File file = delegate.copy(fileName, content, progressListener);
            recordCreation(file.getPath());
            return new EventuallyConsistentFile(file);
        }
        
        @Override
        @Nonnull
        public Folder mkdir(@Nonnull String folderName) throws IOException {
            checkConsistency();
            Folder folder = delegate.mkdir(folderName);
            recordCreation(folder.getPath());
            return new EventuallyConsistentFolder(folder);
        }
            
        /**
         * Records the creation of an entry.
         * 
         * @param path the path of the created entry
         */
        private void recordCreation(String path) {
            creationTimes.put(path, System.currentTimeMillis());
        }
    }
}
