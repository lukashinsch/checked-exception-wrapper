package eu.hinsch.cew

import org.gradle.api.Plugin
import org.gradle.api.Project

class CheckedExceptionWrapperGeneratorPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('checkedExceptionWrapperGenerator', CheckedExceptionWrapperGeneratorPluginExtension)
        project.configurations.create('checkedExceptionWrapperGenerator').setVisible(true)
        project.task('generateCheckedExceptionWrappers', type: GenerateCheckedExceptionWrappersTask)
    }
}
