Description
===
This maven plugin calls the protocol buffers compiler.

This is a fork of the [dtrott maven plugin](https://github.com/dtrott/maven-protoc-plugin). If you can help it, you should use that.
Usage
=====

Here is an usage example; the plugin search for ```.proto``` files inside the ```${project.basedir}/src/main/resources/``` folder and put the compiled java classes inside the ```${project.basedir}/src/main/proto/```

````
...
<properties>
  <maven.lifecycle.mapping.plugin.version>1.0.0</maven.lifecycle.mapping.plugin.version>
  <protoc-plugin.version>0.1</protoc-plugin.version>
</properties>

...
<build>
  <pluginManagement> <!-- If you want to attach the plugin to m2e -->
    <plugins>
      <plugin>
        <groupId>org.eclipse.m2e</groupId>
        <artifactId>lifecycle-mapping</artifactId>
        <version>${maven.lifecycle.mapping.plugin.version}</version>
        <configuration>
          <lifecycleMappingMetadata>
            <pluginExecutions>
              <pluginExecution>
                <pluginExecutionFilter>
                  <groupId>org.wouldgo</groupId>
                  <artifactId>protoc-plugin</artifactId>
                  <versionRange>${protoc-plugin.version}</versionRange>
                  <goals>
                    <goal>compile</goal>
                  </goals>
                </pluginExecutionFilter>
                <action>
                  <execute>
                    <runOnIncremental>true</runOnIncremental>
                    <runOnConfiguration>true</runOnConfiguration>
                  </execute>
                </action>
              </pluginExecution>
            </pluginExecutions>
          </lifecycleMappingMetadata>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
  <plugins>
    <plugin>
      <groupId>org.wouldgo</groupId>
      <artifactId>protoc-plugin</artifactId>
      <version>${protoc-plugin.version}</version>
      <executions>
        <execution>
          <phase>generate-sources</phase>
          <goals>
            <goal>compile</goal>
          </goals>
          <configuration>
            <protoSourceRoot>${project.basedir}/src/main/resources/</protoSourceRoot>
            <outputDirectory>${project.basedir}/src/main/proto/</outputDirectory>
          </configuration>
        </execution>
      </executions>
    </plugin>
    ...
  </plugins>
<build>
```
