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
 * By default (strict mode), a folder that is not yet visible can't be used at all, even through the handle returned by {@link Folder#mkdir(String)}.
 * This models a distributed backend where a following request may be served by a node that does not know the new folder yet.
 * <br>In session consistent mode (see {@link #EventuallyConsistentProvider(FileProvider, long, boolean)}), the handle returned by
 * {@link Folder#mkdir(String)} accepts {@link Folder#mkdir(String)} and {@link Folder#copy(String, File, LongConsumer)} immediately
 * (the creator can write in what it created). Reads ({@code list}, {@code exists}, ...) remain eventually consistent in both modes.
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
    private final boolean sessionConsistent;
    private final FileProvider provider;
    final Map<String, Long> creationTimes = new ConcurrentHashMap<>();
    
    /**
     * Creates a new eventually consistent file provider with the specified delay.
     * @param provider the underlying file provider
     * @param consistencyDelayMs the delay in milliseconds before a newly created entry becomes visible
     */
    public EventuallyConsistentProvider(FileProvider provider, long consistencyDelayMs) {
        this(provider, consistencyDelayMs, false);
    }

    /**
     * Creates a new eventually consistent file provider with the specified delay.
     * @param provider the underlying file provider
     * @param consistencyDelayMs the delay in milliseconds before a newly created entry becomes visible
     * @param sessionConsistent true to allow writes (mkdir, copy) in a folder through the handle returned by mkdir, even if it is not yet visible.
     * <br>If false (the default), any operation on a not yet visible folder fails.
     */
    public EventuallyConsistentProvider(FileProvider provider, long consistencyDelayMs, boolean sessionConsistent) {
        this.provider = provider;
        this.consistencyDelayMs = consistencyDelayMs;
        this.sessionConsistent = sessionConsistent;
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
            return new EventuallyConsistentFolder((Folder) entry, false);
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

    private abstract class LateConsistentEntry<T extends Entry> {
        private final T delegate;
        
        LateConsistentEntry(T delegate) {
            this.delegate = delegate;
        }

        public T getDelegate() {
            return delegate;
        }

        public String getName() {
            return delegate.getName();
        }

        public String getPath() throws IOException {
            return getDelegate().getPath();
        }

        public Entry getParent() throws IOException {
            Entry parent = getDelegate().getParent();
            if (parent == null) {
                return null;
            }
            return wrapEntry(parent);
        }
        
        @Nonnull
        public FileProvider getFileProvider() {
            return EventuallyConsistentProvider.this;
        }
    }
    
    private class MissingEntry extends LateConsistentEntry<Entry> implements Entry {
        MissingEntry(Entry delegate) {
            super(delegate);
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
        public void delete() throws IOException {
            checkReadOnly();
            TIMER.schedule(toTask(getDelegate()::delete), consistencyDelayMs);
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
    private class EventuallyConsistentFile extends LateConsistentEntry<File> implements File {
        private final String path;
        
        EventuallyConsistentFile(File delegate) throws IOException {
            super(delegate);
            this.path = delegate.getPath();
        }
        
        @Override
        public boolean isFile() {
            return !isInconsistent(path) && getDelegate().isFile();
        }
        
        @Override
        public boolean isFolder() {
            return false;
        }
        
        @Override
        public boolean exists() {
            return !isInconsistent(path) && getDelegate().exists();
        }
        
        @Override
        public void delete() throws IOException {
            checkReadOnly();
            TIMER.schedule(toTask(getDelegate()::delete), consistencyDelayMs);
        }
        
        @Override
        public long getSize() throws IOException {
            checkConsistency();
            return getDelegate().getSize();
        }
        
        @Override
        public long getCreationTime() throws IOException {
            checkConsistency();
            return getDelegate().getCreationTime();
        }
        
        @Override
        public long getLastModifiedTime() throws IOException {
            checkConsistency();
            return getDelegate().getLastModifiedTime();
        }
        
        @Override
        @Nonnull
        public String getHash(@Nonnull HashAlgorithm hashAlgorithm) throws IOException {
            checkConsistency();
            return getDelegate().getHash(hashAlgorithm);
        }
        
        @Override
        @Nonnull
        public InputStream getInputStream() throws IOException {
            checkConsistency();
            return getDelegate().getInputStream();
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
    private class EventuallyConsistentFolder extends LateConsistentEntry<Folder> implements Folder {
        private final String path;
        /** True if this folder was returned by {@link Folder#mkdir(String)} in session consistent mode: its creator can write in it even if it is not yet visible. */
        private final boolean created;
        
        EventuallyConsistentFolder(Folder delegate, boolean created) throws IOException {
            super(delegate);
            this.path = delegate.getPath();
            this.created = created;
        }
        
        @Override
        public boolean isFile() {
            return false;
        }
        
        @Override
        public boolean isFolder() {
            return !isInconsistent(path) && getDelegate().isFolder();
        }
        
        @Override
        public boolean exists() {
            return !isInconsistent(path) && getDelegate().exists();
        }

        private void checkConsistency() throws IOException {
            if (isInconsistent(path)) {
                throw new FileNotFoundException("Folder does not exist: " + path);
            }
        }
        
        @Override
        public void delete() throws IOException {
            checkReadOnly();
            if (getDelegate().getPath().equals(FileProvider.ROOT_PATH)) {
                throw new IOException("Cannot delete root folder");
            }
            TIMER.schedule(toTask(getDelegate()::delete), consistencyDelayMs);
        }
        
        @Override
        @Nonnull
        public List<Entry> list() throws IOException {
            checkConsistency();
            
            List<Entry> entries = getDelegate().list();
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
            if (!created) {
                checkConsistency();
            }
            File file = getDelegate().copy(fileName, content, progressListener);
            recordCreation(file.getPath());
            return new EventuallyConsistentFile(file);
        }
        
        @Override
        @Nonnull
        public Folder mkdir(@Nonnull String folderName) throws IOException {
            if (!created) {
                checkConsistency();
            }
            final boolean alreadyExists = provider.get(getDelegate().getPath() + "/" + folderName).isFolder();
            Folder folder = getDelegate().mkdir(folderName);
            if (!alreadyExists) {
                // mkdir is idempotent: only a real creation is subject to the consistency delay
                recordCreation(folder.getPath());
            }
            return new EventuallyConsistentFolder(folder, sessionConsistent);
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
