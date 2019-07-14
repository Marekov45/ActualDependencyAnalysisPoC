package de.hhn.it.wolves.plugins.actualdependencyanalyzer;


import de.hhn.it.wolves.domain.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;

import java.lang.reflect.Field;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ActualDependencyAnalyserPlugin extends AbstractAnalyzeMojo {

    private static final Logger logger = LoggerFactory.getLogger(ActualDependencyAnalyserPlugin.class.getName());

    public static void main(String[] args) throws MavenInvocationException {
        BasicConfigurator.configure();
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
            f = new File("C:/Users/Marvin/IdeaProjects/JavaDeveloperFinalProject/pom.xml");
            reader = new FileReader(f);
            model = mavenreader.read(reader);
            model.setPomFile(f);

        } catch (Exception ex) {
            System.out.println(ex);
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

        request.setGoals(Collections.singletonList("dependency:list"));

        Invoker invoker = new DefaultInvoker();
        List<Artifact> artifacts = new ArrayList<>();


        ArrayList<String> test = new ArrayList<>();
        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[INFO]    ")) {
                        test.add(line);
                    }


                }
            });
            invoker.execute(request);

        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < test.size(); i++) {
            artifacts.add(buildArtifactFromString(test, i));
        }

        request.setGoals(Collections.singletonList("dependency:analyze"));
        List<Artifact> artifacts2 = new ArrayList<>();


        ArrayList<String> test2 = new ArrayList<>();
        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[WARNING] Used undeclared") || line.startsWith("[WARNING] Unused declared") || line.startsWith("[WARNING]    ")) {
                        test2.add(line);
                    }


                }
            });
            invoker.execute(request);

        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        test2.add("END");
        System.out.println(test2);
        if (test2.get(0).equals("[WARNING] Used undeclared dependencies found:") ||test2.get(0).equals("END") ) {
            test2.remove(0);
        }
        if(!test2.contains("[WARNING] Used undeclared dependencies found:") && !test2.contains("[WARNING] Unused declared dependencies found:")){
            test2.clear();
            System.out.println(test2);
        }
        // Problem: multi module maven projects are analyzed for every single module--> duplicates and used undeclared dependencies
        // would be mixed with unused declared dependencies and give wrong results
        String listString = String.join(" ", test2);
        System.out.println(listString);
        if (test2.contains("[WARNING] Used undeclared dependencies found:")) {
            String deps[] = StringUtils.substringsBetween(listString, "[WARNING] Unused declared dependencies found: ", "[WARNING] Used undeclared dependencies found:");
            System.out.println(Arrays.toString(deps));
            List<String> splitDependencies = new ArrayList<>();
            for (String dependency : deps) {
                splitDependencies.addAll(Arrays.asList(dependency.split("\\s+")));
            }
            //remove warnings
            for (int i = 0; i < splitDependencies.size(); i++) {
                if (splitDependencies.get(i).equals("[WARNING]")) {
                    splitDependencies.remove(i);
                }
            }

            System.out.println(splitDependencies);
        }
        if(!test2.isEmpty() && test2.contains("[WARNING] Unused declared dependencies found:")) {
            String onlyUnusedDeclared[] = StringUtils.substringsBetween(listString, "[WARNING] Unused declared dependencies found: ", "END");
            System.out.println(Arrays.toString(onlyUnusedDeclared));
            List<String> splitDependencies = new ArrayList<>();
            for (String dependency : onlyUnusedDeclared) {
                splitDependencies.addAll(Arrays.asList(dependency.split("\\s+")));
            }
            //remove warnings
            for (int i = 0; i < splitDependencies.size(); i++) {
                if (splitDependencies.get(i).equals("[WARNING]")) {
                    splitDependencies.remove(i);
                }
            }
            System.out.println(splitDependencies);
        }
                String mavenText = "[WARNING] Unused declared dependencies found:";
        //   if (!mavenPluginOutput.contains(mavenText)) {
        //       logger.info("This project has no unused declared dependencies.");
        //   return new AnalysisResultWithoutProcessing(repositoryInformation, getUniqueName());
        //   }
        for (int i = getUnusedDependencyIndex(test2, mavenText) + 1; i < test2.size(); i++) {
            if (!test2.get(i).equals("[WARNING] Unused declared dependencies found:") && !test2.get(i).equals("[WARNING] Used undeclared dependencies found:")) {
                artifacts2.add(buildArtifactFromString(test2, i));
            }
        }
        // System.out.println(artifacts);
        List<Artifact> noDuplicateArtifacts = new ArrayList<>();
        for (Artifact element : artifacts2) {
            if (!noDuplicateArtifacts.contains(element)) {
                noDuplicateArtifacts.add(element);
            }
        }
        // System.out.println(noDuplicateArtifacts);
        // System.out.println(artifacts2);
        String seperator = ";";

        List<String> lines = new ArrayList<>();
        lines.add("Dependency;Version;Unused");
        for (Artifact artifact : artifacts) {
            StringBuilder sb = new StringBuilder(artifact.getArtifactId());
            sb.append(seperator).append(artifact.getVersion());
            for (Artifact a : artifacts2) {
                if (a.equals(artifact)) {
                    sb.append(seperator).append("X");
                }
            }
            lines.add(sb.toString());
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("C://Users//Marvin//Documents//Studium//BACHELOR THESIS/" + "/report.csv"));
            for (String string : lines) {
                writer.write(string);
                writer.newLine();
            }
        } catch (IOException e) {

        } finally {
            if (writer != null)
                try {
                    writer.close();
                } catch (IOException e) {

                }
        }
        int vulnerable = 3;
        int notVulnerable = 0;
        logger.info("WE FOUND:\n{} projects with unused dependencies\n{} projects with no unused dependencies", vulnerable, notVulnerable);
        String unUsedString = "";
        for (Artifact a : artifacts2) {
            unUsedString = "\n-" + a.getArtifactId();

            logger.info("For example-java-maven we found the following unused Dependencies:" + unUsedString);
        }
        /**
         File outputDirectory = new File("C:/Users/Marvin/Documents/GitHub/example-java-maven/target");

         System.out.println(project.getModel().getBuild()); // is null
         System.out.println(project.getArtifacts());
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
         } **/
        return new ActualDependencyAnalysisResult(info);

    }

    private int getUnusedDependencyIndex(ArrayList<String> output, String startingPoint) {
        String text = startingPoint;
        int index = 0;
        int counter = 0;
        for (String start : output) {
            if (start.equals(text)) {
                index = counter;
                break;
            }
            counter++;
        }
        return index;
    }

    private Artifact buildArtifactFromString(ArrayList<String> pluginOutput, int unusedDependencyIndex) {
        String line = pluginOutput.get(unusedDependencyIndex);
        String splitValues[] = line.split(":|\\s+");
        String groupId = splitValues[1];
        String artifactId = splitValues[2];
        String type = splitValues[3];
        String version = splitValues[4];
        String scope = splitValues[5];
        Artifact artifact = new DefaultArtifact(groupId, artifactId, version, scope, type, null, new DefaultArtifactHandler());
        return artifact;

    }
}
