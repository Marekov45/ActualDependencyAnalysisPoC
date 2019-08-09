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
import org.apache.maven.plugins.annotations.Parameter;
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

        MavenProject project = new MavenProject(model);
        //  System.out.println(project.getModel().getModules());

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(f);
        //    request.setGoals(Collections.singletonList("clean install"));
        Invoker invoker = new DefaultInvoker();
        // invoker.execute(request);

        request.setGoals(Collections.singletonList("-DexcludeTransitive=true dependency:list"));


        List<Artifact> artifacts = new ArrayList<>();


        ArrayList<String> test = new ArrayList<>();
        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[INFO]    ") && !line.equals("[INFO]    none")) {
                        test.add(line);
                    } else if (line.contains("[ pom ]") || line.contains("[ jar ]")) {
                        if (!project.getModel().getModules().isEmpty()) {
                            line = StringUtils.remove(line, '-');
                            test.add(line);
                        }
                    }


                }
            });
            invoker.execute(request);

        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        System.out.println(test);

//parent poms will be skipped
        if (!project.getModel().getModules().isEmpty()) {
            for (int i = 0; i < test.size(); i++) {
                if (test.get(i).contains("[ jar ]")) {
                    test.subList(0, i).clear();
                    break;
                }
            }
            for (int j = test.size() - 1; j >= 0; j--) {
                if (test.get(j).equals("[INFO] [ jar ]") || test.get(j).equals("[INFO] [ pom ]")) {
                    test.remove(j);
                }
            }
        }
        System.out.println(test);
        for (int i = 0; i < test.size(); i++) {
            artifacts.add(buildArtifactFromString(test, i));
        }
        int count = 0;
        //  for (int i = 0; i < artifacts.size(); i++) {
        //      if(artifacts.get(i).getArtifactId().contains("snappy-java")){
        //          count++;
        //     }
        // }
        System.out.println(artifacts.size());

        //only bytecode analysis, cant detect runtime dependencies, ignore everyathing non compile
        request.setGoals(Collections.singletonList(" -DignoreNonCompile=true dependency:analyze"));
        List<Artifact> artifacts2 = new ArrayList<>();

        ArrayList<String> listOfModules = new ArrayList<>();
        ArrayList<String> test2 = new ArrayList<>();
        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[WARNING] Used undeclared") || line.startsWith("[WARNING] Unused declared")) {
                        test2.add(line);
                        listOfModules.add(line);
                        //spring boot only autoconfigures different things
                        // by adding the corresponding dependency and this configuration is happening outside of the application code
                    } else if (line.startsWith("[WARNING]    ") && !line.contains("org.springframework")) {
                        test2.add(line);
                    } else if (line.startsWith("[ERROR]")) {
                        //  logger.info("The build failed for the project" + repositoryInformation.getName() + ". It will be excluded from the analysis");
                        test2.add(line);
                        return;
                    }
                    //for multi-module projects
                    //  else if (line.startsWith("[INFO]") && line.endsWith("[jar]")) {
                    // logger.info(line);
                    //      listOfModules.add(line);
                    //  }
                    else if (!project.getModel().getModules().isEmpty()) {
                        if (line.contains("[INFO] No dependency problems found") || line.startsWith("[INFO] --- maven-dependency-plugin:") || line.contains("[INFO] Skipping pom project")) {
                            listOfModules.add(line);
                        }
                    }


                }
            });
            invoker.execute(request);

        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }


        System.out.println(listOfModules);

        boolean isMultiModule = false;
        List<Artifact> transformModuletoArtifact = new ArrayList<>();
        if (!project.getModel().getModules().isEmpty()) {
            isMultiModule = true;
            List<String> completeList = new ArrayList<>();
            for (int i = 0; i < listOfModules.size(); i++) {
                if (listOfModules.get(i).contains("@")) {
                    String t = StringUtils.substringBetween(listOfModules.get(i), "@", "---").trim();
                    completeList.add(t);
                } else if (listOfModules.get(i).contains("No dependency problems")) {
                    String u = StringUtils.substringBetween(listOfModules.get(i), "[INFO]", "found");
                    completeList.add(u);
                } else if (listOfModules.get(i).contains("Skipping pom project")) {
                    String u = StringUtils.substringBetween(listOfModules.get(i), "[INFO]", "project");
                    completeList.add(u);
                } else if (listOfModules.get(i).contains("[WARNING]")) {
                    String u = StringUtils.substringBetween(listOfModules.get(i), "[WARNING]", "dependencies");
                    completeList.add(u);
                }


            }

            System.out.println(completeList);

            for (int i = completeList.size() - 1; i >= 0; ) {
                if (completeList.get(i).contains("No dependency problems") || completeList.get(i).contains("Skipping pom")) {
                    completeList.remove(i);
                    completeList.remove(i - 1);
                    i -= 2;
                } else if (completeList.get(i).contains("Unused declared")) {
                    completeList.remove(i);
                    if (completeList.get(i - 1).contains("Used undeclared")) {
                        completeList.remove(i - 1);
                        i -= 2;
                    } else {
                        i -= 1;
                    }
                } else if (completeList.get(i).contains("Used undeclared")) {
                    completeList.remove(i);
                    completeList.remove(i - 1);
                    i -= 2;
                } else {
                    i -= 1;
                }
            }
            System.out.println(completeList);

            for (int i = 0; i < completeList.size(); i++) {
                transformModuletoArtifact.add(i, new DefaultArtifact("org.dummy", completeList.get(i), "", null, "jar", null, new DefaultArtifactHandler()));
            }
        }


        System.out.println(test2);
        System.out.println(transformModuletoArtifact);


        //  String listString = String.join(" ", test2);
        // System.out.println(listString);
        //  if (!test2.contains("[WARNING] Used undeclared dependencies found:")) {
        //      String deps3 = StringUtils.substringBetween(listString, "[WARNING] Unused declared dependencies found: ", "END");
        //  }
        //  else if (){
