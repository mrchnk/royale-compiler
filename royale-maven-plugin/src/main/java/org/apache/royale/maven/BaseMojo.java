/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.royale.maven;

import org.apache.royale.maven.utils.DependencyHelper;
import org.apache.flex.tools.FlexTool;
import org.apache.flex.tools.FlexToolGroup;
import org.apache.flex.tools.FlexToolRegistry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.eclipse.aether.RepositorySystemSession;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by christoferdutz on 22.04.16.
 */
public abstract class BaseMojo
        extends AbstractMojo
{

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue="${project.build.directory}")
    protected File outputDirectory;

    @Parameter
    private Namespace[] namespaces;

    @Parameter
    private String[] includeClasses;

    @Parameter
    private IncludeFile[] includeFiles;

    @Parameter
    private Define[] defines;

    @Parameter
    private String[] keepAs3Metadata;

    /**
     * When compiling framework libraries, it might be desirable to link the
     * dependencies externally, by setting this option to 'true' all dependencies
     * are added to the external-library-path, no matter what scope they have.
     */
    @Parameter(defaultValue = "false")
    private boolean forceSwcExternalLibraryPath;

    @Parameter(defaultValue = "11.1")
    private String targetPlayer;

    @Parameter(defaultValue = "false")
    private boolean includeSources;

    @Parameter
    protected boolean debug = false;

    @Parameter
    protected boolean failOnCompilerWarnings = false;

    @Parameter
    protected boolean allowSubclassOverrides = true;
    
    @Parameter
    private Boolean includeLookupOnly = null;

    @Parameter(readonly = true, defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repositorySystemSession;

    @Parameter
    private String additionalCompilerOptions = null;

    @Component
    private ProjectDependenciesResolver projectDependenciesResolver;

    protected boolean skip() {
        return false;
    }

    protected abstract String getConfigFileName() throws MojoExecutionException;

    protected abstract File getOutput() throws MojoExecutionException;

    protected VelocityContext getVelocityContext() throws MojoExecutionException {
        VelocityContext context = new VelocityContext();

        List<Artifact> allLibraries = DependencyHelper.getAllLibraries(
                project, repositorySystemSession, projectDependenciesResolver);
        List<Artifact> filteredLibraries = getFilteredLibraries(allLibraries);
        List<Artifact> libraries = getLibraries(filteredLibraries);
        List<Artifact> jsLibraries = getJSLibraries(filteredLibraries);
        List<Artifact> swfLibraries = getSWFLibraries(filteredLibraries);
        List<Artifact> externalLibraries = getExternalLibraries(filteredLibraries);
        List<Artifact> jsExternalLibraries = getJSExternalLibraries(filteredLibraries);
        List<Artifact> swfExternalLibraries = getSWFExternalLibraries(filteredLibraries);
        List<Artifact> themeLibraries = getThemeLibraries(filteredLibraries);
        List<String> sourcePaths = getSourcePaths();
        context.put("libraries", libraries);
        context.put("externalLibraries", externalLibraries);
        context.put("jsLibraries", jsLibraries);
        context.put("jsExternalLibraries", jsExternalLibraries);
        context.put("swfLibraries", swfLibraries);
        context.put("swfExternalLibraries", swfExternalLibraries);
        context.put("themeLibraries", themeLibraries);
        context.put("sourcePaths", sourcePaths);
        context.put("namespaces", getNamespaces());
        context.put("jsNamespaces", getNamespacesJS());
        context.put("namespaceUris", getNamespaceUris());
        context.put("includeClasses", includeClasses);
        context.put("includeFiles", includeFiles);
        context.put("defines", getDefines());
        context.put("keepAs3Metadata", keepAs3Metadata);
        context.put("targetPlayer", targetPlayer);
        context.put("includeSources", includeSources);
        context.put("debug", debug);
        context.put("allowSubclassOverrides", allowSubclassOverrides);
        if(includeLookupOnly != null) {
            context.put("includeLookupOnly", includeLookupOnly);
        }
        context.put("output", getOutput());

        return context;
    }

    protected abstract String getToolGroupName();

    protected abstract String getFlexTool();

    protected List<Namespace> getNamespaces() {
        List<Namespace> namespaces = new LinkedList<Namespace>();
        if(this.namespaces != null) {
            for (Namespace namespace : this.namespaces) {
                namespaces.add(namespace);
            }
        }
        return namespaces;
    }

    protected List<Namespace> getNamespacesJS() {
        List<Namespace> namespaces = new LinkedList<Namespace>();
        if(this.namespaces != null) {
            for (Namespace namespace : this.namespaces) {
                namespaces.add(namespace);
            }
        }
        return namespaces;
    }
    
    protected Set<String> getNamespaceUris() {
        Set<String> namespaceUris = new HashSet<String>();
        for(Namespace namespace : getNamespaces()) {
            namespaceUris.add(namespace.getUri());
        }
        return namespaceUris;
    }

    @SuppressWarnings("unchecked")
    protected List<String> getSourcePaths() {
        List<String> sourcePaths = new LinkedList<String>();
        for(String sourcePath : (List<String>) project.getCompileSourceRoots()) {
            if(new File(sourcePath).exists()) {
                sourcePaths.add(sourcePath);
            }
        }
        return sourcePaths;
    }

    protected String getSourcePath(String resourceOnPath) {
        for(String path : getSourcePaths()) {
            File tmpFile = new File(path, resourceOnPath);
            if(tmpFile.exists()) {
                return tmpFile.getPath();
            }
        }
        return null;
    }

    /*@SuppressWarnings("unchecked")
    protected List<IncludeFile> getIncludedFiles() {
        List<IncludeFile> includedFiles = new LinkedList<IncludeFile>();

        // Add all manually added files.
        if(includeFiles != null) {
            includedFiles.addAll(Arrays.asList(includeFiles));
        }

        // Add all files in the resources directory.
        if(project.getResources() != null) {
            for(Resource resource : (List<Resource>) project.getResources()) {
                File resourceDirectory = new File(resource.getDirectory());
                if(resourceDirectory.exists()) {
                    Collection<File> files = FileUtils.listFiles(resourceDirectory,
                            new RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY);
                    for(File file : files) {
                        IncludeFile includeFile = new IncludeFile();
                        String relativePath = file.getPath().substring(resourceDirectory.getPath().length());
                        includeFile.setName(relativePath);
                        includeFile.setPath(file.getPath());
                        includedFiles.add(includeFile);
                    }
                }
            }
        }

        return includedFiles;
    }*/

    protected List<String> getCompilerArgs(File configFile) throws MojoExecutionException {
        List<String> args = new LinkedList<String>();
        args.add("-load-config=" + configFile.getPath());
        if(additionalCompilerOptions != null) {
            if (additionalCompilerOptions.contains("\n")) {
                additionalCompilerOptions = additionalCompilerOptions.replace("\n", "");
            }
            if (additionalCompilerOptions.contains(";"))
            {
                String[] options = additionalCompilerOptions.split(";");
                for (String option : options)
                {
                    if (option.trim().length() > 0)
                        args.add(option.trim());
                }
            }
            else {
                if (additionalCompilerOptions.trim().length() > 0)
                    args.add(additionalCompilerOptions.trim());
            }
        }
        return args;
    }

    public void execute()
            throws MojoExecutionException
    {
        // Skip this step if not all preconditions are met.
        if(skip()) {
            return;
        }

        // Prepare the config file.
        File configFile = new File(outputDirectory, getConfigFileName());
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        Template template = velocityEngine.getTemplate("config/" + getConfigFileName());
        VelocityContext context = getVelocityContext();

        if(!configFile.getParentFile().exists()) {
            if(!configFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException("Could not create output directory: " + configFile.getParent());
            }
        }
        FileWriter writer = null;
        try {
            writer = new FileWriter(configFile);
            template.merge(context, writer);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating config file at " + configFile.getPath());
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new MojoExecutionException("Error creating config file at " + configFile.getPath());
                }
            }
        }

        // Get the tool group.
        FlexToolRegistry toolRegistry = new FlexToolRegistry();
        FlexToolGroup toolGroup = toolRegistry.getToolGroup(getToolGroupName());
        if(toolGroup == null) {
            throw new MojoExecutionException("Could not find tool group: " + getToolGroupName());
        }

        // Get an instance of the compiler and run the build.
        FlexTool tool = toolGroup.getFlexTool(getFlexTool());
        String[] args = getCompilerArgs(configFile).toArray(new String[0]);
        getLog().info("Executing " + getFlexTool() + " in tool group " + getToolGroupName() + " with args: " + Arrays.toString(args));
        int exitCode = tool.execute(args);
        handleExitCode(exitCode);
    }

    protected void handleExitCode(int exitCode) throws MojoExecutionException {
        // Allow normal execution and execution with warnings.
        if(!((exitCode == 0) || (!failOnCompilerWarnings && (exitCode == 2)))) {
            throw new MojoExecutionException("There were errors during the build. Got return code " + exitCode);
        }
    }

    protected List<Artifact> getFilteredLibraries(List<Artifact> artifacts) {
        List<Artifact> filteredLibraries = new LinkedList<Artifact>();
        if(artifacts != null) {
            for(Artifact artifact : artifacts) {
                // Strip out non SWCs for now
                if("swc".equals(artifact.getType())) {
                    filteredLibraries.add(artifact);
                }
            }
        }
        return filteredLibraries;
    }

    protected List<Artifact> getLibraries(List<Artifact> artifacts) {
        if(!isForceSwcExternalLibraryPath()) {
            return internalGetLibraries(artifacts);
        }
        return Collections.emptyList();
    }

    protected List<Artifact> getJSLibraries(List<Artifact> artifacts) {
        if(!isForceSwcExternalLibraryPath()) {
            return internalGetLibrariesJS(artifacts);
        }
        return Collections.emptyList();
    }
    
    protected List<Artifact> getSWFLibraries(List<Artifact> artifacts) {
        if(!isForceSwcExternalLibraryPath()) {
            return internalGetLibrariesSWF(artifacts);
        }
        return Collections.emptyList();
    }
    
    protected List<Artifact> getThemeLibraries(List<Artifact> artifacts) {
        List<Artifact> themeLibraries = new LinkedList<Artifact>();
        for(Artifact artifact : artifacts) {
            if("theme".equalsIgnoreCase(artifact.getScope())) {
                themeLibraries.add(artifact);
            }
        }
        return themeLibraries;
    }

    protected List<Artifact> getExternalLibraries(List<Artifact> artifacts) {
        List<Artifact> externalLibraries = new LinkedList<Artifact>();
        for(Artifact artifact : artifacts) {
            if(("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()))
                    && includeLibrary(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    externalLibraries.add(artifact);
                }
            }
        }
        if(isForceSwcExternalLibraryPath()) {
            externalLibraries.addAll(internalGetLibraries(artifacts));
        }
        return externalLibraries;
    }

    protected List<Artifact> getJSExternalLibraries(List<Artifact> artifacts) {
        List<Artifact> externalLibraries = new LinkedList<Artifact>();
        for(Artifact artifact : artifacts) {
            if(("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()))
               && includeLibraryJS(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    externalLibraries.add(artifact);
                }
            }
        }
        if(isForceSwcExternalLibraryPath()) {
            externalLibraries.addAll(internalGetLibrariesJS(artifacts));
        }
        return externalLibraries;
    }
    
    protected List<Artifact> getSWFExternalLibraries(List<Artifact> artifacts) {
        List<Artifact> externalLibraries = new LinkedList<Artifact>();
        for(Artifact artifact : artifacts) {
            if(("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()))
               && includeLibrarySWF(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    externalLibraries.add(artifact);
                }
            }
        }
        if(isForceSwcExternalLibraryPath()) {
            externalLibraries.addAll(internalGetLibrariesSWF(artifacts));
        }
        return externalLibraries;
    }
    
    protected boolean isForceSwcExternalLibraryPath() {
        return forceSwcExternalLibraryPath;
    }

    private List<Artifact> internalGetLibraries(List<Artifact> artifacts) {
        List<Artifact> libraries = new LinkedList<Artifact>();
        for (Artifact artifact : artifacts) {
            if (!("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()) || "theme".equalsIgnoreCase(artifact.getScope()))
                    && includeLibrary(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    libraries.add(artifact);
                }
            }
        }
        return libraries;
    }

    private List<Artifact> internalGetLibrariesJS(List<Artifact> artifacts) {
        List<Artifact> libraries = new LinkedList<Artifact>();
        for (Artifact artifact : artifacts) {
            if (!("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()))
                && includeLibraryJS(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    libraries.add(artifact);
                }
            }
        }
        return libraries;
    }

    private List<Artifact> internalGetLibrariesSWF(List<Artifact> artifacts) {
        List<Artifact> libraries = new LinkedList<Artifact>();
        for (Artifact artifact : artifacts) {
            if (!("provided".equalsIgnoreCase(artifact.getScope()) || "runtime".equalsIgnoreCase(artifact.getScope()))
                && includeLibrarySWF(artifact)) {
                if(!"pom".equals(artifact.getType())) {
                    libraries.add(artifact);
                }
            }
        }
        return libraries;
    }
    
    protected List<Define> getDefines() throws MojoExecutionException {
        List<Define> defines = new LinkedList<Define>();
        if(this.defines != null) {
            for(Define define : this.defines) {
                defines.add(define);
            }
        }
        return defines;
    }

    protected boolean includeLibrary(Artifact library) {
        return true;
    }

    protected boolean includeLibraryJS(Artifact library) {
        return true;
    }
    
    protected boolean includeLibrarySWF(Artifact library) {
        return true;
    }

}
