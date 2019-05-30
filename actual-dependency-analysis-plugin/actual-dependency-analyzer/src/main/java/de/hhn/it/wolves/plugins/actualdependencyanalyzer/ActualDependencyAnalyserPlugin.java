package de.hhn.it.wolves.plugins.actualdependencyanalyzer;


import de.hhn.it.wolves.domain.*;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.analyze.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

import java.io.IOException;
import java.lang.reflect.Field;

import java.util.Collections;


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
        //fixes the problems where the build is null in some cases (the projects dont have a build element <build> in their pom file
        if (model.getBuild() == null) {
            model.setBuild(new Build());

            MavenXpp3Writer writer = new MavenXpp3Writer();
            try {
                writer.write(new FileOutputStream(f), model);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MavenProject project = new MavenProject(model);
        System.out.println(project.getModel().getBuild()); // for debug purposes

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(f);
        request.setGoals(Collections.singletonList("compile"));

        Invoker invoker = new DefaultInvoker();
        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        File outputDirectory = new File("C:/Users/Marvin/Documents/GitHub/example-java-maven/target");

        System.out.println(project.getModel().getBuild()); // is null
        project.getModel().getBuild().setOutputDirectory(outputDirectory.getAbsolutePath());
        project.getModel().getBuild().setTestOutputDirectory(outputDirectory.getAbsolutePath());

        AnalyzeMojo mojo = new AnalyzeMojo();
        try {
            Field projectField = mojo.getClass().getSuperclass().getDeclaredField("project");
            projectField.setAccessible(true);
            projectField.set(mojo, project);
            Field outputDirectoryField = mojo.getClass().getSuperclass().getDeclaredField("outputDirectory");
            outputDirectoryField.setAccessible(true);
            outputDirectoryField.set(mojo, outputDirectory);
            Field analyzerField = mojo.getClass().getSuperclass().getDeclaredField("analyzer");
            analyzerField.setAccessible(true);
            analyzerField.set(mojo, "default");


        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }


        try {
            Context c = new DefaultContext();
            c.put("plexus", new DefaultPlexusContainer());
            mojo.contextualize(c);
            mojo.execute();
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        } catch (MojoFailureException e) {
            e.printStackTrace();
        } catch (PlexusContainerException e) {
            e.printStackTrace();
        } catch (ContextException e) {
            e.printStackTrace();
        }
        return new ActualDependencyAnalysisResult(info);

    }
}
