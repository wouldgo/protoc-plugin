package com.google.protobuf.maven;

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.collect.ImmutableList;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Mojo(name = "test-compile", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST)
public final class ProtocTestCompileMojo extends AbstractProtocMojo {

  /**
   * The source directories containing the sources to be compiled.
   *
   */
  @SuppressFBWarnings({"UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD"})
  @Parameter(defaultValue = "${basedir}/src/test/proto", required = true)
  private File protoTestSourceRoot;

  /**
   * This is the directory into which the {@code .java} will be created.
   *
   */
  @SuppressFBWarnings({"UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD"})
  @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/protoc", required = true)
  private File outputDirectory;

  @Override
  protected void attachFiles() {
    this.project.addTestCompileSourceRoot(this.outputDirectory.getAbsolutePath());
    this.projectHelper.addTestResource(this.project, this.protoTestSourceRoot.getAbsolutePath(),
        ImmutableList.of("**/*.proto"), ImmutableList.of());
  }

  @Override
  protected List<Artifact> getDependencyArtifacts() {
    // TODO(gak): maven-project needs generics
    // TODO(wouldgo) don't require compile scope for other atificats that contains protos...
    List<Artifact> testArtifacts = this.project.getTestArtifacts();
    return testArtifacts;
  }

  @Override
  protected File getOutputDirectory() {
    return this.outputDirectory;
  }

  @Override
  protected File getProtoSourceRoot() {
    return this.protoTestSourceRoot;
  }
}