//
        //  }
        //  else {
        //      String deps[] = StringUtils.substringsBetween(listString, "[WARNING] Unused declared dependencies found: ", "[WARNING] Used undeclared dependencies found:");
        //  }

        //System.out.println(Arrays.asList(deps));
        //   System.out.println(Arrays.asList(deps2));
        //  System.out.println(deps3);
        String mavenText = "[WARNING] Unused declared dependencies found:";
        //   if (!mavenPluginOutput.contains(mavenText)) {
        //       logger.info("This project has no unused declared dependencies.");
        //   return new AnalysisResultWithoutProcessing(repositoryInformation, getUniqueName());
        //   }
        int moduleCounter = 0;

        boolean unusedPattern = false;
        for (int i = getUnusedDependencyIndex(test2, mavenText); i < test2.size(); i++) {
            if (test2.get(i).contains("Unused declared")) {
                unusedPattern = true;
                if (!project.getModel().getModules().isEmpty()) {
                    artifacts2.add(transformModuletoArtifact.get(moduleCounter));
                    moduleCounter++;
                }
            }
            if (test2.get(i).contains("Used undeclared")) {
                unusedPattern = false;

            }
            if (unusedPattern && !test2.get(i).contains("Unused declared")) {
                artifacts2.add(buildArtifactFromString(test2, i));
            }
        }

        //  Set<Artifact> noDuplicateArtifacts = new LinkedHashSet<>();

        //  for (Artifact artifact : artifacts2) {
        //      noDuplicateArtifacts.add(artifact);
        //  }
        //    List<Artifact> transformedList = new ArrayList(noDuplicateArtifacts);
        // duplicates sind weg, jetzt wieder zurück in Arraylist zur Weiterverarbeitung
        System.out.println(artifacts2);

        // System.out.println(noDuplicateArtifacts);
        // System.out.println(artifacts2);
        String seperator = ";";

        List<String> lines = new ArrayList<>();
        lines.add("Dependency;Version;Modul;Unused");
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
            unUsedString += "\n-" + a.getArtifactId();

        }
        logger.info("For example-java-maven we found the following unused Dependencies:" + unUsedString);
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
