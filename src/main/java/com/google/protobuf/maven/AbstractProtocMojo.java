package com.google.protobuf.maven;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Abstract Mojo implementation.
 * <p/>
 * This class is extended by {@link org.wouldgo.protoc_plugin.ProtocCompileMojo} and
 * {@link org.wouldgo.protoc_plugin.ProtocTestCompileMojo} in order to override the specific configuration for
 * compiling the main or test classes respectively.
 *
 * @author Gregory Kick
 * @author David Trott
 * @author Brice Figureau
 */
abstract class AbstractProtocMojo extends AbstractMojo {

	private static final String PROTO_FILE_SUFFIX = ".proto";
	private static final String DEFAULT_INCLUDES = "**/*" + AbstractProtocMojo.PROTO_FILE_SUFFIX;

	/**
	 * The current Maven thisProject.
	 *
	 */
	@Component
	protected MavenProject project;

	/**
	 * A helper used to add resources to the thisProject.
	 *
	 */
	@Component
	protected MavenProjectHelper projectHelper;

	/**
	 * This is the path to the {@code protoc} executable. By default it will search the {@code $PATH}.
	 *
	 */
	@Parameter(defaultValue = "protoc", required = true)
	private String protocExecutable;

	@Parameter
	private File[] additionalProtoPathElements = new File[]{};

	/**
	 * Since {@code protoc} cannot access jars, proto files in dependencies are extracted to this location
	 * and deleted on exit. This directory is always cleaned during execution.
	 *
	 */
	@Parameter(defaultValue = "${project.build.directory}/protoc-dependencies", required = true)
	private File temporaryProtoFileDirectory;

	/**
	 * This is the path to the local maven {@code repository}.
	 *
	 */
	@Parameter(defaultValue = "${localRepository}", required = true)
	private ArtifactRepository localRepository;

	/**
	 * Set this to {@code false} to disable hashing of dependent jar paths.
	 * <p/>
	 * This plugin expands jars on the classpath looking for embedded .proto files.
	 * Normally these paths are hashed (MD5) to avoid issues with long file names on windows.
	 * However if this property is set to {@code false} longer paths will be used.
	 *
	 */
	@Parameter(defaultValue = "true", required = true)
	private boolean hashDependentPaths;

	@Parameter
	private Set<String> includes = ImmutableSet.of(AbstractProtocMojo.DEFAULT_INCLUDES);

	@Parameter
	private Set<String> excludes = ImmutableSet.of();

	@Parameter
	private long staleMillis = 0;

