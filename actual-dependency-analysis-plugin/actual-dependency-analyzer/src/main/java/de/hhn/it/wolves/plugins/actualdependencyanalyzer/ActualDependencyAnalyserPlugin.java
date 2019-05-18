package de.hhn.it.wolves.plugins.actualdependencyanalyzer;


import de.hhn.it.wolves.domain.*;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.analyze.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActualDependencyAnalyserPlugin extends AbstractAnalyzeMojo {


    public static void main(String[] args) throws MavenInvocationException {
        RepositoryInfo repositoryInfo = new RepositoryInfo();
        ActualDependencyAnalyserPlugin plugin = new ActualDependencyAnalyserPlugin();
        plugin.analyseRepository(repositoryInfo);
    }


    public AnalysisResult analyseRepository(RepositoryInfo info) throws MavenInvocationException {
        Model model = null;
        FileReader reader = null;
        File f = null;

        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        try {
             f = new File("C:/Users/Marvin/Documents/GitHub/example-java-maven/pom.xml");
            reader = new FileReader(f);
            model = mavenreader.read(reader);
            model.setPomFile(f);
        } catch (Exception ex) {
        }
        MavenProject project = new MavenProject(model);

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File("C:/Users/Marvin/Documents/GitHub/example-java-maven/pom.xml"));
        request.setGoals(Collections.singletonList("compile"));

        Invoker invoker = new DefaultInvoker();
        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        File outputDirectory = new File("C:/Users/Marvin/Documents/GitHub/example-java-maven/target");
      //  String analyzer = "maven-dependency-analyzer";

       AnalyzeMojo mojo = new AnalyzeMojo();
        try {
            Field projectField = mojo.getClass().getSuperclass().getDeclaredField("project");
            projectField.setAccessible(true);
            projectField.set(mojo,project);
            Field outputDirectoryField = mojo.getClass().getSuperclass().getDeclaredField("outputDirectory");
            outputDirectoryField.setAccessible(true);
            outputDirectoryField.set(mojo,outputDirectory);
            //Field analyzerField = mojo.getClass().getSuperclass().getDeclaredField("analyzer");
           // analyzerField.setAccessible(true);
           // analyzerField.set(mojo,analyzer);

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }


        try {
            mojo.execute();
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        } catch (MojoFailureException e) {
            e.printStackTrace();
        }
        return new ActualDependencyAnalysisResult(info);
    }
}
