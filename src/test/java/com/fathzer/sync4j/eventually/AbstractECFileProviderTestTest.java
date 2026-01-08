package com.fathzer.sync4j.eventually;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.TestInfo;

import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.memory.MemoryFileProvider;
import com.fathzer.sync4j.test.AbstractFileProviderTest;
import com.fathzer.sync4j.test.UnderlyingFileSystem;

class AbstractECFileProviderTestTest extends AbstractFileProviderTest {

    @Override
    protected FileProvider createFileProvider(TestInfo testInfo) throws IOException {
        return new EventuallyConsistentProvider(new MemoryFileProvider(), 150);
    }

    @Override
    protected Duration getConsistencyTimeout() {
        return Duration.ofMillis(500);
    }

    @Override
    protected UnderlyingFileSystem getUnderlyingFileSystem() {
        return null;
    }
}
