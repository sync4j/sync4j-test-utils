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
    <version>0.0.1</version>
</dependency>
```

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
