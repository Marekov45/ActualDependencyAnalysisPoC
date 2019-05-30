package de.hhn.it.wolves.domain;

import java.io.File;

/**
 * Since this is a Proof of Concept, there is only one known repository used
 */
public class RepositoryInfo {

    private String id;
    private String description;
    private File localRepoPath;
    private ProgrammingLanguage language;
    private String repoName;

    public RepositoryInfo() {
        this.id = "000000";
        this.repoName = "example-java-maven";
        this.description = "An example maven project to demonstrate srcclr scans.";
        this.localRepoPath = new File("C:/Users/Marvin/Documents/GitHub/maven-simple");
        this.language = ProgrammingLanguage.JAVA;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public File getLocalRepoPath() {
        return localRepoPath;
    }

    public void setLocalRepoPath(File localRepoPath) {
        this.localRepoPath = localRepoPath;
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public void setLanguage(ProgrammingLanguage language) {
        this.language = language;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
}
