package eu.hinsch.cew;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Created by lh on 14/03/15.
 */
public class CheckedExceptionWrapperGeneratorPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getExtensions().create("checkedExceptionWrapperGenerator", CheckedExceptionWrapperGeneratorPluginExtension.class);
        project.getConfigurations().create("checkedExceptionWrapperGenerator").setVisible(true);
        project.getTasks().create("generateCheckedExceptionWrappers", GenerateCheckedExceptionWrappersTask.class);
    }
}