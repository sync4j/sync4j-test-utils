# sync4j-test-utils
Some JUnit5 test utilities to help validating sync4j file providers

## Requirements

- Java 17

## Installation

Import with Maven:

```xml
<dependency>
    <groupId>com.fathzer</groupId>
    <artifactId>sync4j-test-utils</artifactId>
    <version>0.0.2</version>
    <scope>test</scope>
</dependency>
```

## Validating a file provider

`AbstractFileProviderTest` is a JUnit 5 test class that checks that a provider respects the contract described in the [FileProvider](https://github.com/sync4j/sync4j/blob/main/src/main/java/com/fathzer/sync4j/FileProvider.java) javadoc (paths, `mkdir` idempotence, error types, hashes, etc...). Extend it and tell it how to create your provider:

```java
class MyProviderTest extends AbstractFileProviderTest {
    @Override
    protected FileProvider createFileProvider(TestInfo testInfo) throws IOException {
        return new MyProvider(...); // return null to skip the tests (if a credential is missing, for instance)
    }

    @Override
    protected UnderlyingFileSystem getUnderlyingFileSystem() {
        return null; // or an object that reads and writes the storage without using the provider
    }
}
```

If `getUnderlyingFileSystem()` returns an [UnderlyingFileSystem](src/main/java/com/fathzer/sync4j/test/UnderlyingFileSystem.java), the tests also check that what the provider does is really done on the storage, and that the provider sees the changes made directly on the storage.

The provider is closed after each test (`cleanUpProvider()`, that you can override to clean up the storage).

### Eventually consistent providers

Most cloud storages are [eventually consistent](https://en.wikipedia.org/wiki/Eventual_consistency): an entry that was just created may not be visible immediately. If your provider is not strongly consistent, override `getConsistencyTimeout()` to tell how long the tests should wait for a write to become visible:

```java
    @Override
    protected Duration getConsistencyTimeout() {
        return Duration.ofSeconds(5);
    }
```

The tests then retry their assertions until they succeed or the timeout is reached. Your own tests can do the same with `doAssert(() -> ...)`. By default, the timeout is zero and each assertion is tried only once, which is what a strongly consistent provider (local file system, for instance) requires.

## Simulating an eventually consistent storage

`EventuallyConsistentProvider` (package `com.fathzer.sync4j.eventually`) wraps any provider (the in-memory provider of sync4j, for instance) and makes it behave like an eventually consistent storage. This lets you test code that uses providers (a synchronization algorithm, for instance) against such a storage, without a real one: a newly created entry stays invisible for a configurable delay. During that delay, it is missing from its parent's `list()`, its `exists()` returns false, and some operations throw `FileNotFoundException`.

```java
// Strict mode: a folder that is not yet visible can't be used at all, even through the object returned by mkdir
FileProvider strict = new EventuallyConsistentProvider(new MemoryFileProvider(), 150);
// Session consistent mode: the object returned by mkdir accepts mkdir and copy immediately (reads remain eventually consistent)
FileProvider session = new EventuallyConsistentProvider(new MemoryFileProvider(), 150, true);
```

- The strict mode models a distributed storage where a request may be served by a node that does not know the new folder yet.
- In both modes, `mkdir` is idempotent, and `isStronglyConsistent()` returns false.
- This provider is thread safe, but it is meant for tests only. It is not optimized, and it never forgets the entries it created (memory leak).

If you get Mockito warnings (if you're using JDK 21+), like this one:

```
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/home/jma/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.7/byte-buddy-agent-1.17.7.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

You can disable them by adding the magic incantation to your `pom.xml`:

In the `<properties>` section, add:
```xml
    <argLine></argLine>
```

In the build section, add:
```xml
  <build>
    <plugins>
      <!-- Magic incantations to prevent Mockito warnings with JDK 21+ -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>properties</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
            <argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>
        </configuration>
      </plugin>
      <!-- End of magic Mockito incantations -->
    </plugins>
  </build>
```