	@Parameter
	private boolean checkStaleness = false;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		this.checkParameters();
		final File protoSourceRoot = this.getProtoSourceRoot();
		if (protoSourceRoot.exists()) {
			try {
				ImmutableSet<File> protoFiles = this.findProtoFilesInDirectory(protoSourceRoot);
				final File outputDirectory = this.getOutputDirectory();
				ImmutableSet<File> outputFiles = this.findGeneratedFilesInDirectory(this.getOutputDirectory());

				if (protoFiles.isEmpty()) {
					this.getLog().info("No proto files to compile.");
				} else if (this.checkStaleness && this.lastModified(protoFiles) + this.staleMillis < this.lastModified(outputFiles)) {
					this.getLog().info("Skipping compilation because target directory newer than sources.");
					this.attachFiles();
				} else {
					ImmutableSet<File> derivedProtoPathElements =
							this.makeProtoPathFromJars(this.temporaryProtoFileDirectory, this.getDependencyArtifactFiles());

					if(!outputDirectory.exists() && !outputDirectory.mkdirs()) {
						if (!outputDirectory.exists()) {
							throw new MojoExecutionException("Could not create directories: " + outputDirectory.getAbsolutePath() + " does not exist!");
						}
						if (!outputDirectory.isDirectory()) {
							throw new MojoExecutionException("Could not create directories: " + outputDirectory.getAbsolutePath() + " exists but is not a directory!");
						}
					}

					// Quick fix to fix issues with two mvn installs in a row (ie no clean)
					AbstractProtocMojo.clean(outputDirectory);

					Protoc protoc = new Protoc.Builder(this.protocExecutable, outputDirectory)
					.addProtoPathElement(protoSourceRoot)
					.addProtoPathElements(derivedProtoPathElements)
					.addProtoPathElements(Arrays.asList(this.additionalProtoPathElements))
					.addProtoFiles(protoFiles)
					.build();
					final int exitStatus = protoc.compile();
					if (exitStatus != 0) {
						this.getLog().error("protoc failed output: " + protoc.getOutput());
						this.getLog().error("protoc failed error: " + protoc.getError());
						throw new MojoFailureException(
								"protoc did not exit cleanly. Review output for more information.");
					}
					this.attachFiles();
				}
			} catch (IOException e) {
				throw new MojoExecutionException("An IO error occured", e);
			} catch (IllegalArgumentException e) {
				throw new MojoFailureException("protoc failed to execute because: " + e.getMessage(), e);
			} catch (CommandLineException e) {
				throw new MojoExecutionException("An error occurred while invoking protoc.", e);
			}
		} else {
			this.getLog().info(String.format("%s does not exist. Review the configuration or consider disabling the plugin.",
					protoSourceRoot));
		}
	}

	ImmutableSet<File> findGeneratedFilesInDirectory(File directory) throws IOException {
		if (directory == null || !directory.isDirectory())
			return ImmutableSet.of();

		// TODO(gak): plexus-utils needs generics
		@SuppressWarnings("unchecked")
		List<File> javaFilesInDirectory = FileUtils.getFiles(directory, "**/*.java", null);
		return ImmutableSet.copyOf(javaFilesInDirectory);
	}

	private long lastModified(ImmutableSet<File> files) {
		long result = 0;
		for (File file : files) {
			if (file.lastModified() > result)
				result = file.lastModified();
		}
		return result;
	}

	private void checkParameters() {
		Preconditions.checkNotNull(this.project, "project");
		Preconditions.checkNotNull(this.projectHelper, "projectHelper");
		Preconditions.checkNotNull(this.protocExecutable, "protocExecutable");
		final File protoSourceRoot = this.getProtoSourceRoot();
		Preconditions.checkNotNull(protoSourceRoot);
		Preconditions.checkArgument(!protoSourceRoot.isFile(), "protoSourceRoot is a file, not a diretory");
		Preconditions.checkNotNull(this.temporaryProtoFileDirectory, "temporaryProtoFileDirectory");
		Preconditions.checkState(!this.temporaryProtoFileDirectory.isFile(), "temporaryProtoFileDirectory is a file, not a directory");
		final File outputDirectory = this.getOutputDirectory();
		Preconditions.checkNotNull(outputDirectory);
		Preconditions.checkState(!outputDirectory.isFile(), "the outputDirectory is a file, not a directory");
	}

	protected abstract File getProtoSourceRoot();

	protected abstract List<Artifact> getDependencyArtifacts();

	protected abstract File getOutputDirectory();

	protected abstract void attachFiles();

	/**
	 * Gets the {@link File} for each dependency artifact.
	 *
	 * @return A set of all dependency artifacts.
	 */
	private ImmutableSet<File> getDependencyArtifactFiles() {
		Set<File> dependencyArtifactFiles = Sets.newHashSet();
		for (Artifact artifact : this.getDependencyArtifacts()) {
			dependencyArtifactFiles.add(artifact.getFile());
		}
		return ImmutableSet.copyOf(dependencyArtifactFiles);
	}

	/**
	 * @throws IOException
	 */
	ImmutableSet<File> makeProtoPathFromJars(File tmpProtoFileDirectory, Iterable<File> classpathElementFiles)
			throws IOException, MojoExecutionException {
		Preconditions.checkNotNull(classpathElementFiles, "classpathElementFiles");
		// clean the temporary directory to ensure that stale files aren't used
		if (tmpProtoFileDirectory.exists()) {
			FileUtils.cleanDirectory(tmpProtoFileDirectory);
		}
		Set<File> protoDirectories = Sets.newHashSet();
		for (File classpathElementFile : classpathElementFiles) {
			// for some reason under IAM, we receive poms as dependent files
			// I am excluding .xml rather than including .jar as there may be other extensions in use (sar, har, zip)
			if (classpathElementFile.isFile() && classpathElementFile.canRead() &&
					!classpathElementFile.getName().endsWith(".xml")) {

				// create the jar file. the constructor validates.
				JarFile classpathJar = null;
				try {
					classpathJar = new JarFile(classpathElementFile);
					for (JarEntry jarEntry : Collections.list(classpathJar.entries())) {
						final String jarEntryName = jarEntry.getName();
						if (jarEntry.getName().endsWith(AbstractProtocMojo.PROTO_FILE_SUFFIX)) {
							final File uncompressedCopy =
									new File(new File(tmpProtoFileDirectory,
											this.truncatePath(classpathJar.getName())), jarEntryName);

							File outputDirectory = uncompressedCopy.getParentFile();
							if(!outputDirectory.mkdirs()) {
								if (!outputDirectory.exists()) {
									throw new MojoExecutionException("Could not create directories: " + outputDirectory.getAbsolutePath() + " does not exist!");
								}
								if (!outputDirectory.isDirectory()) {
									throw new MojoExecutionException("Could not create directories: " + outputDirectory.getAbsolutePath() + " exists but is not a directory!");
								}
							}

							FileUtils.copyStreamToFile(new RawInputStreamFacade(classpathJar
									.getInputStream(jarEntry)), uncompressedCopy);
							protoDirectories.add(uncompressedCopy.getParentFile());
						}
					}
				} catch (IOException e) {
					throw new IllegalArgumentException(String.format(
							"%s was not a readable artifact", classpathElementFile));
				}
				finally {
					if (classpathJar != null) {
						classpathJar.close();
					}
				}
			} else if (classpathElementFile.isDirectory()) {
				File[] protoFiles = classpathElementFile.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(AbstractProtocMojo.PROTO_FILE_SUFFIX);
					}
				});

				if (protoFiles.length > 0) {
					protoDirectories.add(classpathElementFile);
				}
			}
		}
		return ImmutableSet.copyOf(protoDirectories);
	}

	ImmutableSet<File> findProtoFilesInDirectory(File directory) throws IOException {
		Preconditions.checkNotNull(directory);
		Preconditions.checkArgument(directory.isDirectory(), "%s is not a directory", directory);
		// TODO(gak): plexus-utils needs generics
		@SuppressWarnings("unchecked")
		List<File> protoFilesInDirectory = FileUtils.getFiles(directory, Joiner.on(",").join(this.includes), Joiner.on(",").join(this.excludes));
		return ImmutableSet.copyOf(protoFilesInDirectory);
	}

	ImmutableSet<File> findProtoFilesInDirectories(Iterable<File> directories) throws IOException {
		Preconditions.checkNotNull(directories);
		Set<File> protoFiles = Sets.newHashSet();
		for (File directory : directories) {
			protoFiles.addAll(this.findProtoFilesInDirectory(directory));
		}
		return ImmutableSet.copyOf(protoFiles);
	}

	/**
	 * Truncates the path of jar files so that they are relative to the local repository.
	 *
	 * @param jarPath the full path of a jar file.
	 * @return the truncated path relative to the local repository or root of the drive.
	 */
	String truncatePath(final String jarPath) throws MojoExecutionException {

		if (this.hashDependentPaths) {
			try {
				return AbstractProtocMojo.toHexString(MessageDigest.getInstance("MD5").digest(jarPath.getBytes(Charsets.UTF_8)));
			} catch (NoSuchAlgorithmException e) {
				throw new MojoExecutionException("Failed to expand dependent jar", e);
			}
		}

		String repository = this.localRepository.getBasedir().replace('\\', '/');
		if (!repository.endsWith("/")) {
			repository += "/";
		}

		String path = jarPath.replace('\\', '/');
		int repositoryIndex = path.indexOf(repository);
		if (repositoryIndex != -1) {
			path = path.substring(repositoryIndex + repository.length());
		}

		// By now the path should be good, but do a final check to fix windows machines.
		int colonIndex = path.indexOf(':');
		if (colonIndex != -1) {
			// 2 = :\ in C:\
			path = path.substring(colonIndex + 2);
		}

		return path;
	}

	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

	public static String toHexString(byte[] byteArray) {
		final StringBuilder hexString = new StringBuilder(2 * byteArray.length);
		for (final byte b : byteArray) {
			hexString.append(AbstractProtocMojo.HEX_CHARS[(b & 0xF0) >> 4]).append(AbstractProtocMojo.HEX_CHARS[b & 0x0F]);
		}
		return hexString.toString();
	}

	public static boolean clean(File aFile) {
		if(aFile.isDirectory() && aFile.list().length > 0) {
			File[] files = aFile.listFiles();

			boolean areDeleted = true;
			for (File aFileInFolder : files) {

				if (!aFileInFolder.isHidden()) { // Doesn't delete .svn folder

					areDeleted &= AbstractProtocMojo.clean(aFileInFolder);
				}
			}

			return areDeleted;
		} else if (!aFile.isDirectory() && !aFile.isHidden()) {

			return aFile.delete();
		} else {

		  return false;
		}
	}
}
