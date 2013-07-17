package com.google.protobuf.maven;

import java.io.File;
import java.util.List;

import org.apache.maven.artifact.Artifact;

import com.google.common.collect.ImmutableList;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This mojo executes the {@code protoc} compiler for generating java sources
 * from protocol buffer definitions. It also searches dependency artifacts for
 * proto files and includes them in the protopath so that they can be
 * referenced. Finally, it adds the proto files to the project as resources so
 * that they are included in the final artifact.
 *
 * @phase generate-sources
 * @goal compile
 * @requiresDependencyResolution compile
 */

public final class ProtocCompileMojo extends AbstractProtocMojo {

    /**
     * The source directories containing the sources to be compiled.
     *
     * @parameter default-value="${basedir}/src/main/proto"
     * @required
     */
    @SuppressFBWarnings({"UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD"})
    private File protoSourceRoot;

    /**
     * This is the directory into which the {@code .java} will be created.
     *
     * @parameter default-value="${project.build.directory}/generated-sources/protoc"
     * @required
     */
    @SuppressFBWarnings({"UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD"})
    private File outputDirectory;

    @Override
    protected List<Artifact> getDependencyArtifacts() {
        // TODO(gak): maven-project needs generics
        @SuppressWarnings("unchecked")
        List<Artifact> compileArtifacts = project.getCompileArtifacts();
        return compileArtifacts;
    }

    @Override
    protected File getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected File getProtoSourceRoot() {
        return protoSourceRoot;
    }

    @Override
    protected void attachFiles() {
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        projectHelper.addResource(project, protoSourceRoot.getAbsolutePath(),
                ImmutableList.of("**/*.proto"), ImmutableList.of());
    }
}
