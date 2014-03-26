package com.google.protobuf.maven;

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.collect.ImmutableList;

/**
 * This mojo executes the {@code protoc} compiler for generating java sources
 * from protocol buffer definitions. It also searches dependency artifacts for
 * proto files and includes them in the protopath so that they can be
 * referenced. Finally, it adds the proto files to the project as resources so
 * that they are included in the final artifact.
 *
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public final class ProtocCompileMojo extends AbstractProtocMojo {

  /**
   * The source directories containing the sources to be compiled.
   *
   */
  @Parameter(defaultValue = "${basedir}/src/main/proto", required = true)
  private File protoSourceRoot;

  /**
   * This is the directory into which the {@code .java} will be created.
   *
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/protoc", required = true)
  private File outputDirectory;

  @Override
  protected List<Artifact> getDependencyArtifacts() {
    // TODO(gak): maven-project needs generics
    // TODO(wouldgo) don't require compile scope for other atificats that contains protos...
    List<Artifact> compileArtifacts = this.project.getCompileArtifacts();
    return compileArtifacts;
  }

  @Override
  protected File getOutputDirectory() {
    return this.outputDirectory;
  }

  @Override
  protected File getProtoSourceRoot() {
    return this.protoSourceRoot;
  }

  @Override
  protected void attachFiles() {
    this.project.addCompileSourceRoot(this.outputDirectory.getAbsolutePath());
    this.projectHelper.addResource(this.project, this.protoSourceRoot.getAbsolutePath(),
        ImmutableList.of("**/*.proto"), ImmutableList.of());
  }
}
