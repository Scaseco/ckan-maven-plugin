package org.aksw.maven.plugin.ckan;

import java.io.File;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class TestCkanUploadMojo {
    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    @Ignore
    public void testMojoGoal() throws Exception {
        File file = new File("src/test/resources/coypu-dm");
        MavenProject project = rule.readMavenProject(file);
        Mojo mojo = rule.lookupConfiguredMojo(project, "upload");
        mojo.execute();
    }
}
