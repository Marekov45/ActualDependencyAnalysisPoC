package de.hhn.it.wolves.plugins.actualdependencyanalyzer;

import de.hhn.it.wolves.domain.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugins.dependency.analyze.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;


/**
 * Created by Marvin Rekovsky on 15.05.19.
 * <p>
 * This plugin was used as a testing ground for the analysis of declared dependencies that are unused in a java project.
 */
public class ActualDependencyAnalyserPlugin {

    private static final Logger logger = LoggerFactory.getLogger(ActualDependencyAnalyserPlugin.class.getName());

    public static void main(String[] args) throws MavenInvocationException {
        BasicConfigurator.configure();
        ActualDependencyAnalyserPlugin plugin = new ActualDependencyAnalyserPlugin();
        plugin.analyseRepository();
    }


    public ActualDependencyAnalysisResult analyseRepository() {
        Model model = null;
        FileReader reader = null;
        File f = null;

        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        try {
            f = new File("C:/Users/Marvin/IdeaProjects/dubboDemo/pom.xml");
            reader = new FileReader(f);
            model = mavenreader.read(reader);
            model.setPomFile(f);

        } catch (Exception ex) {
            System.out.println(ex);
        }

        MavenProject project = new MavenProject(model);

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(f);
        Invoker invoker = new DefaultInvoker();

        request.setGoals(Collections.singletonList("-DexcludeTransitive=true dependency:list"));

        ArrayList<String> artifactsAsString = new ArrayList<>();

        List<Artifact> artifacts = new ArrayList<>();

        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[INFO]    ") && !line.equals("[INFO]    none")) {
                        artifactsAsString.add(line);
                    } else if (line.contains("[ pom ]") || line.contains("[ jar ]")) {
                        if (!project.getModel().getModules().isEmpty()) {
                            line = StringUtils.remove(line, '-');
                            artifactsAsString.add(line);
                        }
                    }


                }
            });
            invoker.execute(request);

        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        System.out.println(artifactsAsString);

        //parent poms will be skipped
        if (!project.getModel().getModules().isEmpty()) {
            for (int i = 0; i < artifactsAsString.size(); i++) {
                if (artifactsAsString.get(i).contains("[ jar ]")) {
                    artifactsAsString.subList(0, i).clear();
                    break;
                }
            }
            for (int j = artifactsAsString.size() - 1; j >= 0; j--) {
                if (artifactsAsString.get(j).equals("[INFO] [ jar ]") || artifactsAsString.get(j).equals("[INFO] [ pom ]")) {
                    artifactsAsString.remove(j);
                }
            }
        }
        System.out.println(artifactsAsString);
        for (int i = 0; i < artifactsAsString.size(); i++) {
            artifacts.add(buildArtifactFromString(artifactsAsString, i));
        }

        //only bytecode analysis, cant detect runtime dependencies, ignore everything non compile
        request.setGoals(Collections.singletonList(" -DignoreNonCompile=true dependency:analyze"));

        ArrayList<String> listOfModules = new ArrayList<>();

        ArrayList<String> analyzeGoalArtifacts = new ArrayList<>();

        List<Artifact> unusedArtifacts = new ArrayList<>();
        try {
            // invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
            invoker.setOutputHandler(new InvocationOutputHandler() {
                @Override
                public void consumeLine(String line) throws IOException {
                    if (line.startsWith("[WARNING] Used undeclared") || line.startsWith("[WARNING] Unused declared")) {
                        analyzeGoalArtifacts.add(line);
                        listOfModules.add(line);
                        //org.springframework is an example of a dependency that is not always analysed correctly
                    } else if (line.startsWith("[WARNING]    ") && !line.contains("org.springframework")) {
                        analyzeGoalArtifacts.add(line);
                    } else if (line.startsWith("[ERROR]")) {
                        analyzeGoalArtifacts.add(line);
                        return;
                    } else if (!project.getModel().getModules().isEmpty()) {
                        //for module matching purposes
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

        List<Artifact> transformModuletoArtifact = new ArrayList<>();
        if (!project.getModel().getModules().isEmpty()) {
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
                // for the matching process the module has to be transformed into an artifact
                transformModuletoArtifact.add(i, new DefaultArtifact("org.dummy", completeList.get(i), "No version", null, "jar", null, new DefaultArtifactHandler()));
            }
        }


        System.out.println(analyzeGoalArtifacts);
        System.out.println(transformModuletoArtifact);

        String mavenText = "[WARNING] Unused declared dependencies found:";

        int moduleCounter = 0;

        boolean unusedPattern = false;
        for (int i = getUnusedDependencyIndex(analyzeGoalArtifacts, mavenText); i < analyzeGoalArtifacts.size(); i++) {
            if (analyzeGoalArtifacts.get(i).contains("Unused declared")) {
                unusedPattern = true;
                if (!project.getModel().getModules().isEmpty()) {
                    unusedArtifacts.add(transformModuletoArtifact.get(moduleCounter));
                    moduleCounter++;
                }
            }
            if (analyzeGoalArtifacts.get(i).contains("Used undeclared")) {
                unusedPattern = false;

            }
            if (unusedPattern && !analyzeGoalArtifacts.get(i).contains("Unused declared")) {
                unusedArtifacts.add(buildArtifactFromString(analyzeGoalArtifacts, i));
            }
        }

        System.out.println(unusedArtifacts);

        String seperator = ";";
        List<String> lines = new ArrayList<>();
        lines.add("Dependency;Version;Modul;Unused");
        String moduleName = " ";
        List<Artifact> unusedDepsCopy = new ArrayList<>();
        for (int i = 0; i < (unusedArtifacts.size()); i++) {
            unusedDepsCopy.add(unusedArtifacts.get(i));
        }
        //match modules to unused dependencies if the project has multiple modules
        for (Artifact artifact : artifacts) {
            StringBuilder sb = new StringBuilder(artifact.getArtifactId());
            sb.append(seperator).append(artifact.getVersion());
            for (Iterator<Artifact> iterator = unusedDepsCopy.iterator(); iterator.hasNext(); ) {
                Artifact module = iterator.next();
                if (module.getVersion().equals("No version") && !moduleName.equals(module.getArtifactId())) {
                    moduleName = module.getArtifactId();
                } else {
                    if (module.equals(artifact)) {
                        sb.append(seperator).append(moduleName);
                        sb.append(seperator).append("X");
                        iterator.remove();
                        break;
                    }
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
            e.printStackTrace();
        } finally {
            if (writer != null)
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
        //  int vulnerable = 3;
        //  int notVulnerable = 0;
        //  logger.info("WE FOUND:\n{} projects with unused dependencies\n{} projects with no unused dependencies", vulnerable, notVulnerable);
        String unUsedString = "";
        for (Artifact a : unusedArtifacts) {
            unUsedString += "\n-" + a.getArtifactId();

        }
        //dependencies that are present in multiple modules will be counted as one in the global statistics
        logger.info("For this project we found the following unused Dependencies:" + unUsedString);

        return new ActualDependencyAnalysisResult();
    }

    /**
     * Returns the index of the starting point for extracting unused dependencies.
     *
     * @param output        the list that contains the unused dependencies along other possible stuff like missing dependencies.
     *                      <b>Must not be <code>null</code></b>.
     * @param startingPoint the string to be searched for in the list.<b>Must not be <code>null</code> or empty</b>.
     * @return the index of the starting point for extracting unused dependencies.
     */
    private int getUnusedDependencyIndex(ArrayList<String> output, String startingPoint) {
        int index = 0;
        int counter = 0;
        for (String start : output) {
            if (start.equals(startingPoint)) {
                index = counter;
                break;
            }
            counter++;
        }
        return index;
    }

    /**
     * Transforms the dependency of the type String into an Artifact and returns it.
     *
     * @param pluginOutput          the list that contains all dependencies.
     * @param unusedDependencyIndex the current index of the dependency to be transformed.
     * @return the dependency that now has the reference type Artifact.
     */
    private Artifact buildArtifactFromString(ArrayList<String> pluginOutput, int unusedDependencyIndex) {
        String line = pluginOutput.get(unusedDependencyIndex);
        String[] splitValues = line.split(":|\\s+");
        String groupId = splitValues[1];
        String artifactId = splitValues[2];
        String type = splitValues[3];
        String version = splitValues[4];
        String scope = splitValues[5];
        return new DefaultArtifact(groupId, artifactId, version, scope, type, null, new DefaultArtifactHandler());


    }
}
